#!/usr/bin/groovy

def call(Map parameters = [:], body) {

    def defaultLabel = buildId('nodejs')
    def label = parameters.get('label', defaultLabel)

    nodejsClientsTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
