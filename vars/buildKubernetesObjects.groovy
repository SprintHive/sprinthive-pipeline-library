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
    def SERVICE = 'service'
    def DEPLOYMENT = 'deployment'
    def templateFiles = [:]

    if (config.templatePath?.trim()) {
        def jsonParser = new JsonSlurperClassic()
        def templateDir = new File(config.templatePath)
        try {
            templateDir.eachFileRecurse(FileType.FILES) { file ->
                switch (file.getName()) {
                    case "${SERVICE}.json":
                        templateFiles[SERVICE] = jsonParser.parseText(file.text)
                        println "Using template file: ${SERVICE}.json"
                        break
                    case "${DEPLOYMENT}.json":
                        templateFiles[DEPLOYMENT] = jsonParser.parseText(file.text)
                        println "Using template file: ${DEPLOYMENT}.json"
                        break
                }
            }
        } catch(FileNotFoundException e) {
            println "templatePath does not exist"
        } catch (IllegalArgumentException e) {
            println "Could not read files in templatePath: ${e.getMessage()}"
        }
    }

    def kubernetesObjects = []
    kubernetesObjects << generateServiceJson(config, env, templateFiles[SERVICE] ?: null)
    kubernetesObjects << generateDeploymentJson(config, env, templateFiles[DEPLOYMENT] ?: null)

    return createKubernetesList(kubernetesObjects)
}

def createKubernetesList(kubernetesObjects) {
    println "About to serialize items"
    def items = JsonOutput.prettyPrint(JsonOutput.toJson(kubernetesObjects))
    println "About to create list"
    def listJson = """
    {
        "kind": "List",
        "apiVersion": "v1",
        "items": ${items}
    }
    """
    println "created json list"
    println listJson
    println "done"

    return listJson
}

def mergeObjectOverrides(jsonObject, jsonObjectOverride) {
    if (jsonObjectOverride instanceof Map) {
        jsonObjectOverride.each {
            if (jsonObject.containsKey(it.getKey())) {
                jsonObject["${it.getKey()}"] = mergeObjectOverrides(jsonObject[it.getKey()], it.getValue())
            } else {
                jsonObject.put(it.getKey(), it.getValue())
            }
        }
    } else {
        jsonObject = jsonObjectOverride
    }

    return jsonObject
}

def generateServiceJson(config, env, partialTemplate = null) {
    def serviceJson = """
		{
				"kind": "Service",
				"apiVersion": "v1",
				"metadata": {
						"name": "${env.JOB_NAME}",
						"labels": {
								"project": "${env.JOB_NAME}",
								"expose": "${config.expose ?: 'true'}",
								"version": "${config.version}"
						}
				},
				"spec": {
						"ports": [
              {
                  "protocol": "TCP",
                  "port": 80,
                  "targetPort": ${config.port}
              }
            ],
						"selector": {
								"project": "${env.JOB_NAME}",
								"expose": "${config.expose ?: 'true'}",
								"version": "${config.version}"
						},
						"type": "ClusterIP",
						"sessionAffinity": "None"
				}
		}
    """

    println "Created service json"

    jsonObject = new JsonSlurperClassic().parseText(serviceJson)

    if (partialTemplate != null) {
        mergeObjectOverrides(jsonObject, partialTemplate)
    }

    println "Merged service json and created json object"

    return jsonObject
}

def generateDeploymentJson(config, env, partialTemplate = null) {
    def fabric8Registry = ''
    if (env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST){
        fabric8Registry = env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST+':'+env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT+'/'
    }

    def deploymentJson = """
		{
				"kind": "Deployment",
        "apiVersion": "extensions/v1beta1",
				"metadata": {
            "name": "${env.JOB_NAME}",
						"labels": {
								"project": "${env.JOB_NAME}",
								"version": "${config.version}"
						}
				},
				"spec": {
          "replicas": 1,
          "selector": {
            "matchLabels": {
              "project": "${env.JOB_NAME}"
            }
          },
          "template": {
            "metadata": {
              "labels": {
                "project": "${env.JOB_NAME}",
                "version": "${config.version}"
              }
            },
            "spec": {
              "containers": [
                {
                  "env": [
                    {
                      "name": "KUBERNETES_NAMESPACE",
                      "valueFrom": {
                        "fieldRef": {
                          "fieldPath": "metadata.namespace"
                        }
                      }
                    }
                  ],
                  "image": "${fabric8Registry}${env.KUBERNETES_NAMESPACE}/${env.JOB_NAME}:${config.version}",
                  "imagePullPolicy": "IfNotPresent",
                  "name": "${env.JOB_NAME}",
                  "ports": [
                    {
                      "containerPort": ${config.port},
                      "name": "http"
                    }
                  ],
                  "resources": {
                    "limits": {
                      "cpu": "${config.resourceRequestCPU ?: 0}",
                      "memory": "${config.reourceRequestMemory ?: 0}"
                    },
                    "requests": {
                      "cpu": "${config.resourceLimitCPU ?: 0}",
                      "memory": "${config.resourceLimitMemory ?: 0}"
                    }
                  }
                }
              ],
              "terminationGracePeriodSeconds": 2
            }
          }
				}
		}
    """

    println "Created deployment json"
    jsonObject = new JsonSlurperClassic().parseText(deploymentJson)

    if (partialTemplate != null) {
        mergeObjectOverrides(jsonObject, partialTemplate)
    }

    println "Merged deployment json and created deployment object"

    return jsonObject
}
