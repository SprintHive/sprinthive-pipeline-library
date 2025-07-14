#!/usr/bin/groovy

def call(contextDir, destinationTarPath, destination, sourceVersion) {
    def extraArgs = [
        '--snapshot-mode=redo',
        '--cache=true',
        '--cache-repo=eu.gcr.io/sh-honeycomb/kaniko-cache',
        '--compressed-caching'
    ].join(' ')

    container('kaniko') {
        sh """
          /kaniko/executor \
            --dockerfile "${contextDir}/Dockerfile" \
            --context "${contextDir}" \
            --tar-path "${destinationTarPath}" \
            --destination "${destination}" \
            --build-arg SOURCE_VERSION="${sourceVersion}" \
            ${extraArgs} \
            --log-format text \
            --no-push
        """
    }
}
