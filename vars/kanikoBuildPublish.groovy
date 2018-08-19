#!/usr/bin/groovy

def call(config) {
    container(name: 'skaffold') {
        def dockerFile = config.dockerFileOverride != null ? config.dockerFileOverride : "Dockerfile"
        for (String tag: config.tags) {
            sh "skaffold run"
        }
    }
}
