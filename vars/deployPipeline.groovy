#!/usr/bin/groovy

/**
 * @param config.application: The application being deployed
 * @param config.namespace: The kubernetes namespace into which the application should be deployed
 * @param config.imageTag: (Optional) The image tag that should be deployed
 * @param config.chartNameOverride: (Optional) The helm chart to use to deploy the application
 * @param config.chartRepoOverride: (Optional) Override the helm chart repo used to fetch the helm chart
 * @param config.requireReleaseApproval: (Optional) Request approval before releasing into the environment
 * @param config.nextStageName: The environment stage name that will be deployed into
 * @return
 */
def call(config) {

  if (config.requireReleaseApproval == true) {
    stage("${config.nextStageName} Release Approval") {
      timeout(time: 5, unit: 'DAYS') {
        def imageTagMsg = ""
        if (config.imageTag != null) {
          imageTagMsg = " with image tag '${config.imageTag}'"
        }
        input message: "Start ${config.nextStageName} release pipeline for ${config.application}${imageTagMsg}?"
      }
    }
  }

  cdNode {
    stage("Helm Deploy") {
      helmDeploy([
              releaseName      : config.application,
              namespace        : config.namespace,
              chartName        : config.chartNameOverride != null ? config.chartNameOverride : config.application,
              imageTag         : config.imageTag,
              chartRepoOverride: config.chartRepoOverride
      ])
    }
  }
}
