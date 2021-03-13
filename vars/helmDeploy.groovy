#!/usr/bin/groovy

def call(config) {
    def overrides = ""
    def chartVersion = ""
    for (String override : config.overrides) {
        overrides += " --set " + override
    }
    if (config.imageTag) {
        overrides += " --set global.image.tag=${config.imageTag}"
    }

    if (config.chartVersion) {
        chartVersion = " --version ${config.chartVersion}"
    }

    if (config.multivariateTest) {
        overrides += " --set ${config.chartName}.multivariateTest=" + config.multivariateTest
    }

    def chartEnv = env.CHART_ENVIRONMENT
    if (!chartEnv) {
        chartEnv = config.namespace
    }

    def releaseName = config.releaseName
    def chartRepo = config.chartRepoOverride != null ? config.chartRepoOverride : "https://sprinthive-service-${chartEnv}-charts.storage.googleapis.com"
    checkout([
            $class: 'GitSCM',
            branches: [[name: "env-values"]],
            extensions: [[$class: 'GitLFSPull']],
            userRemoteConfigs: [[credentialsId: 'bitbucket', url: "https://bitbucket.org/sprinthive/service-charts.git"]]
    ])

    def envValueOverrides = "service-charts/environments/${config.namespace}/${releaseName}.yaml"
    if (fileExists(envValueOverrides)) {
        chartRepo = "https://sprinthive-service-base-charts.storage.googleapis.com"
        overrides += " -f $envValueOverrides"
    }

    if (config.multivariateTest != null) {
        releaseName += "-" + config.multivariateTest
    }

    container('helm') {
        sh "helm init --client-only"
        sh "helm repo add service-charts $chartRepo"
        def statusCode = sh script:"helm --tiller-namespace ${config.namespace} upgrade ${releaseName} --namespace ${config.namespace} -i --reset-values --wait service-charts/${config.chartName}${chartVersion} ${overrides}", returnStatus:true

        if (statusCode != 0) {
            sh "helm --tiller-namespace ${config.namespace} rollback ${releaseName} ```helm --tiller-namespace ${config.namespace} history ${releaseName} | grep DEPLOYED | awk '{print \$1}' | tail -n 1```"
            error "The deployment failed and was rolled back"
        }
    }
}
