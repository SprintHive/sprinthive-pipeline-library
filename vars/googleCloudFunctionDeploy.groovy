#!/usr/bin/groovy

def call(Map config) {
    stage('Checkout') {
        checkout scm
    }

    stage('Prepare Function Archive') {
        def archiveName = "${config.functionName}.zip"
        
        sh """
            cd ${env.WORKSPACE}/${config.functionName}
            jar -cvf ../${archiveName} .
            cd ..
            if [ ! -s ${archiveName} ]; then
                echo "Error: Created archive is empty"
                exit 1
            fi
        """
        
        archiveArtifacts artifacts: archiveName, fingerprint: true
    }
    
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
            stage('Copy Function Archive') {
                container('gcloud') {
                    script {
                        try {
                            def archiveName = "${config.functionName}.zip"
                            copyArtifacts(
                                projectName: env.JOB_NAME, 
                                selector: specific(env.BUILD_NUMBER), 
                                filter: archiveName, 
                                fingerprintArtifacts: true
                            )
                            
                            sh """
                                if [ ! -f "${archiveName}" ]; then
                                    echo "Error: Archive file not found in current directory"
                                    exit 1
                                fi
                            """
                        } catch (Exception e) {
                            error "Failed to process function archive: ${e.message}"
                        }
                    }
                }
            }

            container('gcloud') {
                stage("Upload Function Archive") {
                    sh """
                        gcloud config set project ${config.projectId}
                        gcloud storage cp ${config.functionName}.zip ${config.gcsPath}
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
                    """

                    if (config.triggerType == 'http') {
                        deployCommand += "    --trigger-http"
                    } else if (config.triggerType == 'pubsub') {
                        deployCommand += "    --trigger-topic ${config.topicName}"
                    }

                    sh deployCommand
                }
            }
        }
    }
}