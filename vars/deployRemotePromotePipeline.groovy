#!/usr/bin/groovy

/**
 * @param config.application: The application being deployed
 * @param config.namespace: The kubernetes namespace into which the application should be deployed
 * @param config.chartNameOverride: (Optional) The helm chart to use to deploy the application
 * @param config.chartRepoOverride: (Optional) Override the helm chart repo used to fetch the helm chart
 * @param config.requireReleaseApproval: (Optional) Request approval before releasing into the environment
 * @param config.nextStageName: The environment stage name that will be deployed into
 * @param config.promotionStageName: The environment stage name that will be promoted to after the next stage
 * @param config.requirePromotionApproval: (Optional) Request approval before promoting to the promotion stage
 * @param config.remoteTriggerCredentials: The Jenkins credentials that contain the authentication token for the remote pipeline trigger
 * @param config.remoteHostName: The hostname of the remote Jenkins instance (e.g. prod.jenkins.acme.com)
 * @return
 */
def call(config) {

  if (config.requireReleaseApproval == true) {
    stage("${config.nextStageName} Release Approval") {
      timeout(time: 5, unit: 'DAYS') {
        input message: "Start ${config.nextStageName} release pipeline for ${config.application} with image tag '${params.imageTag}'?"
      }
    }
  }

  cdNode {
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

  if (config.requirePromotionApproval == true) {
    stage("${config.nextStageName} Release Approval") {
      timeout(time: 5, unit: 'DAYS') {
        input message: "Start ${config.nextStageName} release pipeline for ${config.application} with image tag '${params.imageTag}'?"
      }
    }
  }

  stage("Trigger ${config.promotionStageName} Pipeline") {
    withCredentials([string(credentialsId: config.remoteTriggerCredentials, variable: 'token')]) {
      httpRequest contentType: 'APPLICATION_JSON', customHeaders: [[maskValue: true, name: 'Authorization', value: "Bearer $token"]], httpMode: 'POST', requestBody: "{\"triggerJob\": \"${config.application}\", \"imageTag\": \"${params.imageTag}\"}", responseHandle: 'NONE', url: "https://${config.remoteHostName}/generic-webhook-trigger/invoke"
    }
  }
}
