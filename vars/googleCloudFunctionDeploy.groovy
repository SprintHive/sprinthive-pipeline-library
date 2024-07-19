#!/usr/bin/groovy

def call(Map config) {
    stage('Prepare Function Archive') {
        sh """
            cd ${env.WORKSPACE}/${config.sourceFolderPath}
            tar -cvzf ../${config.functionName}.tar.gz .
            cd ..
            if [ ! -s ${config.functionName}.tar.gz ]; then
                echo "Error: Created archive is empty"
                exit 1
            fi
            echo "Contents of the archive:"
            tar -tvf ${config.functionName}.tar.gz
        """
    }

    stage('Debug Environment') {
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
            echo "\nGcloud version:"
            gcloud version
        """
    }

    stage("Upload Function Archive") {
        sh """
            if [ ! -f "${config.functionName}.tar.gz" ]; then
                echo "Error: Archive file not found"
                exit 1
            fi
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
                gcloud scheduler jobs create pubsub ${config.functionName}-scheduler \\
                    --schedule '${config.schedule}' \\
                    --topic ${config.topicName} \\
                    --message-body '${config.pubsubTargetData}' \\
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
                response=\$(curl -s -o /dev/null -w '%{http_code}' "\${functionUrl}")
                if [ "\$response" != "200" ]; then
                    echo "HTTP-triggered Cloud Function ${config.functionName} verification failed. HTTP response code: \$response"
                    exit 1
                fi
            """
        }
    }
}