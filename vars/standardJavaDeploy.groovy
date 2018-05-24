#!/usr/bin/groovy

def call(config) {
    def versionTag = ''
    def dockerImage

    javaNode {
        def scmInfo = checkout scm
        def envInfo = environmentInfo(scmInfo)
        echo "Current branch is: ${envInfo.branch}"
        echo "Deploy namespace set to ${envInfo.namespace}"

        stage('Compile source') {
            versionTag = getNewVersion{}
            dockerImage = "${config.dockerTagBase}/${config.componentName}:${versionTag}"

            container(name: config.buildContainerOverride != null ? config.buildContainerOverride : 'gradle') {
                sh config.buildCommandOverride != null ? config.buildCommandOverride : "gradle bootJar"
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

        stage("Rollout to ${envInfo.deployStage}") {
            helmDeploy([
                releaseName:  config.releaseName,
                namespace:  envInfo.namespace,
                chartName:  config.chartNameOverride != null ? config.chartNameOverride : config.componentName,
                imageTag:  versionTag,
                overrides: config.chartOverrides,
                chartRepoOverride: config.chartRepoOverride
            ])
        }
    }
}
