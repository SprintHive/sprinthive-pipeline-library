#!/usr/bin/groovy

/**
 * @param config.application: The application being deployed
 * @param config.integrationTest: (Optional) The integration test configuration (fields: enabled, repository, branch). If set and enabled, this will run the integration tests prior to deploying past test environments.
 * @param config.namespacesTest: (Optional) if skipDeploy is true. The kubernetes test namespaces into which the application should be deployed without release approval.
 * @param config.namespacesPreProd: (Optional) if skipDeploy is true. The kubernetes pre-prod namespaces into which the application should be deployed prior to full production rollout.
 * @param config.namespacesProd: (Optional) if skipDeploy is true. The kubernetes prod namespace into which the application should be deployed only with manual approval.
 * @param config.gcrCredentialsId: The credentials id for a GCR Service Account that can read from the source GCR \
 *        repository and publish to the target GCR repository
 * @param config.sourceGcrProjectId: The GCP project id of the project with the source GCR repository
 * @param config.targetGcrProjectId: The GCP project id of the project with the target GCR repository
 * @param config.chartNameOverride: (Optional) The helm chart to use to deploy the application
 * @param config.chartRepoOverride: (Optional) Override the helm chart repo used to fetch the helm chart
 * @param config.requireReleaseApproval: (Optional) Request approval before releasing into the environment
 * @param config.nextStageName: The environment stage name that will be deployed into
 * @param config.skipDeploy: (Optional) Deployment will skip Helm Deploy stage
 * @return
 */
def call(config) {
  sourceDockerImage  = "eu.gcr.io/${config.sourceGcrProjectId}/${config.application}:${params.imageTag}"
  targetDockerImage  = "eu.gcr.io/${config.targetGcrProjectId}/${config.application}:${params.imageTag}"

  if (params.changeLog != null && !params.changeLog.isEmpty()) {
    println("Change log:")
    println(params.changeLog)
  }

  if (config.namespacesTest != null && config.namespacesTest.size() > 0) {
    cdNode {
      for (namespaceTest in config.namespacesTest) {
        stage("Helm Deploy: $namespaceTest") {
          helmDeploy([
                  releaseName: config.application,
                  namespace  : namespaceTest,
                  chartName  : config.chartNameOverride != null ? config.chartNameOverride : config.application,
                  imageTag   : params.imageTag
          ])
        }
      }
    }
  }

  if (config.integrationTest != null && config.integrationTest.enabled) {
    stage("Integration test") {
      cdNode {
        git(
          url: "https://bitbucket.org/sprinthive/${config.integrationTest.repository}.git",
          branch: config.integrationTest.branch,
          credentialsId: "bitbucket",
        )
        podTemplateYaml = readFile("jenkins/integration-test-pod.yaml")
        podLabel = "integ-test-${config.application}"
        podTemplate(yaml: podTemplateYaml, label: podLabel, namespace: "integ-test") {
          node(podLabel) {
            git(
              url: "https://bitbucket.org/sprinthive/${config.integrationTest.repository}.git",
              branch: config.integrationTest.branch,
              credentialsId: "bitbucket",
            )
            container('test') {
              sh "./integrationTest.sh"
            }
          }
        }
      }
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
    stage("Push image to ${config.targetGcrProjectId}") {
      container("docker") {
        docker.withRegistry("https://eu.gcr.io", "gcr:${config.gcrCredentialsId}") {
          docker.image(sourceDockerImage).pull()
          sh "docker tag ${sourceDockerImage} ${targetDockerImage}"
          docker.image(targetDockerImage).push()
        }
      }
    }

    if (config.skipDeploy != true) {
      for (namespacePreProd in config.namespacesPreProd) {
        stage("Helm Deploy: $namespacePreProd") {
          helmDeploy([
                  releaseName      : config.application,
                  namespace        : namespacePreProd,
                  chartName        : config.chartNameOverride != null ? config.chartNameOverride : config.application,
                  imageTag         : params.imageTag
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
                    releaseName      : config.application,
                    namespace        : namespaceProd,
                    chartName        : config.chartNameOverride != null ? config.chartNameOverride : config.application,
                    imageTag         : params.imageTag
            ])
          }
        }
      }
    }
  }
}
