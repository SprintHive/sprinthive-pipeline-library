def call(config) {
    def functionName = config.functionName
    def projectId = config.projectId
    def serviceAccountEmail = config.serviceAccountEmail
    def zipFilePath = config.zipFilePath
    def region = config.region
    def environmentVariables = config.environmentVariables
    def credentialsId = config.credentialsId
    def runtime = config.runtime ?: 'python312'
    def entryPoint = config.entryPoint
    def timeout = config.timeout
    def maxInstances = config.maxInstances
    def allowUnauthenticated = config.allowUnauthenticated ?: false

    // Validation of required parameters
    if (!functionName || !projectId || !serviceAccountEmail || !zipFilePath || !region) {
        error "Missing required parameters for deploying Cloud Function."
    }

    cdNode {
        stage("Deploy Cloud Function: ${functionName}") {
            container('gcloud') {
                // TODO: tried adding ADC functionality but can't find docs to specify if this is possible
                withCredentials([file(credentialsId: credentialsId, variable: 'GCLOUD_KEY')]) {
                    try {
                        echo "Starting deployment of Cloud Function ${functionName}..."

                        sh """
                            gcloud auth activate-service-account --key-file ${GCLOUD_KEY}
                            gcloud config set project ${projectId}

                            gcloud functions deploy ${functionName} \
                                --runtime ${runtime} \
                                --region ${region} \
                                --source ${zipFilePath} \
                                --trigger-http \
                                --service-account ${serviceAccountEmail} \
                                --set-env-vars ${environmentVariables.collect { "$it.key=$it.value" }.join(',')} \
                                ${entryPoint ? "--entry-point ${entryPoint}" : ''} \
                                ${timeout ? "--timeout ${timeout}" : ''} \
                                ${maxInstances ? "--max-instances ${maxInstances}" : ''} \
                                ${allowUnauthenticated ? '--allow-unauthenticated' : ''} \
                        """

                        echo "Cloud Function ${functionName} deployed successfully."
                    } catch (err) {
                        error "Deployment of Cloud Function ${functionName} failed: ${err}"
                    }
                }
            }
        }

        stage("Verify Cloud Function: ${functionName}") {
            container('gcloud') {
                withCredentials([file(credentialsId: credentialsId, variable: 'GCLOUD_KEY')]) {
                    try {
                        echo "Verifying Cloud Function ${functionName}..."

                        def functionUrl = sh(
                            script: "gcloud functions describe ${functionName} --region ${region} --format='value(httpsTrigger.url)'",
                            returnStdout: true
                        ).trim()

                        def response = sh(
                            script: "curl -s -o /dev/null -w '%{http_code}' ${functionUrl}",
                            returnStdout: true
                        ).trim()

                        if (response == '200') {
                            echo "Cloud Function ${functionName} is verified and responding successfully."
                        } else {
                            error "Cloud Function ${functionName} verification failed. HTTP response code: ${response}"
                        }
                    } catch (err) {
                        error "Verification of Cloud Function ${functionName} failed: ${err}"
                    }
                }
            }
        }
    }
}