#!/usr/bin/groovy

/**
 * @param config.application: The application being deployed
 * @param config.namespace: The kubernetes namespace into which the application should be deployed
 * @param config.imageTag: (Optional) The image tag that should be deployed
 * @param config.helmfileRepoOverride: The git repository containing the application helmfiles
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
              releaseName          : config.application,
              namespace            : config.namespace,
              imageTag             : config.imageTag,
              helmfileRepoOverride : config.helmfileRepoOverride
      ])
    }

    if (config.publishDeployMetric == true) {
      publishDeployMetric(config.application, [config.namespace])
    }
  }
}
