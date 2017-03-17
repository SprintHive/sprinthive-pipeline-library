#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [version:'']
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def newVersion = ''
    if (config.version == '') {
        newVersion = getNewVersion {}
    } else {
        newVersion = config.version
    }

    def flow = new io.fabric8.Fabric8Commands()

    env.setProperty('VERSION',newVersion)

    kubernetes.image().withName("${config.name}").build().fromPath(".")
    kubernetes.image().withName("${config.name}").tag().inRepository("${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${env.KUBERNETES_NAMESPACE}/${config.name}").withTag(newVersion)

    if (flow.isSingleNode()){
        echo 'Running on a single node, skipping docker push as not needed'
    } else {
        kubernetes.image().withName("${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${env.KUBERNETES_NAMESPACE}/${config.name}").push().withTag(newVersion).toRegistry()
    }

    return newVersion
  }
