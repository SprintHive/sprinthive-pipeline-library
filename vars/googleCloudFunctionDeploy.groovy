#!/usr/bin/groovy

import groovy.json.JsonSlurper

/**
 * Deploy a Google Cloud Function (HTTP or PubSub triggered)
 * When using this remember to configure discarding artifacts so we don't hoard them
 *
 * CLI docs: https://cloud.google.com/sdk/gcloud/reference/functions/deploy
 *
 * @param config A map containing configuration parameters for the function deployment:
 *   functionName           : Name of the Cloud Function (String, required)
 *   projectId              : Google Cloud Project ID (String, required)
 *                              - make sure the project has Jenkins worker as a principal (currently for qa and prod)
 *   runtime                : Runtime for the Cloud Function (String, required)
 *   region                 : Deployment region (String, required)
 *   serviceAccountEmail    : Service account email for the function (String, required)
 *   environmentVariables   : Map of environment variables for the function (Map, optional)
 *   entryPoint             : Entry point for the function (String, optional)
 *   timeout                : Function timeout (String, optional)
 *   maxInstances           : Maximum number of function instances (Integer, optional)
 *   triggerType            : Type of trigger - 'http' or 'pubsub' (String, required)
 *   topicName              : PubSub topic name (required if triggerType is 'pubsub')
 *   memory                 : Memory allocation for the function (String, optional)
 *   concurrency            : Maximum number of concurrent requests (Integer, optional)
 *   cpu                    : CPU allocation for the function (Integer, optional)
 *   ingress                : Ingress settings controlling what traffic can reach the function ('all' || 'internal-only' || 'internal-and-gclb', optional, default 'all')
 *   allowUnauthenticated   : If true this will allow all callers, without checking authentication (boolean, optional, default false)
 *   version                : function version tag (string, required)
 */
def call(Map config) {
    // Validate required parameters
    def requiredParams = [
            'functionName',
            'projectId',
            'serviceAccountEmail',
            'runtime',
            'region',
            'triggerType',
            'version'
    ]
    requiredParams.each { param ->
        if (!config.containsKey(param)) {
            error "Missing required parameter: ${param}"
        }
    }

    if (config.triggerType == 'pubsub' && !config.containsKey('topicName')) {
        error "Missing required parameter for PubSub trigger: topicName"
    }

    if (!['all', 'internal-only', 'internal-and-gclb', null].contains(config.ingress)) {
        error "Invalid ingress specified. Must be 'all', 'internal-only', 'internal-and-gclb'."
    }

    def podLabel = "gcloud-${UUID.randomUUID().toString()}"

    podTemplate(label: podLabel, yaml: getGcloudPodYaml()) {
        node(podLabel) {
            def scmInfo = checkout scm
            def envInfo = environmentInfo(scmInfo)
            echo "Current branch is: ${envInfo.branch}"

            container('gcloud') {
                stage("Deploy Cloud Function: ${config.functionName}") {
                    deployFunction(config)
                }
            }
        }
    }
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

def deployFunction(Map config) {
    def deployCommand = "gcloud functions deploy ${config.functionName} \\"

    deployCommand += """
        --gen2 \\
        --project ${config.projectId} \\
        --runtime ${config.runtime} \\
        --region ${config.region} \\
        --concurrency ${config.concurrency ? config.concurrency : 1} \\
        --cpu ${config.cpu ? config.cpu : 1} \\
        --update-labels version=${config.version.replaceAll("\\.", "-")} \\
        ${config.environmentVariables != null && !config.environmentVariables.isEmpty() ? "--set-env-vars " + config.environmentVariables.collect { "${it.key}=${it.value}" }.join(',') : "" } \\
        --ingress-settings ${config.ingress != null ? config.ingress : 'all'} \\
        ${config.entryPoint ? "--entry-point ${config.entryPoint}" : ''} \\
        ${config.timeout ? "--timeout ${config.timeout}" : ''} \\
        ${config.maxInstances ? "--max-instances ${config.maxInstances}" : ''} \\
        ${config.memory ? "--memory=${config.memory}" : ''} \\
        ${config.allowUnauthenticated ? "--allow-unauthenticated" : '--no-allow-unauthenticated'} \\
    """
    if (config.triggerType == 'http') {
        deployCommand += "    --trigger-http"
    } else if (config.triggerType == 'pubsub') {
        deployCommand += "    --trigger-topic ${config.topicName}"
    }

    // Add --format=json to get the output in JSON format
    deployCommand += " --format=json"

    def result = sh(script: deployCommand, returnStdout: true).trim()

    // Parse the JSON result
    def jsonSlurper = new JsonSlurper()
    def jsonResult = jsonSlurper.parseText(result)

    // Print the URL only if it's an HTTP function
    if (config.triggerType == 'http' && jsonResult.url) {
        echo "Function URL: ${jsonResult.url}"
    }
}