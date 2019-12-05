#!/usr/bin/groovy

def call(config) {
  pipeline {
    environment {
      sourceDockerImage  = "eu.gcr.io/${config.sourceGcrProjectId}/${config.application}:${params.imageTag}"
      targetDockerImage  = "eu.gcr.io/${config.targetGcrProjectId}/${config.application}:${params.imageTag}"
    }
    agent none
    stages {
      stage("Release Approval") {
        agent none
        steps {
          script {
              timeout(time:5, unit:'DAYS') {
                input message:"Push and deploy new release image with tag \"${params.imageTag}\"?"
              }
          }
        }
      }
      stage("Push image to release") {
        agent {
          kubernetes {
            defaultContainer 'docker'
            label 'docker'
            yaml """
  apiVersion: v1
  kind: Pod
  spec:
    containers:
    - name: docker
      image: docker:stable
      command:
      - cat
      tty: true
      securityContext:
        privileged: true
      env:
      - name: DOCKER_HOST
        value: unix:///var/run/docker.sock
      volumeMounts:
      - name: docker-socket
        mountPath: /var/run/docker.sock
    volumes:
    - name: docker-socket
      hostPath:
        path: /var/run/docker.sock
        type: Socket
  """
            }
        }
        steps {
          script {
            docker.withRegistry("https://eu.gcr.io", "gcr:${config.gcrCredentialsId}") {
              docker.image(sourceDockerImage).pull()
              sh "docker tag ${sourceDockerImage} ${targetDockerImage}"
              docker.image(targetDockerImage).push()
            }
          }
        }
      }
      stage("Triggers jobs") {
        agent none
        steps {
          script {
            for (deployJob in config.deployJobs) {
              build job: deployJob.path, parameters: [string(name: 'imageTag', value: params.imageTag)], wait: false
            }
          }
        }
      }
    }
  }
}
