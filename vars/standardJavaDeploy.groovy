#!/usr/bin/groovy

def call(config) {
    def versionTag = ''
    def dockerImage
    def targetNamespace

    javaNode {
        def scmInfo = checkout scm
        def envInfo = environmentInfo(scmInfo)
        targetNamespace = envInfo.deployStage
        echo "Current branch is: ${envInfo.branch}"
        echo "Deploy namespace is: ${envInfo.deployStage}"
        if (envInfo.multivariateTest != null) {
            echo "Multivariate test: ${envInfo.multivariateTest}"
        }

        stage('Compile source') {
            versionTag = getNewVersion{}
            dockerImage = "${config.dockerTagBase}/${config.componentName}:${versionTag}"

            container(name: config.buildContainerOverride != null ? config.buildContainerOverride : 'gradle') {
                def buildCommand = config.buildCommandOverride != null ? config.buildCommandOverride : "gradle bootJar"
                sh """
                    export ENV_STAGE=${envInfo.deployStage}
                    export ENV_BRANCH=${envInfo.branch}
                    ${buildCommand}
                """
            }
        }

        stage('Publish docker image') {
            container('docker') {
                docker.withRegistry(config.registryUrl, config.registryCredentialsId) {
                    sh "docker build -t ${dockerImage} ."
                    docker.image(dockerImage).push()
                    docker.image(dockerImage).push('latest')
                }
            }
        }

        stage("Rollout to ${envInfo.deployStage.capitalize()}") {
            helmDeploy([
                releaseName:  config.releaseName,
                namespace:  envInfo.deployStage,
                multivariateTest: envInfo.multivariateTest,
                chartName:  config.chartNameOverride != null ? config.chartNameOverride : config.componentName,
                imageTag:  versionTag,
                overrides: config.chartOverrides,
                chartRepoOverride: config.chartRepoOverride
            ])
        }
    }

    if (config.runIntegrationTests != null && config.runIntegrationTests) {
        stage("Integration test") {
            def label = "java-test-${UUID.randomUUID().toString()}"
            podTemplate(label: label, containers: [
                containerTemplate(name: 'gradle', image: 'bitstack701/base-gradle:v3.0.2', ttyEnabled: true, command: 'cat'),
              ], namespace: targetNamespace, envVars: [
                envVar(key: 'JENKINS_URL', value: 'http://cicd-jenkins.infra:8080/'),
                envVar(key: 'JENKINS_TUNNEL', value: 'cicd-jenkins-agent.infra:50000')
              ]) {
                node(label) {
                    checkout scm
                    container('gradle') {
                        sh 'gradle -i integrationTest'
                    }
                }
              }
        }
    }

    if (env.POST_BUILD_TRIGGER_JOB != null) {
        stage("Trigger ${env.POST_BUILD_TRIGGER_JOB}") {
            build job: env.POST_BUILD_TRIGGER_JOB, parameters: [string(name: 'IMAGE_TAG', value: versionTag)], wait: false
        }
    }
}
