#!/usr/bin/groovy

def call(config) {
    def overrides = ""
    for (String override : config.overrides) {
        overrides += " --set " + override
    }
    if (config.imageTag) {
        overrides += " --set global.image.tag=${config.imageTag}"
    }

    def helmfileRepo
    if (config.helmfileRepoOverride != null) {
      helmfileRepo = config.helmfileRepoOverride
    } else {
        helmfileRepo = "https://bitbucket.org/sprinthive/service-charts.git"
    }

    def chartEnv = env.CHART_ENVIRONMENT
    if (!chartEnv) {
        chartEnv = config.namespace
    }
    def releaseName = config.releaseName

    checkout([
            $class: 'GitSCM',
            branches: [[name: "env-values"]],
            userRemoteConfigs: [[credentialsId: 'bitbucket', url: helmfileRepo]]
    ])

    container('helm') {
        def statusCode = sh script:"helmfile -f ${chartEnv}/helmfile.yaml --selector name=${releaseName} --namespace ${config.namespace} sync --wait ${overrides} --args \"--tiller-namespace ${config.namespace}\"", returnStatus:true

        if (statusCode != 0) {
            sh "helm --tiller-namespace ${config.namespace} rollback ${releaseName} ```helm --tiller-namespace ${config.namespace} history ${releaseName} | grep DEPLOYED | awk '{print \$1}' | tail -n 1```"
            error "The deployment failed and was rolled back"
        }
    }
}
