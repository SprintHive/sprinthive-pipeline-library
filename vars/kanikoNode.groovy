#!/usr/bin/groovy

def call(Map parameters = [:], body) {

    def defaultLabel = buildId('kaniko')
    def label = parameters.get('label', defaultLabel)

    kanikoTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
