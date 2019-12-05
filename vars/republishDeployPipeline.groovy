#!/usr/bin/groovy

/**
 * @param config.application: The application being deployed
 * @param config.namespace: The kubernetes namespace into which the application should be deployed
 * @param config.gcrCredentialsId: The credentials id for a GCR Service Account that can read from the source GCR \
 *        repository and publish to the target GCR repository
 * @param config.sourceGcrProjectId: The GCP project id of the project with the source GCR repository
 * @param config.targetGcrProjectId: The GCP project id of the project with the target GCR repository
 * @param config.chartNameOverride: (Optional) The helm chart to use to deploy the application
 * @param config.chartRepoOverride: (Optional) Override the helm chart repo used to fetch the helm chart
 * @param config.requireReleaseApproval: (Optional) Request approval before releasing into the environment
 * @param config.nextStageName: The environment stage name that will be deployed into
 * @return
 */
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

  cdNode {
    stage("Push image to ${config.targetGcrProjectId}") {
      container("docker") {
        docker.withRegistry("https://eu.gcr.io", "gcr:${config.gcrCredentialsId}") {
          docker.image(sourceDockerImage).pull()
          sh "docker tag ${sourceDockerImage} ${targetDockerImage}"
          docker.image(targetDockerImage).push()
        }
      }
    }

    stage("Helm Deploy") {
      helmDeploy([
              releaseName      : config.application,
              namespace        : config.namespace,
              chartName        : config.chartNameOverride != null ? config.chartNameOverride : config.application,
              imageTag         : params.imageTag,
              chartRepoOverride: config.chartRepoOverride
      ])
    }
  }
}
