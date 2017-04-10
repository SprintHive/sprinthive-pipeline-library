#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()

    def defaultLabel = buildId('nodejs')
    def label = parameters.get('label', defaultLabel)

    def nodejsImage = parameters.get('nodejsImage', 'node:7.8.0-alpine')
    def clientsImage = parameters.get('clientsImage', 'fabric8/builder-clients:latest')
    def inheritFrom = parameters.get('inheritFrom', 'base')

	podTemplate(label: label, inheritFrom: "${inheritFrom}",
			containers: [
					[name: 'client', image: "${clientsImage}", command: 'cat', ttyEnabled: true, privileged: true],
					[name: 'nodejs', image: "${nodejsImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true,  workingDir: '/home/jenkins/']],
			volumes: [hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')],
			envVars: [[key: 'DOCKER_HOST', value: 'unix:/var/run/docker.sock'], [key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/']]) {
		body()
	}
}
