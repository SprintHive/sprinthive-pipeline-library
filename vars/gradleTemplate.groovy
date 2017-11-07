#!/usr/bin/groovy

def call(Map parameters = [:], body) {
    def defaultLabel = buildId('gradle')
    def label = parameters.get('label', defaultLabel)

//    def gradleImage = parameters.get('gradleImage', 'gradle:4.2-jdk8')
    def gradleImage = parameters.get('gradleImage', 'base-gradle:v1')
    def dockerImage = parameters.get('dockerImage', 'docker:stable')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    echo "Deploying gradle:4.2-jdk8 and Docker image"

    podTemplate(label: label, inheritFrom: "${inheritFrom}",
            containers: [
                [name: 'gradle', image: "${gradleImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true],
                [name: 'docker', image: "${dockerImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true]],
            volumes: [configMapVolume(configMapName: 'jenkins-maven-settings', mountPath: '/root/.m2'),
                      hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')],
            envVars: [[key: 'DOCKER_HOST', value: 'unix:/var/run/docker.sock'], [key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/']]
    ) {
        body()
    }
}
