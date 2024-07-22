#!/usr/bin/groovy

def call(Map config) {
    def podLabel = "gcloud-${UUID.randomUUID().toString()}"
    
    podTemplate(
        label: podLabel, 
        yaml: """
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
              - name: jenkins-home
                emptyDir: {}
        """
    ) {
        node(podLabel) {
            stage('Debug Environment') {
                container('gcloud') {
                    sh """
                        echo "Current working directory:"
                        pwd
                        echo "\nDirectory contents:"
                        ls -la
                        echo "\nJenkins workspace:"
                        echo ${env.WORKSPACE}
                        echo "\nJob name:"
                        echo ${env.JOB_NAME}
                        echo "\nBuild number:"
                        echo ${env.BUILD_NUMBER}
                    """
                }
            }

            stage('Copy Function Archive') {
                container('gcloud') {
                    script {
                        try {
                            def archiveName = "${config.functionName}.zip"
                            
                            echo "Debugging archive copying process"
                            echo "Archive name: ${archiveName}"
                            echo "Current workspace: ${env.WORKSPACE}"
                            
                            sh "ls -la ${env.WORKSPACE}"
                            
                            echo "Attempting to copy from artifacts..."
                            echo "Job name: ${env.JOB_NAME}"
                            echo "Build number: ${env.BUILD_NUMBER}"
                            
                            // Use copyArtifacts step to retrieve the archive
                            copyArtifacts(
                                projectName: env.JOB_NAME, 
                                selector: specific(env.BUILD_NUMBER), 
                                filter: archiveName, 
                                fingerprintArtifacts: true
                            )
                            
                            sh "ls -la"  // Check if the artifact was copied successfully
                            
                            // Check if the file exists in the current directory
                            sh """
                                echo "Current directory contents after copy attempt:"
                                ls -la
                                if [ -f "${archiveName}" ]; then
                                    echo "Contents of the archive:"
                                    jar -tvf ${archiveName}
                                else
                                    echo "Error: Archive file not found in current directory"
                                    exit 1
                                fi
                            """
                        } catch (Exception e) {
                            echo "Error occurred while handling function archive: ${e.message}"
                            echo "Stack trace: ${e.stackTrace.join('\n')}"
                            error "Failed to process function archive"
                        }
                    }
                }
            }

            container('gcloud') {
                stage("Upload Function Archive") {
                    sh """
                        if [ ! -f "${config.functionName}.zip" ]; then
                            echo "Error: Archive file not found"
                            exit 1
                        fi
                        gcloud config set project ${config.projectId}
                        gcloud storage cp ${config.functionName}.zip ${config.gcsPath}
                        echo "Function archive uploaded to ${config.gcsPath}"
                        gcloud storage ls -l ${config.gcsPath}
                    """
                }

                stage("Deploy Cloud Function: ${config.functionName}") {
                    echo "Debug: config object = ${config}"
                    echo "Debug: triggerType = ${config.triggerType}"

                    def deployCommand = """
                        gcloud functions deploy ${config.functionName} \\
                            --runtime ${config.runtime} \\
                            --region ${config.region} \\
                            --source ${config.gcsPath} \\
                            --service-account ${config.serviceAccountEmail} \\
                            --set-env-vars ${config.environmentVariables.collect { "${it.key}=${it.value}" }.join(',')} \\
                            ${config.entryPoint ? "--entry-point ${config.entryPoint}" : ''} \\
                            ${config.timeout ? "--timeout ${config.timeout}" : ''} \\
                            ${config.maxInstances ? "--max-instances ${config.maxInstances}" : ''} \\
                            --verbosity debug \\
                    """

                    if (config.triggerType == 'http') {
                        deployCommand += "    --trigger-http"
                    } else if (config.triggerType == 'pubsub') {
                        deployCommand += "    --trigger-topic ${config.topicName}"
                    }

                    echo "Debug: Final deployCommand = ${deployCommand}"
                    sh deployCommand
                }

                if (config.triggerType == 'pubsub') {
                    stage("Create Pub/Sub Topic and Cloud Scheduler Job") {
                        sh """
                            gcloud scheduler jobs create pubsub "${config.functionName}-scheduler" \\
                                --schedule '${config.schedule}' \\
                                --topic "${config.topicName}" \\
                                --message-body '${config.pubsubTargetData}' \\
                                --time-zone '${config.timeZone}' || true
                        """
                    }
                }

                // stage("Verify Cloud Function: ${config.functionName}") {
                //     sh """
                //         functionStatus=\$(gcloud functions describe "${config.functionName}" --region "${config.region}" --format='value(status)')
                //         if [ "\$functionStatus" != "ACTIVE" ]; then
                //             echo "Cloud Function ${config.functionName} verification failed. Status: \$functionStatus"
                //             exit 1
                //         fi
                //     """

                //     if (config.triggerType == 'http') {
                //         sh """
                //             functionUrl=\$(gcloud functions describe "${config.functionName}" --region "${config.region}" --format='value(httpsTrigger.url)')
                //             response=\$(curl -s -o /dev/null -w '%{http_code}' "\${functionUrl}")
                //             if [ "\$response" != "200" ]; then
                //                 echo "HTTP-triggered Cloud Function ${config.functionName} verification failed. HTTP response code: \$response"
                //                 exit 1
                //             fi
                //         """
                //     }
                // }
            }
        }
    }
}