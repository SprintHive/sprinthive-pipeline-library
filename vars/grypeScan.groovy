#!/usr/bin/groovy

def call(imageName) {
    def additionalOptions = ""
    if (fileExists("grype.yaml")) {
        additionalOptions += "-c grype.yaml"
    }
    container('grype-scanner') {
        sh "/grype $imageName --fail-on high $additionalOptions"
    }
}
