#!/usr/bin/groovy

def call(config) {
    terraformPod(['arch':'arm64']) {
        checkout([
            $class: 'GitSCM',
            branches: [[name: 'dev']],
            userRemoteConfigs: [[credentialsId: 'bitbucket', url: params.GIT_URL]]
    ])
        def targetWorkspaces = params.targetWorkspaces.readLines()
        if (targetWorkspaces.size <= 0) {
            error "No Workspaces were specified"
        }
        def targetArguments = []
        if (config.TF_TARGETTED_RESOURCES) {
            def lines = config.TF_TARGETTED_RESOURCES.readLines()
            lines.each { line ->
                def trimmedLine = line.trim()
                if (trimmedLine) {
                    targetArguments << "--target=${trimmedLine}"
                }
            }
        }

        container('terraform') {
            sh script: "vault login -no-print --method gcp role=terraform-dev  && mkdir -p ~/.ssh/ ${config.TF_DIRECTORY}/logs/plans ${config.TF_DIRECTORY}/plans && chmod -R 777 ${config.TF_DIRECTORY}/logs/plans  && cp /dump/id_rsa ~/.ssh/id_rsa && chmod 0600 ~/.ssh/id_rsa && cp /dump/known_hosts ~/.ssh/known_hosts && cp /dump/config ~/.ssh/config"
            for (workspace in targetWorkspaces) {
                stage("Terraform Plan: ${workspace}") {
                    
                    sh script: "set -x && (cd ${config.TF_DIRECTORY} && terraform init && terraform workspace select ${workspace} && terraform plan -out plans/${workspace}.tfplan ${targetArguments.join(' ')}) | tee -a logs/plans/${workspace}.log"

                    archiveArtifacts artifacts: "${config.TF_DIRECTORY}/logs/plans/${workspace}.log", fingerprint: true
                    archiveArtifacts artifacts: "${config.TF_DIRECTORY}/plans/${workspace}.tfplan", fingerprint: true
                }
                stage("Terraform Apply: ${workspace}") {
                  input message: 'Review the Terraform plan. Proceed with apply?', ok: 'Apply', cancel: 'Abort'
                  sh script: "cd ${config.TF_DIRECTORY} && terraform workspace select ${workspace} && terraform apply plans/${workspace}.tfplan"
                }
            }

        }
    }
}
