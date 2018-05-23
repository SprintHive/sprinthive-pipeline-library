#!/usr/bin/groovy

def call(config) {
    def versionTag = ''
    def dockerImage
    def namespace = ''
    def deployStage = ''

    dockerNode {
        stage('Publish docker image') {
            def scmInfo = checkout scm

            if (scmInfo == null || scmInfo.GIT_BRANCH == null) {
                currentBuild.result = 'ABORTED'
                error('Git branch is null..')
            }

            def branch = scmInfo.GIT_BRANCH.substring(scmInfo.GIT_BRANCH.lastIndexOf('/')+1)
            echo "Current branch is: ${branch}"
            if (branch.equals("dev")) {
                namespace = "dev"
                deployStage = 'Development'
            } else if (branch.equals('master')) {
                namespace = 'pre-prod'
                deployStage = 'Pre-Production'
            } else {
                namespace = branch
                deployStage = "Test Stack"
            }
            echo "Deploy namespace set to ${namespace}"

            versionTag = getNewVersion{}
            dockerImage = "${config.dockerTagBase}/${config.componentName}:${versionTag}"

            container('docker') {
                docker.withRegistry(config.registryUrl, config.registryCredentialsId) {
                    sh "docker build -t ${dockerImage} ."
                    docker.image(dockerImage).push()
                    docker.image(dockerImage).push('latest')
                }
            }
        }

        stage("Rollout to ${deployStage}") {
            helmDeploy([
                releaseName:  config.releaseName,
                namespace:  namespace,
                chartName:  config.componentName,
                imageTag:  versionTag
            ])
        }
    }
}
