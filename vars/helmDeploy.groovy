#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def overrides = ""
    for (String override : config.overrides) {
        overrides += " --set " + override;
    }

    container('helm') {
        sh "helm init --client-only"
        sh "helm repo add sprinthive-service-dev-charts https://sprinthive-service-dev-charts.storage.googleapis.com/"
        sh "helm upgrade ${config.releaseName} --namespace ${config.namespace} -i --reset-values --recreate-pods --wait sprinthive-service-dev-charts/${config.chartName} --set image.tag=${config.imageTag} ${overrides}"
    }
}
