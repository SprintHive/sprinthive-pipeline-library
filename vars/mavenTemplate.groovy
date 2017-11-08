#!/usr/bin/groovy

def call(Map parameters = [:], body) {
    def defaultLabel = buildId('maven')
    def label = parameters.get('label', defaultLabel)

    def mavenImage = parameters.get('mavenImage', 'maven:3.5-jdk-8-alpine')
    def dockerImage = parameters.get('dockerImage', 'docker:stable')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    echo "Starting pod with maven and docker"

    podTemplate(label: label, inheritFrom: "${inheritFrom}",
            containers: [
                [name: 'maven', image: "${mavenImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true, envVars: [ [key: 'MAVEN_OPTS', value: '-Duser.home=/root/'] ]],
                [name: 'docker', image: "${dockerImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true]],
            volumes: [configMapVolume(configMapName: 'jenkins-maven-settings', mountPath: '/root/.m2'),
                      hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')],
            envVars: [[key: 'DOCKER_HOST', value: 'unix:/var/run/docker.sock'], [key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/']]
    ) {

        body(

        )
    }

}
