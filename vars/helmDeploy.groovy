#!/usr/bin/groovy

def call(String releaseName, String namespace, String versionTag) {
    container('helm') {
        sh "helm upgrade ${releaseName} --namespace ${namespace} -i --reset-values --recreate-pods ./config/chart --values ./config/chart/values.yaml --set image.tag=${versionTag}"
    }
}
