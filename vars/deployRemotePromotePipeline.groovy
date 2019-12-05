#!/usr/bin/groovy

def call(config) {

  if (config.requireReleaseApproval == true) {
    stage("${config.nextStageName} Release Approval") {
      timeout(time: 5, unit: 'DAYS') {
        input message: "Start ${config.nextStageName} release pipeline for ${config.application} with image tag '${params.imageTag}'?"
      }
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

  if (config.requirePromoteApproval == true) {
    stage("Trigger ${config.nextStageName} Pipeline") {
      withCredentials([string(credentialsId: config.remoteTriggerCredentials, variable: 'token')]) {
        httpRequest contentType: 'APPLICATION_JSON', customHeaders: [[maskValue: true, name: 'Authorization', value: "Bearer $token"]], httpMode: 'POST', requestBody: "{\"triggerJob\": \"${config.application}\", \"imageTag\": \"${params.imageTag}\"}", responseHandle: 'NONE', url: "https://${config.remoteHostName}/generic-webhook-trigger/invoke"
      }
    }
  }
}
