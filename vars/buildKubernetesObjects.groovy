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

    if (config.serviceTemplate?.trim()) {
      templateFiles[SERVICE] = loadTemplate(config.serviceTemplate)
      println "Loaded partial service template"
    }
    if (config.deploymentTemplate?.trim()) {
      templateFiles[DEPLOYMENT] = loadTemplate(config.deploymentTemplate)
      println "Loaded partial deployment template"
    }

    def kubernetesObjects = []
    kubernetesObjects << generateServiceJson(config, env, templateFiles[SERVICE] ?: null)
    kubernetesObjects << generateDeploymentJson(config, env, templateFiles[DEPLOYMENT] ?: null)

    return createKubernetesList(kubernetesObjects)
}

def loadTemplate(templateFile) {
    try {
        return new JsonSlurperClassic().parseText(templateFile)
    } catch (Exception e) {
        println "Something went wrong parsing template: ${e.getMessage()}"
    }
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
						"name": "${config.name}",
						"labels": {
								"project": "${config.name}",
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
								"project": "${config.name}",
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
    def dockerRegistry= ''
    if (env.DOCKER_REGISTRY_SERVICE_HOST){
        dockerRegistry = env.DOCKER_REGISTRY_SERVICE_HOST+':'+env.DOCKER_REGISTRY_SERVICE_PORT+'/'
    }

    def deploymentJson = """
		{
				"kind": "Deployment",
        "apiVersion": "extensions/v1beta1",
				"metadata": {
            "name": "${config.name}",
						"labels": {
								"project": "${config.name}",
								"version": "${config.version}",
								"stage": "${config.stage}"
						}
				},
				"spec": {
          "replicas": 1,
          "selector": {
            "matchLabels": {
              "project": "${config.name}"
            }
          },
          "template": {
            "metadata": {
              "labels": {
                "project": "${config.name}",
                "version": "${config.version}",
								"stage": "${config.stage}"
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
                  "image": "${dockerRegistry}${env.KUBERNETES_NAMESPACE}/${config.name}:${config.version}",
                  "imagePullPolicy": "IfNotPresent",
                  "name": "${config.name}",
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
