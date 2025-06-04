#!/usr/bin/groovy

def call(config) {
    terraformPod(['arch':'arm64']) {
        checkout([
            $class: 'GitSCM',
            branches: [[name: 'dev']],
            userRemoteConfigs: [[credentialsId: 'bitbucket', url: '${config.GIT_URL}']]
    ])

        container('terraform') {
            stage('Terraform Plan') {
                sh script: 'mkdir ~/.ssh/ && cp /dump/id_rsa ~/.ssh/id_rsa && chmod 0600 ~/.ssh/id_rsa && cp /dump/known_hosts ~/.ssh/known_hosts && cp /dump/config ~/.ssh/config'
                sh script: "cd ${config.TF_DIRECTORY} && terraform init && vault login -no-print --method gcp role=terraform-dev  && terraform workspace select ${params.TF_WORKSPACE_EXPLICIT} && terraform plan -out plan.tfplan --target=${params.TARGETTED_RESOURCES}"
            //archiveArtifacts artifacts: 'plan.tfplan', fingerprint: true
            }
            stage('Terraform Apply') {
                input message: 'Review the Terraform plan. Proceed with apply?', ok: 'Apply', cancel: 'Abort'
                sh script: "cd ${config.TF_DIRECTORY} && terraform workspace select ${config.TF_WORKSPACE_EXPLICIT} && terraform apply plan.tfplan"
             }
        }
    }
}
