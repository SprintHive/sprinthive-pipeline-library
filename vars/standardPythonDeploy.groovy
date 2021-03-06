#!/usr/bin/groovy

def call(config) {
    def versionTag = ''
    def dockerImage

    dockerNode {
        def scmInfo = checkout scm
        def envInfo = environmentInfo(scmInfo)
        echo "Current branch is: ${envInfo.branch}"
        echo "Deploy namespace is: ${envInfo.deployStage}"

        stage('Build docker image') {
            versionTag = getNewVersion{}
            dockerImage = "${config.dockerTagBase}/${config.componentName}:${versionTag}"
            container('docker') {
                docker.withRegistry(config.registryUrl, config.registryCredentialsId) {
                    sh "docker build -t ${dockerImage} ."
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

        if (env.POST_BUILD_TRIGGER_JOB != null) {
            stage("Trigger ${env.POST_BUILD_TRIGGER_JOB}") {
                build job: env.POST_BUILD_TRIGGER_JOB, parameters: [string(name: 'IMAGE_TAG', value: versionTag), string(name: 'imageTag', value: versionTag), string(name: 'CHANGE_LOG', value: changeLog())], wait: false
            }
        }
    }
}
