#!/usr/bin/groovy

def call(config) {
    def versionTag = ''
    def dockerImage

    dockerNode {
        def scmInfo = checkout scm
        def envInfo = environmentInfo(scmInfo)
        echo "Current branch is: ${envInfo.branch}"
        echo "Deploy namespace is: ${envInfo.deployStage}"

        stage('Publish docker image') {
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

        if ("${env.POST_BUILD_TRIGGER_JOB}") {
            stage("Trigger ${env.POST_BUILD_TRIGGER_JOB}") {
                build job: env.POST_BUILD_TRIGGER_JOB, parameters: [string(name: 'POST_BUILD_TRIGGER_JOB', value: versionTag)], wait: false
            }
        }
    }
}
