import io.fabric8.Fabric8Commands
import io.fabric8.Utils

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
    def utils = new Utils()
    def flow = new Fabric8Commands()
    def namespace = utils.getNamespace()
    def newImageName = "${env.DOCKER_REGISTRY_SERVICE_HOST}:${env.DOCKER_REGISTRY_SERVICE_PORT}/${namespace}/${name}:${version}"

    sh "docker push ${newImageName}"
}
