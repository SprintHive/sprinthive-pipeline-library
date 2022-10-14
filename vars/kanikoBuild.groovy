#!/usr/bin/groovy

def call(contextDir, destinationTarPath, destination, sourceVersion) {
    container('kaniko') {
        sh "/kaniko/executor --context \"$contextDir\" --tar-path \"$destinationTarPath\" --destination \"$destination\" --build-arg SOURCE_VERSION=\"$sourceVersion\" --log-format text --no-push"
    }
}
