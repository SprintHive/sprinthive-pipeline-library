#!/usr/bin/groovy

def call(contextDir, destinationTarPath, destination, sourceVersion) {
    container('kaniko') {
        sh "busybox rm -fr /app"
        sh "/kaniko/executor --custom-platform=linux/arm64/v8 --dockerfile \"$contextDir/Dockerfile\" --context \"$contextDir\" --tar-path \"$destinationTarPath\" --destination \"$destination\" --build-arg SOURCE_VERSION=\"$sourceVersion\" --log-format text --no-push"
    }
}
