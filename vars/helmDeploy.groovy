#!/usr/bin/groovy

def call(config) {
    def overrides = ""
    for (String override : config.overrides) {
        overrides += " --set " + override;
    }

    if (config.multivariateTest) {
        overrides += " --set multivariateTest=" + config.multivariateTest
    }

    def chartRepo = config.chartRepoOverride != null ? config.chartRepoOverride : "https://sprinthive-service-${config.namespace}-charts.storage.googleapis.com"
    def releaseName = config.releaseName
    if (config.multivariateTest != null) {
        releaseName += "-" + config.multivariateTest
    }

    container('helm') {
        sh "helm init --client-only"
        sh "helm repo add service-charts $chartRepo"
        sh "helm --tiller-namespace ${config.namespace} upgrade ${releaseName} --namespace ${config.namespace} -i --reset-values --wait service-charts/${config.chartName} --set ${config.chartName}.image.tag=${config.imageTag} ${overrides}"
    }
}
