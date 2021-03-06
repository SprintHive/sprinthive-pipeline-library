#!/usr/bin/groovy

def call(Map parameters = [:], body) {

    def defaultLabel = "gradle.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    gradleTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
