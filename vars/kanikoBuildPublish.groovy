#!/usr/bin/groovy

def call(config) {
    def label = "kaniko-${UUID.randomUUID().toString()}"
    podTemplate(label: label, inheritFrom: 'base', containers: [
        containerTemplate(name: 'jnlp', image: 'jenkins/jnlp-slave:alpine', args: '${computer.jnlpmac} ${computer.name}'),
        containerTemplate(name: 'kaniko', image: 'gcr.io/kaniko-project/executor:debug', command: '/busybox/sh -c', args: 'cat', ttyEnabled: true)])
        {
            node(label) {
                checkout scm
                container(name: 'kaniko', shell: '/busybox/sh') {
                    withCredentials([file(credentialsId: config.registryCredentialsId, variable: 'GOOGLE_APPLICATION_CREDENTIALS')]) {
                        for (String dockerTag : config.dockerTags) {
                            sh """#!/busybox/sh
                            /kaniko/executor -f `pwd`/Dockerfile -c `pwd` --destination=${config.registryUrl}/${config.dockerTagBase}:${config.componentName}:${config.dockerTag}
                            """
                        }
                    }
                }
            }
       }
}
