#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def version = sh(script: 'git rev-parse --short HEAD', returnStdout: true).toString().trim()
    return version
}
