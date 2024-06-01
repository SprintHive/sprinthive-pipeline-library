def call(config) {
    def functionName = config.functionName
    def projectId = config.projectId
    def serviceAccountEmail = config.serviceAccountEmail
    def zipFilePath = config.zipFilePath
    def region = config.region
    def environmentVariables = config.environmentVariables
    def credentialsId = config.credentialsId
    def runtime = config.runtime ?: 'python312'
    def triggerType = config.triggerType ?: 'http'
    def entryPoint = config.entryPoint
    def timeout = config.timeout
    def maxInstances = config.maxInstances
    def allowUnauthenticated = config.allowUnauthenticated ?: false

    cdNode {
        stage("Deploy Cloud Function: ${functionName}") {
            container('gcloud') {
                // TODO: tried adding ADC functionality but can't find docs to specify if this is possible
                withCredentials([file(credentialsId: credentialsId, variable: 'GCLOUD_KEY')]) {
                    try {
                        sh """
                            gcloud auth activate-service-account --key-file ${GCLOUD_KEY}
                            gcloud config set project ${projectId}

                            gcloud functions deploy ${functionName} \
                                --runtime ${runtime} \
                                --region ${region} \
                                --source ${zipFilePath} \
                                --trigger-${triggerType} \
                                --service-account ${serviceAccountEmail} \
                                --set-env-vars ${environmentVariables.collect { "$it.key=$it.value" }.join(',')} \
                                ${entryPoint ? "--entry-point ${entryPoint}" : ''} \
                                ${timeout ? "--timeout ${timeout}" : ''} \
                                ${maxInstances ? "--max-instances ${maxInstances}" : ''} \
                                ${allowUnauthenticated ? '--allow-unauthenticated' : ''} \
                        """
                    } catch (err) {
                        error "Deployment of Cloud Function ${functionName} failed: ${err}"
                    }
                }
            }
        }
    }
}