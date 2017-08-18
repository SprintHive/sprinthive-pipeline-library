#!/usr/bin/groovy
import groovy.io.FileType
import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput
import com.cloudbees.groovy.cps.NonCPS

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def templateVars = [
        "image": config.image,
		"version": config.version,
		"stage": config.stage,
        "name": config.name
    ]

    def populatedTemplates = []
    for (String template : config.templates) {
        def populatedTemplate = populateTemplate(template, templateVars)
        populatedTemplates << populatedTemplate
        println "Generated resource: "
        println populatedTemplate
    }

    return populatedTemplates
}

@NonCPS
def populateTemplate(template, templateVars) {
    def engine = new groovy.text.SimpleTemplateEngine()
    return engine.createTemplate(template).make(templateVars)
}
