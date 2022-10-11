#!/usr/bin/groovy

def call(destination, containerTar) {
    container('crane') {
        sh "/ko-app/gcrane push $containerTar $destination"
    }
}
