#!/usr/bin/groovy

// This script currently supports deploying Google Cloud Functions with HTTP and Pub/Sub triggers.

/**
 * Deploys a Google Cloud Function.
 *
 * @param config The configuration for deploying the Cloud Function.
 * @param config.functionName The name of the Cloud Function.
 * @param config.projectId The ID of the Google Cloud project where the function will be deployed.
 * @param config.serviceAccountEmail The email of the service account that will be used to run the function.
 * @param config.zipFilePath The path to the ZIP file containing the function's source code and dependencies.
 * @param config.region The region where the function will be deployed.
 * @param config.environmentVariables (Optional) A map of environment variables to be set for the function.
 * @param config.runtime The runtime environment for the function (e.g., 'python37', 'nodejs10').
 * @param config.entryPoint (Optional) The name of the function to be executed within the source code.
 * @param config.timeout (Optional) The timeout duration for the function execution.
 * @param config.maxInstances (Optional) The maximum number of instances for the function.
 * @param config.allowUnauthenticated (Optional) Whether to allow unauthenticated access to the function. Default is false.
 * @param config.triggerType The type of trigger for the function ('http' or 'pubsub').
 * @param config.topicName (Optional if not Pub/Sub triggered) The name of the Pub/Sub topic for Pub/Sub triggered functions.
 */
def call(config) {
    def functionName = config.functionName
    def projectId = config.projectId
    def serviceAccountEmail = config.serviceAccountEmail
    def zipFilePath = config.zipFilePath
    def region = config.region
    def environmentVariables = config.environmentVariables
    def runtime = config.runtime
    def entryPoint = config.entryPoint
    def timeout = config.timeout
    def maxInstances = config.maxInstances
    def allowUnauthenticated = config.allowUnauthenticated ?: false
    def triggerType = config.triggerType
    def topicName = config.topicName

    // Validation of required parameters
    if (!functionName || !projectId || !serviceAccountEmail || !zipFilePath || !region || !triggerType || !runtime) {
        error "Missing required parameters for deploying Cloud Function."
    }

    if (triggerType == 'pubsub' && !topicName) {
        error "Missing required parameter 'topicName' for Pub/Sub triggered function."
    }

    cdNode {
        stage("Deploy Cloud Function: ${functionName}") {
            // We use ADC by default to run gcloud
            container('gcloud') {
                try {
                    echo "Starting deployment of Cloud Function ${functionName}..."

                    sh """
                        gcloud config set project ${projectId}

                        gcloud functions deploy ${functionName} \
                            --runtime ${runtime} \
                            --region ${region} \
                            --source ${zipFilePath} \
                            ${triggerType == 'http' ? '--trigger-http' : "--trigger-topic ${topicName}"} \
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

        if (triggerType == 'http') {
            stage("Verify Cloud Function: ${functionName}") {
                container('gcloud') {
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