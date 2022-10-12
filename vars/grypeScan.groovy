#!/usr/bin/groovy

def call(imageName, contextDir) {
    def additionalOptions = ""
    if (fileExists("$contextDir/grype.yaml")) {
        additionalOptions += "-c $contextDir/grype.yaml"
    }
    container('grype-scanner') {
        sh "/grype $imageName --fail-on high $additionalOptions"
    }
}
