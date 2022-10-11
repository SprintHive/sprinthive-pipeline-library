#!/usr/bin/groovy

def call(config) {
  pipeline {
    environment {
      sourceContainerImage  = "eu.gcr.io/${config.sourceGcrProjectId}/${config.application}:${params.imageTag}"
      targetContainerImage  = "eu.gcr.io/${config.targetGcrProjectId}/${config.application}:${params.imageTag}"
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
            defaultContainer 'crane'
            label 'crane'
            yaml """
  apiVersion: v1
  kind: Pod
  spec:
    containers:
    - name: crane
      image: gcr.io/go-containerregistry/gcrane:debug
      command:
      - busybox
      args:
      - cat
      tty: true
      resources:
        requests:
          memory: 128Mi
  """
            }
        }
        steps {
          script {
            cranePull(sourceContainerImage, "container.tar")
            cranePush(targetContainerImage, "container.tar")
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
