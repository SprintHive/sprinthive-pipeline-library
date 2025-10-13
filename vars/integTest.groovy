#!/usr/bin/groovy

def call(config) {
    stage("Integration test") {
        node {
            checkout([
                    $class: 'GitSCM',
                    branches: [[name: config.branch]],
                    extensions: [[$class: 'GitLFSPull']],
                    userRemoteConfigs: [[credentialsId: 'bitbucket', url: "https://bitbucket.org/sprinthive/${config.repository}.git"]]
            ])
            podTemplateYaml = readFile("jenkins/integration-test-pod.yaml")
            podLabel = "integ-test-${config.application}-${UUID.randomUUID().toString()}"
            podTemplate(yaml: podTemplateYaml, label: podLabel, namespace: config.namespace) {
                node(podLabel) {
                    checkout([
                            $class: 'GitSCM',
                            branches: [[name: config.branch]],
                            extensions: [[$class: 'GitLFSPull']],
                            userRemoteConfigs: [[credentialsId: 'bitbucket', url: "https://bitbucket.org/sprinthive/${config.repository}.git"]]
                    ])
                    container('test') {
                        exports = []
                        if (config.envVars != null) {
                            config.envVars.each { envVar ->
                                exports.add("${envVar.key}=${envVar.value}")
                            }
                        }
                        sh "${exports.join(" ")} ./jenkins/integrationTest.sh"
                    }
                }
            }
        }
    }
}