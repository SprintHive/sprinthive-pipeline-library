def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [version: '']
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    container('clients') {
        def newVersion = config.version
        if (newVersion == '') {
            newVersion = getNewVersion {}
        }

        env.setProperty('VERSION', newVersion)

        dockerBuild(newVersion, config.name)

        return newVersion
    }
}

def dockerBuild(version, name){
    def newImageName = "${env.DOCKER_REGISTRY_SERVICE_HOST}:${env.DOCKER_REGISTRY_SERVICE_PORT}/${name}:${version}"

    sh "docker build -t ${newImageName} ."
    sh "docker push ${newImageName}"
}
