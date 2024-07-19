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
                            // Copy the archive to a known location in the Jenkins home directory
                            def artifactDir = "/home/jenkins/function-archives"
                            sh "mkdir -p ${artifactDir}"
                            
                            step([$class: 'CopyArtifact',
                                projectName: env.JOB_NAME,
                                filter: "${config.functionName}.tar.gz",
                                fingerprintArtifacts: true,
                                target: artifactDir,
                                selector: [$class: 'SpecificBuildSelector', buildNumber: env.BUILD_NUMBER]])
                            
                            // Move the archive to the current working directory
                            sh """
                                mv ${artifactDir}/${config.functionName}.tar.gz .
                                echo "Archive file details:"
                                ls -l ${config.functionName}.tar.gz || echo "Archive not found"
                                if [ -f "${config.functionName}.tar.gz" ]; then
                                    echo "\nContents of the archive:"
                                    tar -tvf ${config.functionName}.tar.gz
                                else
                                    echo "Error: Archive file not found"
                                    exit 1
                                fi
                            """
                        } catch (Exception e) {
                            echo "Error occurred while copying artifact: ${e.message}"
                            echo "Stack trace: ${e.stackTrace.join('\n')}"
                            error "Failed to copy function archive"
                        }
                    }
                }
            }

            container('gcloud') {
                stage("Upload Function Archive") {
                    sh """
                        if [ ! -f "${config.functionName}.tar.gz" ]; then
                            echo "Error: Archive file not found"
                            exit 1
                        fi
                        gcloud auth activate-service-account --key-file=\$GOOGLE_APPLICATION_CREDENTIALS
                        gcloud config set project ${config.projectId}
                        gcloud storage cp ${config.functionName}.tar.gz ${config.gcsPath}
                        echo "Function archive uploaded to ${config.gcsPath}"
                        gcloud storage ls -l ${config.gcsPath}
                    """
                }

                stage("Deploy Cloud Function: ${config.functionName}") {
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
                            gcloud scheduler jobs create pubsub "${config.functionName}-scheduler" \\
                                --schedule '${config.schedule}' \\
                                --topic "${config.topicName}" \\
                                --message-body '${config.pubsubTargetData}' \\
                                --time-zone '${config.timeZone}' || true
                        """
                    }
                }

                stage("Verify Cloud Function: ${config.functionName}") {
                    sh """
                        functionStatus=\$(gcloud functions describe "${config.functionName}" --region "${config.region}" --format='value(status)')
                        if [ "\$functionStatus" != "ACTIVE" ]; then
                            echo "Cloud Function ${config.functionName} verification failed. Status: \$functionStatus"
                            exit 1
                        fi
                    """

                    if (config.triggerType == 'http') {
                        sh """
                            functionUrl=\$(gcloud functions describe "${config.functionName}" --region "${config.region}" --format='value(httpsTrigger.url)')
                            response=\$(curl -s -o /dev/null -w '%{http_code}' "\${functionUrl}")
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