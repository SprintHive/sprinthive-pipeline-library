#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('nodejs')
    def label = parameters.get('label', defaultLabel)

    def nodejsImage = parameters.get('nodejsImage', 'mhart/alpine-node:8')
    def dockerImage = parameters.get('dockerImage', 'docker:stable')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    echo "Starting pod with node and docker"

	podTemplate(label: label, inheritFrom: "${inheritFrom}",
			containers: [
					containerTemplate(name: 'docker', image: "${dockerImage}", command: 'cat', ttyEnabled: true, privileged: true),
					containerTemplate(name: 'nodejs', image: "${nodejsImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true,  workingDir: '/home/jenkins/')],
			volumes: [hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')],
			envVars: [
                envVar(key: 'DOCKER_HOST', value: 'unix:///var/run/docker.sock'),
                envVar(key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/')]
    ) {
		body()
	}
}
