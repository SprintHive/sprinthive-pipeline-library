def call(config) {
    def functionName = config.functionName
    def projectId = config.projectId
    def serviceAccountEmail = config.serviceAccountEmail
    def sourceCodeBucket = config.sourceCodeBucket
    def sourceCodeObject = config.sourceCodeObject
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
                            --source gs://${sourceCodeBucket}/${sourceCodeObject} \
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