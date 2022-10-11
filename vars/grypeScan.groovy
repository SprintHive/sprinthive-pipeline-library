#!/usr/bin/groovy

def call(imageName) {
    container('grype-scanner') {
        def additionalOptions = ""
        if (new File("grype.yaml").exists()) {
            additionalOptions += "-c grype.yaml"
        }
        sh "/grype $imageName --fail-on high $additionalOptions"
    }
}
