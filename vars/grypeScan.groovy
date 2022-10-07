#!/usr/bin/groovy

def call(imageName) {
    container('grype-scanner') {
        sh "/grype $imageName"
    }
}
