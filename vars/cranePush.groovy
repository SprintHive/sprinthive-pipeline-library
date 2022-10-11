#!/usr/bin/groovy

def call(containerTar, destination) {
    container('crane') {
        sh "/ko-app/gcrane push $containerTar $destination"
    }
}
