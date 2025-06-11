#!/usr/bin/groovy

def call(imageName, contextDir) {
    def additionalOptions = ""
    if (fileExists("$contextDir/grype.yaml")) {
        additionalOptions += "-c $contextDir/grype.yaml"
    }
    container('grype-scanner') {
        sh "sleep 100000 && /grype $imageName --fail-on high $additionalOptions"
    }
}
