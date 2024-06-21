#!/usr/bin/groovy

def call(Map config) {
    // Common required parameters for all trigger types
    def commonRequiredParams = [
        'functionName',
        'projectId',
        'serviceAccountEmail',
        'zipFilePath',
        'region',
        'environmentVariables',
        'runtime',
        'triggerType'
    ]

    // Verify common required parameters
    commonRequiredParams.each { param ->
        if (!config.containsKey(param) || config[param] == null || config[param].toString().trim().isEmpty()) {
            error("Required parameter '${param}' is missing or empty")
        }
    }

    // Verify trigger-specific parameters
    switch(config.triggerType) {
        case 'http':
            // No additional required parameters for HTTP trigger
            break
        case 'pubsub':
            def pubsubParams = ['topicName', 'schedule', 'timeZone', 'pubsubTargetData']
            pubsubParams.each { param ->
                if (!config.containsKey(param) || config[param] == null || config[param].toString().trim().isEmpty()) {
                    error("Required Pub/Sub parameter '${param}' is missing or empty")
                }
            }
            break
        default:
            error("Invalid triggerType: ${config.triggerType}. Supported types are 'http' and 'pubsub'")
    }

    def podLabel = "gcloud-${UUID.randomUUID().toString()}"
    
    // TODO: alter mountPath to be correct
    podTemplate(label: podLabel, yaml: '''
        apiVersion: v1
        kind: Pod
        spec:
          containers:
          - name: gcloud
            image: google/cloud-sdk:latest
            command:
            - cat
            tty: true
            volumeMounts:
            - name: workspace-volume
              mountPath: /home/jenkins/agent
          volumes:
          - name: workspace-volume
            emptyDir: {}
    ''') {
        node(podLabel) {
            stage('Verify GCloud Auth') {
                container('gcloud') {
                    sh 'gcloud auth list'
                    sh 'gcloud config list project'
                }
            }

            stage('Copy ZIP File') {
                container('gcloud') {
                    // Download the archived ZIP file
                    step([$class: 'CopyArtifact',
                          projectName: env.JOB_NAME,
                          filter: config.zipFilePath,
                          target: '/home/jenkins/agent/'])
                    
                    sh 'ls -la /home/jenkins/agent/'
                }
            }

            stage('Verify ZIP File') {
                container('gcloud') {
                    def zipFileName = config.zipFilePath.split('/')[-1]
                    if (!fileExists("/home/jenkins/agent/${zipFileName}")) {
                        error("ZIP file not found: /home/jenkins/agent/${zipFileName}")
                    }
                }
            }

            stage("Deploy Cloud Function: ${config.functionName}") {
                container('gcloud') {
                    def zipFileName = config.zipFilePath.split('/')[-1]
                    def deployCommand = """
                        set -x
                        gcloud config set project ${config.projectId}

                        gcloud functions deploy ${config.functionName} \
                            --runtime ${config.runtime} \
                            --region ${config.region} \
                            --source /home/jenkins/agent/${zipFileName} \
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

                    echo "Cloud Function ${config.functionName} deployed successfully."
                }
            }

            if (config.triggerType == 'pubsub') {
                stage("Create Pub/Sub Topic and Cloud Scheduler Job") {
                    container('gcloud') {
                        sh """
                            gcloud pubsub topics create ${config.topicName} || true

                            gcloud scheduler jobs create pubsub ${config.functionName}-scheduler \
                                --schedule '${config.schedule}' \
                                --topic ${config.topicName} \
                                --message-body '${config.pubsubTargetData}' \
                                --time-zone '${config.timeZone}' || true
                        """

                        echo "Pub/Sub topic and Cloud Scheduler job created successfully."
                    }
                }
            }

            stage("Verify Cloud Function: ${config.functionName}") {
                container('gcloud') {
                    sh """
                        functionStatus=\$(gcloud functions describe ${config.functionName} --region ${config.region} --format='value(status)')

                        if [ "\$functionStatus" == "ACTIVE" ]; then
                            echo "Cloud Function ${config.functionName} is verified and in ACTIVE state."
                        else
                            echo "Cloud Function ${config.functionName} verification failed. Status: \$functionStatus"
                            exit 1
                        fi
                    """

                    if (config.triggerType == 'http') {
                        sh """
                            functionUrl=\$(gcloud functions describe ${config.functionName} --region ${config.region} --format='value(httpsTrigger.url)')
                            response=\$(curl -s -o /dev/null -w '%{http_code}' \${functionUrl})

                            if [ "\$response" == "200" ]; then
                                echo "HTTP-triggered Cloud Function ${config.functionName} is responding successfully."
                            else
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
