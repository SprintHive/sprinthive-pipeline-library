#!/usr/bin/groovy

/**
 * @param config.application: The application being deployed
 * @param config.integrationTest: (Optional) The integration test configuration (fields: runTest, repository, branch, envVars, deploy, chart).
 * @param config.namespacesPreProd: (Optional) if skipDeploy is true. The kubernetes pre-prod namespaces into which the application should be deployed prior to full production rollout.
 * @param config.namespacesProd: (Optional) if skipDeploy is true. The kubernetes prod namespace into which the application should be deployed only with manual approval.
 * @param config.gcrCredentialsId: The credentials id for a GCR Service Account that can read from the source GCR \
 *        repository and publish to the target GCR repository
 * @param config.sourceGcrProjectId: The GCP project id of the project with the source GCR repository
 * @param config.targetGcrProjectId: The GCP project id of the project with the target GCR repository
 * @param config.helmfileRepoOverride: The git repository containing the application helmfiles
 * @param config.requireReleaseApproval: (Optional) Request approval before releasing into the environment
 * @param config.nextStageName: The environment stage name that will be deployed into
 * @param config.skipDeploy: (Optional) Deployment will skip Helm Deploy stage
 * @return
 */


class containerImage {
    String name
    String sourceRepoURL
    String sourceRepoName
    String destinationRepoURL
    String destinationRepoName
}

def call(config) {
  def imagesToPublish = []
  imagesToPublish.add(config.application)
  imagesToPublish.addAll(config.additionalImageNames ?: [])

  // Note: We expect all images to have the same tags, this will likely have to be expended on in the future
  def containerImages = imagesToPublish.collect { image ->
    new containerImage(
      name: image, 
      sourceRepoURL: "eu.gcr.io/${config.sourceGcrProjectId}/${image}:${params.imageTag}",
      sourceRepoName: config.sourceGcrProjectId,
      destinationRepoURL : "eu.gcr.io/${config.targetGcrProjectId}/${image}:${params.imageTag}",
      destinationRepoName: config.targetGcrProjectId
    )
  }

  if (params.changeLog != null && !params.changeLog.isEmpty()) {
    println("Change log:")
    println(params.changeLog)
  }

  if (config.integrationTest != null) {
    if (config.integrationTest.deploy) {
        cdNode {
            stage("Helm Deploy: integ-test") {
              def chartEnv = config.integrationTest.chart
              if (!chartEnv) {
                chartEnv = "integ-test"
              }
              withEnv(["CHART_ENVIRONMENT=${chartEnv}"]) {
                helmDeploy([
                        releaseName          : config.application,
                        namespace            : "integ-test",
                        imageTag             : params.imageTag,
                        helmfileRepoOverride : config.helmfileRepoOverride
                ])
              }
            }
        }
    }

    if (config.integrationTest.runTest) {
        config.integrationTest.namespace = "integ-test"
        integTest(config.integrationTest)
    }
  }

  if (config.requireReleaseApproval == true) {
    stage("${config.nextStageName} Release Approval") {
      timeout(time: 5, unit: 'DAYS') {
        input message: "Start ${config.nextStageName} release pipeline for ${config.application} with image tag '${params.imageTag}' [source QA job ${params.sourceJobId}]?"
      }
    }
  }

  cdNode {
    
    for (image in containerImages) {
      stage("Promote Docker Image: ${image.name} to ${image.destinationRepoName}") {
        cranePull(image.sourceRepoURL, "container.tar")
        cranePush(image.destinationRepoURL, "container.tar")
      }
    }

    if (config.skipDeploy != true) {
      for (namespacePreProd in config.namespacesPreProd) {
        stage("Helm Deploy: $namespacePreProd") {
          helmDeploy([
                  releaseName          : config.application,
                  namespace            : namespacePreProd,
                  imageTag             : params.imageTag,
                  helmfileRepoOverride : config.helmfileRepoOverride
          ])
        }
      }

      if (config.namespacesProd.size() > 0) {
        stage("Prod deploy approval") {
          timeout(time: 5, unit: 'DAYS') {
            input message: "Deploy to Prod Namespaces?"
          }
        }
        
        for (namespaceProd in config.namespacesProd) {
          stage("Helm Deploy: $namespaceProd") {
            helmDeploy([
                    releaseName          : config.application,
                    namespace            : namespaceProd,
                    imageTag             : params.imageTag,
                    helmfileRepoOverride : config.helmfileRepoOverride
            ])
          }
        }
      }

      if (config.publishDeployMetric == true) {
        publishDeployMetric(config.application, config.namespacesProd + config.namespacesPreProd)
      }
    }
  }
}
