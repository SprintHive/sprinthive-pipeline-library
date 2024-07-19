#!/usr/bin/groovy

def call(Map config) {
    def podLabel = "gcloud-${UUID.randomUUID().toString()}"
    
    podTemplate(label: podLabel, yaml: '''
        apiVersion: v1
        kind: Pod
        spec:
          serviceAccountName: jenkins-worker
          containers:
          - name: gcloud
            image: google/cloud-sdk:latest
            command:
            - cat
            tty: true
          volumes:
          - name: workspace-volume
            emptyDir: {}
    ''') {
        node(podLabel) {
            stage('Prepare Function Archive') {
                // Copy workspace contents to pod
                sh "cp -R ${env.WORKSPACE}/* ."
                
                sh """
                    echo "Current working directory:"
                    pwd
                    echo "\nDirectory contents:"
                    ls -la
                    
                    cd ${config.sourceFolderPath}
                    echo "\nFunction directory contents:"
                    ls -la
                    
                    tar -cvzf ../${config.functionName}.tar.gz .
                    cd ..
                    
                    if [ ! -s ${config.functionName}.tar.gz ]; then
                        echo "Error: Created archive is empty"
                        exit 1
                    fi
                    
                    echo "\nContents of the archive:"
                    tar -tvf ${config.functionName}.tar.gz
                    
                    echo "\nArchive file details:"
                    ls -l ${config.functionName}.tar.gz
                """
            }

            container('gcloud') {
                stage("Verify Environment") {
                    sh """
                        echo "Current working directory in gcloud container:"
                        pwd
                        echo "\nDirectory contents in gcloud container:"
                        ls -la
                        echo "\nArchive file details:"
                        ls -l ${config.functionName}.tar.gz || echo "Archive not found"
                        echo "\nGcloud version:"
                        gcloud version
                    """
                }

                stage("Upload Function Archive") {
                    sh """
                        gcloud storage cp ${config.functionName}.tar.gz ${config.gcsPath}
                        echo "Function archive uploaded to ${config.gcsPath}"
                        gcloud storage ls -l ${config.gcsPath}
                    """
                }

                stage("Deploy Cloud Function: ${config.functionName}") {
                    def deployCommand = """
                        gcloud functions deploy ${config.functionName} \
                            --runtime ${config.runtime} \
                            --region ${config.region} \
                            --source ${config.gcsPath} \
                            --service-account ${config.serviceAccountEmail} \
                            --set-env-vars ${config.environmentVariables.collect { "$it.key=$it.value" }.join(',')} \
                            ${config.entryPoint ? "--entry-point ${config.entryPoint}" : ''} \
                            ${config.timeout ? "--timeout ${config.timeout}" : ''} \
                            ${config.maxInstances ? "--max-instances ${config.maxInstances}" : ''} \
                            --verbosity debug
                    """

                    if (config.triggerType == 'http') {
                        deployCommand += " --trigger-http"
                    } else if (config.triggerType == 'pubsub') {
                        deployCommand += " --trigger-topic ${config.topicName}"
                    }

                    sh deployCommand
                }

                if (config.triggerType == 'pubsub') {
                    stage("Create Pub/Sub Topic and Cloud Scheduler Job") {
                        sh """
                            gcloud scheduler jobs create pubsub ${config.functionName}-scheduler \
                                --schedule '${config.schedule}' \
                                --topic ${config.topicName} \
                                --message-body '${config.pubsubTargetData}' \
                                --time-zone '${config.timeZone}' || true
                        """
                    }
                }

                stage("Verify Cloud Function: ${config.functionName}") {
                    sh """
                        functionStatus=\$(gcloud functions describe ${config.functionName} --region ${config.region} --format='value(status)')
                        if [ "\$functionStatus" != "ACTIVE" ]; then
                            echo "Cloud Function ${config.functionName} verification failed. Status: \$functionStatus"
                            exit 1
                        fi
                    """

                    if (config.triggerType == 'http') {
                        sh """
                            functionUrl=\$(gcloud functions describe ${config.functionName} --region ${config.region} --format='value(httpsTrigger.url)')
                            response=\$(curl -s -o /dev/null -w '%{http_code}' \${functionUrl})
                            if [ "\$response" != "200" ]; then
                                echo "HTTP-triggered Cloud Function ${config.functionName} verification failed. HTTP response code: \$response"
                                exit 1
                            fi
                        """
                    }
                }
            }
        }
    }
}