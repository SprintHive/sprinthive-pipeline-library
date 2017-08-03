#!/usr/bin/groovy

def call(Map parameters = [:], body) {

    def defaultLabel = "maven.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    shMavenTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
