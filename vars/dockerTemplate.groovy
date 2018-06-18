#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('docker')
    def label = parameters.get('label', defaultLabel)

    def dockerImage = parameters.get('dockerImage', 'docker:stable')
    def helmImage = parameters.get('helmImage', 'lachlanevenson/k8s-helm:v2.9.1')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    echo "Starting pod with docker"

	podTemplate(label: label, inheritFrom: "${inheritFrom}", serviceAccount: "helm",
			containers: [
					containerTemplate(name: 'helm', image: "${helmImage}", command: 'cat', ttyEnabled: true),
					containerTemplate(name: 'docker', image: "${dockerImage}", command: 'cat', ttyEnabled: true, privileged: true)],
			volumes: [hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')],
			envVars: [
                envVar(key: 'DOCKER_HOST', value: 'unix:///var/run/docker.sock'),
                envVar(key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/')]
    ) {
		body()
	}
}
