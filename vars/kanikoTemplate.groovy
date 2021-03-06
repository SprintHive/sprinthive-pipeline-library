#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('kaniko')
    def label = parameters.get('label', defaultLabel)

    def skaffoldImage = parameters.get('skaffoldImage', 'gcr.io/k8s-skaffold/skaffold:latest')
    def jnlpImage = parameters.get('jnlpImage', 'jenkins/jnlp-slave:latest')
    def helmImage = parameters.get('helmImage', 'quay.io/roboll/helmfile:v0.138.7')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    echo "Starting pod with kaniko and helm"

	podTemplate(label: label, inheritFrom: "${inheritFrom}", serviceAccount: "helm",
			containers: [
                    containerTemplate(name: 'jnlp', image: "${jnlpImage}", args: '${computer.jnlpmac} ${computer.name}'),
					containerTemplate(name: 'helm', image: "${helmImage}", command: 'cat', ttyEnabled: true),
					containerTemplate(name: 'skaffold', image: "${skaffoldImage}", command: 'cat', ttyEnabled: true)]) {
		body()
	}
}
