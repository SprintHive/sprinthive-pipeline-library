#!/usr/bin/groovy

def call(config) {

    checkout([
            $class: 'GitSCM',
            branches: [[name: "dev"]],
            userRemoteConfigs: [[credentialsId: 'bitbucket', url: "https://bitbucket.org/sprinthive/infrastructure/src/main/"]]
    ])

    container('terraform') {
        sh script:"terraform version"
    }
}
