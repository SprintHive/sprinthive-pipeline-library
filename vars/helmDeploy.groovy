#!/usr/bin/groovy

def call(config) {
    def overrides = ""
    for (String override : config.overrides) {
        overrides += " --set " + override
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
            branches: [[name: "main"]],
            userRemoteConfigs: [[credentialsId: 'bitbucket', url: helmfileRepo]]
    ])

    container('helm') {
        File pipelineValuesFile = File.createTempFile("pipeline-values",".tmp")
        sh script:"touch ${pipelineValuesFile.absolutePath}"
        if (config.imageTag) {
            sh script:"echo 'global:\n  image:\n    tag: \"${config.imageTag}\"\n' > ${pipelineValuesFile.absolutePath}"
        }
        def statusCode = sh script:"helmfile -f ${chartEnv}/helmfile.yaml --selector name=${releaseName}" +
                " --namespace " +
                "${config.namespace} sync --wait --values ${pipelineValuesFile.absolutePath} ${overrides} --set " +
                "timeout=6" ,
                returnStatus:true

        if (statusCode != 0) {
            sh "helm rollback ${releaseName} -n ${config.namespace} ```helm history ${releaseName} -n ${config.namespace} | grep DEPLOYED | awk '{print \$1}' | tail -n 1```"
            error "The deployment failed and was rolled back"
        }
    }
}
