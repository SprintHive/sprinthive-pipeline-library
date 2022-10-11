#!/usr/bin/groovy

def call(imageName) {
    def additionalOptions = ""
    if (new File("grype.yaml").exists()) {
        additionalOptions += "-c grype.yaml"
    }
    container('grype-scanner') {
        sh "/grype $imageName --fail-on high $additionalOptions -c grype.yaml"
    }
}
