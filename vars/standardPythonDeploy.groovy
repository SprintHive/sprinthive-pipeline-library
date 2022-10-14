#!/usr/bin/groovy

def call(config) {
    def versionTag = ''
    def containerImageTagless = "${config.dockerTagBase}/${config.componentName}".toString()

    ciNode {
        def scmInfo = checkout scm
        def envInfo = environmentInfo(scmInfo)
        echo "Current branch is: ${envInfo.branch}"
        echo "Deploy namespace is: ${envInfo.deployStage}"

        stage('Build container image') {
            versionTag = getNewVersion{}
            kanikoBuild(env.WORKSPACE, "container.tar", "${containerImageTagless}:${versionTag}", scmInfo.GIT_COMMIT)
        }

        if (config.containerScanEnabled != false) {
            stage('Container scan') {
                grypeScan("container.tar", env.WORKSPACE)
            }
        }

        stage('Push container image') {
            cranePush("${containerImageTagless}:${versionTag}", "container.tar")
            cranePush("${containerImageTagless}:${envInfo.branch}", "container.tar")
        }

        if (env.POST_BUILD_TRIGGER_JOB != null) {
            stage("Trigger ${env.POST_BUILD_TRIGGER_JOB}") {
                build job: env.POST_BUILD_TRIGGER_JOB, parameters: [string(name: 'IMAGE_TAG', value: versionTag), string(name: 'imageTag', value: versionTag), string(name: 'CHANGE_LOG', value: changeLog())], wait: false
            }
        }
    }
}
