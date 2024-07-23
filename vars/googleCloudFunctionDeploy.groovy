#!/usr/bin/groovy

/**
 * Deploy a Google Cloud Function (HTTP or PubSub triggered)
 *
 * CLI docs: https://cloud.google.com/sdk/gcloud/reference/functions/deploy
 *
 * @param config A map containing configuration parameters for the function deployment:
 *   functionName        : Name of the Cloud Function (String, required)
 *   projectId           : Google Cloud Project ID (String, required)
 *                         - make sure the project has Jenkins worker as a principal (currently for qa and prod)
 *   gcsPath             : Google Cloud Storage path for the function archive (String, required)
 *   runtime             : Runtime for the Cloud Function (String, required)
 *   region              : Deployment region (String, required)
 *   serviceAccountEmail : Service account email for the function (String, required)
 *   environmentVariables: Map of environment variables for the function (Map, required)
 *   entryPoint          : Entry point for the function (String, optional)
 *   timeout             : Function timeout (String, optional)
 *   maxInstances        : Maximum number of function instances (Integer, optional)
 *   triggerType         : Type of trigger - 'http' or 'pubsub' (String, required)
 *   topicName           : PubSub topic name (required if triggerType is 'pubsub')
 *   generation          : Cloud Function generation - 'gen1' or 'gen2' (String, required)
 */
def call(Map config) {
    // Validate required parameters
    def requiredParams = ['functionName', 'projectId', 'gcsPath', 'runtime', 'region', 'serviceAccountEmail', 'environmentVariables', 'triggerType', 'generation']
    requiredParams.each { param ->
        if (!config.containsKey(param)) {
            error "Missing required parameter: ${param}"
        }
    }

    if (config.triggerType == 'pubsub' && !config.containsKey('topicName')) {
        error "Missing required parameter for PubSub trigger: topicName"
    }

    if (!['gen1', 'gen2'].contains(config.generation)) {
        error "Invalid generation specified. Must be 'gen1' or 'gen2'."
    }

    stage('Checkout') {
        checkout scm
    }

    stage('Prepare Function Archive') {
        prepareArchive(config.functionName)
    }
    
    def podLabel = "gcloud-${UUID.randomUUID().toString()}"
    
    podTemplate(label: podLabel, yaml: getGcloudPodYaml()) {
        node(podLabel) {
            stage('Copy Function Archive') {
                copyArchive(config.functionName)
            }

            container('gcloud') {
                stage("Upload Function Archive") {
                    uploadArchive(config)
                }

                stage("Deploy Cloud Function: ${config.functionName}") {
                    deployFunction(config)
                }
            }
        }
    }
}

def prepareArchive(String functionName) {
    def archiveName = "${functionName}.zip"
    
    sh """
        cd ${env.WORKSPACE}/${functionName}
        jar -cvf ../${archiveName} .
        cd ..
        if [ ! -s ${archiveName} ]; then
            echo "Error: Created archive is empty"
            exit 1
        fi
    """
    
    archiveArtifacts artifacts: archiveName, fingerprint: true
}

def getGcloudPodYaml() {
    return """
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
}

def copyArchive(String functionName) {
    container('gcloud') {
        script {
            try {
                def archiveName = "${functionName}.zip"
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

def uploadArchive(Map config) {
    sh """
        gcloud config set project ${config.projectId}
        gcloud storage cp ${config.functionName}.zip ${config.gcsPath}
    """
}

def deployFunction(Map config) {
    def deployCommand = "gcloud functions deploy ${config.functionName}"
    
    if (config.generation == 'gen2') {
        deployCommand += " --gen2 \\"
    }
    
    deployCommand += """
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

    def result = sh(script: deployCommand, returnStdout: true).trim()

    // Print the deployment result for verfication
    echo "Deployment result: ${result}"
}