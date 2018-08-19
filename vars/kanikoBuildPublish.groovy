#!/usr/bin/groovy

def call(config) {
    container(name: 'skaffold') {
        withCredentials([file(credentialsId: config.registryCredentialsId, variable: 'GOOGLE_APPLICATION_CREDENTIALS')]) {
            def dockerFile = config.dockerFileOverride != null ? config.dockerFileOverride : "Dockerfile"
            for (String tag: config.tags) {
                sh "skaffold run"
            }
        }
    }
}
