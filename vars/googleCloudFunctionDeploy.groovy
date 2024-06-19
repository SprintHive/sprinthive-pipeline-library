#!/usr/bin/groovy

// This script currently supports deploying Google Cloud Functions with HTTP and Pub/Sub triggers.
// TODO: figure out exact workload identity

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
    def triggerType = config.triggerType
    def topicName = config.topicName
    def schedule = config.schedule
    def timeZone = config.timeZone
    def pubsubTargetData = config.pubsubTargetData

    ciNode {
        try {
            stage("Deploy Cloud Function: ${functionName}") {
                container('gcloud') {
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
                            ${maxInstances ? "--max-instances ${maxInstances}" : ''}
                    """

                    echo "Cloud Function ${functionName} deployed successfully."
                }
            }

            if (triggerType == 'pubsub') {
                stage("Create Pub/Sub Topic and Cloud Scheduler Job") {
                    container('gcloud') {
                        sh """
                            gcloud config set project ${projectId}

                            gcloud pubsub topics create ${topicName}

                            gcloud scheduler jobs create pubsub ${functionName}-scheduler \
                                --schedule '${schedule}' \
                                --topic ${topicName} \
                                --message-body '${pubsubTargetData}' \
                                --time-zone '${timeZone}'
                        """

                        echo "Pub/Sub topic and Cloud Scheduler job created successfully."
                    }
                }
            }

            stage("Verify Cloud Function: ${functionName}") {
                container('gcloud') {
                    sh """
                        gcloud config set project ${projectId}

                        functionStatus=\$(gcloud functions describe ${functionName} --region ${region} --format='value(status)')

                        if [ "\$functionStatus" == "ACTIVE" ]; then
                            echo "Cloud Function ${functionName} is verified and in ACTIVE state."
                        else
                            echo "Cloud Function ${functionName} verification failed. Status: \$functionStatus"
                            exit 1
                        fi
                    """

                    if (triggerType == 'http') {
                        sh """
                            functionUrl=\$(gcloud functions describe ${functionName} --region ${region} --format='value(httpsTrigger.url)')
                            response=\$(curl -s -o /dev/null -w '%{http_code}' \${functionUrl})

                            if [ "\$response" == "200" ]; then
                                echo "HTTP-triggered Cloud Function ${functionName} is responding successfully."
                            else
                                echo "HTTP-triggered Cloud Function ${functionName} verification failed. HTTP response code: \$response"
                                exit 1
                            fi
                        """
                    }
                }
            }
        } catch (err) {
            echo "Error deploying Cloud Function: ${err.getMessage()}"
            currentBuild.result = 'FAILURE'
        }
    }
}