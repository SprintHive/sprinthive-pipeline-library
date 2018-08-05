#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('kaniko')
    def label = parameters.get('label', defaultLabel)

    def kanikoImage = parameters.get('kanikoImage', 'quay.io/sprinthive/kaniko:067bb501b8ea')
    def jnlpImage = parameters.get('jnlpImage', 'jenkins/jnlp-slave:latest')
    def helmImage = parameters.get('helmImage', 'lachlanevenson/k8s-helm:v2.9.1')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    echo "Starting pod with kaniko and helm"

	podTemplate(label: label, inheritFrom: "${inheritFrom}", serviceAccount: "helm",
			containers: [
                    containerTemplate(name: 'jnlp', image: "${jnlpImage}", args: '${computer.jnlpmac} ${computer.name}'),
					containerTemplate(name: 'helm', image: "${helmImage}", command: 'cat', ttyEnabled: true),
					containerTemplate(name: 'kaniko', image: "${kanikoImage}", command: 'cat', ttyEnabled: true)]) {
		body()
	}
}
