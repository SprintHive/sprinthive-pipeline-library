#!/usr/bin/groovy

def call(Map parameters = [:], body) {
    def defaultLabel = buildId('gradle')
    def label = parameters.get('label', defaultLabel)

    def gradleImage = parameters.get('gradleImage', 'bitstack701/base-gradle:v3.0.2')
    def dockerImage = parameters.get('dockerImage', 'docker:19.03.8')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    echo "Starting pod with gradle and docker"

    podTemplate(label: label, inheritFrom: "${inheritFrom}",
            containers: [
                containerTemplate(name: 'gradle', image: "${gradleImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true),
                containerTemplate(name: 'docker', image: "${dockerImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true)],
            volumes: [hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')],
            envVars: [
                envVar(key: 'DOCKER_HOST', value: 'unix:///var/run/docker.sock'),
                envVar(key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/')]
    ) {
        body()
    }
}
