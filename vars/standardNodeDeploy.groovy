#!/usr/bin/groovy

def call(config) {
    def versionTag = ''
    def containerImageTagless = "${config.dockerTagBase}/${config.componentName}".toString()
    def targetNamespace

    ciNode {
        def scmInfo = checkout scm
        def envInfo = environmentInfo(scmInfo)
        targetNamespace = envInfo.deployStage
        echo "Current branch is: ${envInfo.branch}"
        echo "Deploy namespace is: ${envInfo.deployStage}"
        echo "Context directory is: ${env.WORKSPACE}"
        stage('Build distribution') {
            versionTag = getNewVersion{}
            container(name: "nodejs") {
                versionTag = "${nodeAppVersion()}-${versionTag}"
                def buildCommand = config.buildCommandOverride != null ? config.buildCommandOverride : "yarn && yarn install --production"
                sh """
                    export ENV_STAGE=${envInfo.deployStage}
                    export ENV_BRANCH=${envInfo.branch}
                    ${buildCommand}
                """
            }
        }

        stage('Build container image') {
            kanikoBuild(env.WORKSPACE, "container.tar", "${containerImageTagless}:${versionTag}", scmInfo.GIT_COMMIT)
        }

        if (config.containerScanEnabled != false) {
            grypeScan("container.tar", env.WORKSPACE)
        }

        stage('Push container image') {
            cranePush("${containerImageTagless}:${versionTag}", "container.tar")
            cranePush("${containerImageTagless}:${envInfo.branch}", "container.tar")
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
