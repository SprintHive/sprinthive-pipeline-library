#!/usr/bin/groovy
import groovy.io.FileType
import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def dockerRegistry= ''
    if (env.DOCKER_REGISTRY_SERVICE_HOST){
        dockerRegistry = env.DOCKER_REGISTRY_SERVICE_HOST+':'+env.DOCKER_REGISTRY_SERVICE_PORT+'/'
    }

    def templateVars = [
        "image": "${dockerRegistry}${env.KUBERNETES_NAMESPACE}/${config.name}:${config.version}",
		"version": config.version,
		"stage": config.stage
    ]

    def engine = new groovy.text.SimpleTemplateEngine()
    def populatedTemplate = engine.createTemplate(config.template).make(templateVars)

    return populatedTemplate
}
