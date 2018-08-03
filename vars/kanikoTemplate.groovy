#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('kaniko')
    def label = parameters.get('label', defaultLabel)

    def kanikoImage = parameters.get('kanikoImage', 'gcr.io/kaniko-project/executor:debug-53b5fb41d2e08dc45a491836a8e8b0fff3961f0e')
    def jnlpImage = parameters.get('jnlpImage', 'jenkins/jnlp-slave:alpine')
    def helmImage = parameters.get('helmImage', 'lachlanevenson/k8s-helm:v2.9.1')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    echo "Starting pod with kaniko and helm"

	podTemplate(label: label, inheritFrom: "${inheritFrom}", serviceAccount: "helm",
			containers: [
                    containerTemplate(name: 'jnlp', image: "${jnlpImage}", args: '${computer.jnlpmac} ${computer.name}'),
					containerTemplate(name: 'helm', image: "${helmImage}", command: 'cat', ttyEnabled: true),
					containerTemplate(name: 'kaniko', image: "${kanikoImage}", command: '/busybox/cat', ttyEnabled: true)]) {
		body()
	}
}
