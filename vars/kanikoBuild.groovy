#!/usr/bin/groovy

def call(contextDir, destinationTarPath, destination, sourceVersion, extraBuildArgs = "") {
    container('kaniko') {
        sh "/kaniko/executor --dockerfile \"$contextDir/Dockerfile\" --context \"$contextDir\" --tar-path \"$destinationTarPath\" --destination \"$destination\" --build-arg SOURCE_VERSION=\"$sourceVersion\" ${extraBuildArgs} --log-format text --no-push"
    }
}
