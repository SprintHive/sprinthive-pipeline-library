#!/usr/bin/groovy

def call(config) {
    def versionTag = ''
    def dockerImage
    def targetNamespace

    nodejsNode {
        def scmInfo = checkout scm
        def envInfo = environmentInfo(scmInfo)
        targetNamespace = envInfo.deployStage
        echo "Current branch is: ${envInfo.branch}"
        echo "Deploy namespace is: ${envInfo.deployStage}"

        stage('Build distribution') {
            versionTag = getNewVersion{}
            dockerImage = "${config.dockerTagBase}/${config.componentName}:${versionTag}"

            container(name: "nodejs") {
                def buildCommand = config.buildCommandOverride != null ? config.buildCommandOverride : "yarn && yarn install --production"
                sh """
                    export ENV_STAGE=${envInfo.deployStage}
                    export ENV_BRANCH=${envInfo.branch}
                    ${buildCommand}
                """
            }
        }

        stage('Build docker image') {
            container('docker') {
                docker.withRegistry(config.registryUrl, config.registryCredentialsId) {
                    sh "docker build -t ${dockerImage} --build-arg SOURCE_VERSION=${scmInfo.GIT_COMMIT} ."
                }
            }
        }

        if (config.containerScanEnabled != false) {
            stage('Container scan') {
                container('clairscanner') {
                    sh '/usr/local/bin/clair -w /config/whitelist.yaml -c http://clair.infra:6060 --ip $POD_IP ' + dockerImage
                }
            }
        }

        stage('Push docker image') {
            container('docker') {
                docker.withRegistry(config.registryUrl, config.registryCredentialsId) {
                    docker.image(dockerImage).push()
                    docker.image(dockerImage).push(envInfo.branch)
                }
            }
        }

        if (env.DEPLOY_AFTER_BUILD != "false") {
            stage("Rollout to ${envInfo.deployStage.capitalize()}") {
                helmDeploy([
                    releaseName:  config.releaseName,
                    namespace:  envInfo.deployStage,
                    chartName:  config.chartNameOverride != null ? config.chartNameOverride : config.componentName,
                    imageTag:  versionTag,
                    overrides: config.chartOverrides,
                    chartRepoOverride: config.chartRepoOverride
                ])
            }
        }
    }

    if (config.runIntegrationTests != null && config.runIntegrationTests) {
        stage("Integration test") {
            def label = "node-test-${UUID.randomUUID().toString()}"
            podTemplate(label: label, containers: [
                containerTemplate(name: 'node', image: 'mhart/alpine-node:8', ttyEnabled: true, command: 'cat'),
              ], namespace: targetNamespace, envVars: [
                envVar(key: 'JENKINS_URL', value: 'http://cicd-jenkins.infra:8080/'),
                envVar(key: 'JENKINS_TUNNEL', value: 'cicd-jenkins-agent.infra:50000')
              ]) {
                node(label) {
                    checkout scm
                    container('node') {
                        sh 'yarn'
                        sh 'yarn integration-test'
                    }
                }
              }
        }
    }

    if (env.POST_BUILD_TRIGGER_JOB != null) {
        stage("Trigger ${env.POST_BUILD_TRIGGER_JOB}") {
            build job: env.POST_BUILD_TRIGGER_JOB, parameters: [string(name: 'IMAGE_TAG', value: versionTag), string(name: 'imageTag', value: versionTag), string(name: 'CHANGE_LOG', value: changeLog())], wait: false
        }
    }
}
