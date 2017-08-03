#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('nodejs')
    def label = parameters.get('label', defaultLabel)

    def nodejsImage = parameters.get('nodejsImage', 'node:7.8.0-alpine')
    def dockerImage = parameters.get('dockerImage', 'docker:stable')
    def inheritFrom = parameters.get('inheritFrom', 'base')

	podTemplate(label: label, inheritFrom: "${inheritFrom}",
			containers: [
					[name: 'docker', image: "${dockerImage}", command: 'cat', ttyEnabled: true, privileged: true],
					[name: 'nodejs', image: "${nodejsImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true,  workingDir: '/home/jenkins/']],
			volumes: [hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')],
			envVars: [[key: 'DOCKER_HOST', value: 'unix:/var/run/docker.sock'], [key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/']]) {
		body()
	}
}
