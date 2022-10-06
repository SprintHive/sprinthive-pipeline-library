#!/usr/bin/groovy

def call(source, containerTar) {
    container('crane') {
        sh "/ko-app/gcrane pull $source $containerTar"
    }
}
