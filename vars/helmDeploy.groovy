#!/usr/bin/groovy

def call(config) {
    def overrides = ""
    for (String override : config.overrides) {
        overrides += " --set " + override
    }

    if (config.multivariateTest) {
        overrides += " --set ${config.chartName}.multivariateTest=" + config.multivariateTest
    }

    def chartEnv = env.CHART_ENVIRONMENT
    if (!chartEnv) {
        chartEnv = config.namespace
    }

    def chartRepo = config.chartRepoOverride != null ? config.chartRepoOverride : "https://sprinthive-service-${chartEnv}-charts.storage.googleapis.com"
    def releaseName = config.releaseName
    if (config.multivariateTest != null) {
        releaseName += "-" + config.multivariateTest
    }

    container('helm') {
        sh "helm init --client-only --home /tmp"
        sh "helm repo add service-charts $chartRepo"
        def statusCode = sh script:"helm --tiller-namespace ${config.namespace} upgrade ${releaseName} --namespace ${config.namespace} -i --reset-values --wait service-charts/${config.chartName} --set ${config.chartName}.image.tag=${config.imageTag} ${overrides}", returnStatus:true

        if (statusCode != 0) {
            sh "helm --tiller-namespace ${config.namespace} rollback ${releaseName} ```helm --tiller-namespace ${config.namespace} history ${releaseName} | grep DEPLOYED | awk '{print \$1}' | tail -n 1```"
            error "The deployment failed and was rolled back"
        }
    }
}
