#!/usr/bin/groovy

import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import groovy.json.JsonSlurperClassic

def call(String pvcJsonString, String namespace) {
    def kubernetes = new DefaultKubernetesClient()

	def pvcName = getPvcName(pvcJsonString)
 	def foundPvc = false
    for (PersistentVolumeClaim pvc : kubernetes.inNamespace(namespace).persistentVolumeClaims().list()
            .getItems()) {
        if (pvc.getMetadata().getName().equals(pvcName)) {
            foundPvc = true
        }
    }

	return foundPvc
}

def getPvcName(pvcJsonString) {
    def pvcJson = new JsonSlurperClassic().parseText(pvcJsonString)
	def missingPropertyErrorMsg = "Provided persistent volume claim json does not contain '{0}' element."

    def metadata = pvcJson.get("metadata")
    if (metadata == null) {
        error String.format(missingPropertyErrorMsg, "metadata")
    }

    def name = metadata.get("name")
    if (name == null) {
        error String.format(missingPropertyErrorMsg, "metadata.name")
    }

    return name
}
