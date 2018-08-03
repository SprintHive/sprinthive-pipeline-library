#!/usr/bin/groovy

def call(config) {
    container(name: 'kaniko', shell: '/busybox/sh') {
        withCredentials([file(credentialsId: config.registryCredentialsId, variable: 'GOOGLE_APPLICATION_CREDENTIALS')]) {
            def dockerFile = config.dockerFileOverride != null ? config.dockerFileOverride : "Dockerfile"
            for (String tag: config.tags) {
                sh """#!/busybox/sh
                /kaniko/executor -f `pwd`/${dockerFile} -c `pwd` --destination=${config.registryUrl}/${config.registryBasePath}:${config.imageName}:${tag}
                """
            }
        }
    }
}
