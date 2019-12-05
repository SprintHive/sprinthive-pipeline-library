#!/usr/bin/groovy

def call(config) {
  sourceDockerImage  = "eu.gcr.io/${config.sourceGcrProjectId}/${config.application}:${params.imageTag}"
  targetDockerImage  = "eu.gcr.io/${config.targetGcrProjectId}/${config.application}:${params.imageTag}"

  if (config.requireReleaseApproval == true) {
    stage("${config.nextStageName} Release Approval") {
      timeout(time: 5, unit: 'DAYS') {
        input message: "Start ${config.nextStageName} release pipeline for ${config.application} with image tag '${params.imageTag}'?"
      }
    }
  }

  stage("Push image to ${config.targetGcrProjectId}") {
    docker.withRegistry("https://eu.gcr.io", "gcr:${config.gcrCredentialsId}") {
      docker.image(sourceDockerImage).pull()
      sh "docker tag ${sourceDockerImage} ${targetDockerImage}"
      docker.image(targetDockerImage).push()
    }
  }

  stage("Helm Deploy") {
    helmDeploy([
            releaseName      : config.application,
            namespace        : config.namespace,
            chartName        : config.chart,
            imageTag         : params.imageTag,
            chartRepoOverride: config.chartRepoOverride
    ])
  }
}
