#!/usr/bin/groovy

def call(config) {
    def versionTag = ''
    def dockerImage

    nodejsNode {
        def scmInfo = checkout scm
        def envInfo = environmentInfo(scmInfo)
        echo "Current branch is: ${envInfo.branch}"
        echo "Deploy namespace is: ${envInfo.deployStage}"

        stage('Build distribution') {
            versionTag = getNewVersion{}
            dockerImage = "${config.dockerTagBase}/${config.componentName}:${versionTag}"

            container(name: config.buildContainerOverride != null ? config.buildContainerOverride : 'nodejs') {
                def buildCommand = config.buildCommandOverride != null ? config.buildCommandOverride : "yarn && yarn install --production"
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
                chartName:  config.chartNameOverride != null ? config.chartNameOverride : config.componentName,
                imageTag:  versionTag,
                overrides: config.chartOverrides,
                chartRepoOverride: config.chartRepoOverride
            ])
        }

        if (env.POST_BUILD_TRIGGER_JOB != null) {
            stage("Trigger ${env.POST_BUILD_TRIGGER_JOB}") {
                build job: env.POST_BUILD_TRIGGER_JOB, parameters: [string(name: 'IMAGE_TAG', value: versionTag)], wait: false
            }
        }
    }
}
