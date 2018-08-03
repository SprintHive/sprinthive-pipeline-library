#!/usr/bin/groovy

def call(config) {
    def versionTag = ''

    kanikoNode {
        def scmInfo = checkout scm
        def envInfo = environmentInfo(scmInfo)
        echo "Current branch is: ${envInfo.branch}"
        echo "Deploy namespace is: ${envInfo.deployStage}"
        if (envInfo.multivariateTest != null) {
            echo "Multivariate test: ${envInfo.multivariateTest}"
        }

        stage('Kaniko build and publish') {
            versionTag = getNewVersion{}
            dockerTags = [versionTag, envInfo.branch]

            kanikoBuildPublish([
                registryCredentialsId: config.registryCredentialsId,
                registryUrl: config.registryUrl,
                registryBasePath: config.registryBasePath,
                imageName: config.componentName,
                tags: dockerTags
            ])
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
}
