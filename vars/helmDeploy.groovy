#!/usr/bin/groovy

def call(config) {
    def overrides = ""
    for (String override : config.overrides) {
        overrides += " --set " + override;
    }

    def chartRepo = config.chartRepoOverride != null ? config.chartRepoOverride : "https://sprinthive-service-${config.namespace}-charts.storage.googleapis.com"

    container('helm') {
        sh "helm init --client-only"
        sh "helm repo add service-charts $chartRepo"
        sh "helm --tiller-namespace ${config.namespace} upgrade ${config.releaseName} --namespace ${config.namespace} -i --reset-values --wait service-charts/${config.chartName} --set ${config.chartName}.image.tag=${config.imageTag} ${overrides}"
    }
}
