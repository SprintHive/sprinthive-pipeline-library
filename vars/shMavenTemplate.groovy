#!/usr/bin/groovy

def call(Map parameters = [:], body) {
    def defaultLabel = buildId('maven')
    def label = parameters.get('label', defaultLabel)

    def mavenImage = parameters.get('mavenImage', 'fabric8/maven-builder:2.2.297')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    echo "building using the docker socket"

    podTemplate(label: label, inheritFrom: "${inheritFrom}",
            containers: [
                    [name: 'maven', image: "${mavenImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true,
                     envVars: [
                             [key: 'MAVEN_OPTS', value: '-Duser.home=/root/']]]],
            volumes: [secretVolume(secretName: 'jenkins-maven-settings', mountPath: '/root/.m2'),
                      secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                      secretVolume(secretName: 'jenkins-release-gpg', mountPath: '/home/jenkins/.gnupg'),
                      secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                      secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                      secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git'),
                      hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')],
            envVars: [[key: 'DOCKER_HOST', value: 'unix:/var/run/docker.sock'], [key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/']]
    ) {

        body(

        )
    }

}
