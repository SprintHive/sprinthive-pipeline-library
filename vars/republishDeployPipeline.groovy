#!/usr/bin/groovy

/**
 * @param config.application: The application being deployed
 * @param config.integrationTest: (Optional) The integration test configuration (fields: enabled, repository, branch, envVars, namespace). If set and enabled, this will run the integration tests prior to deploying past test environments.
 * @param config.namespacesTest: (Optional) if skipDeploy is true. The kubernetes test namespaces into which the application should be deployed without release approval.
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
                  releaseName          : config.application,
                  namespace            : namespaceTest,
                  imageTag             : params.imageTag,
                  helmfileRepoOverride : config.helmfileRepoOverride
          ])
        }
      }
    }
  }

  if (config.integrationTest != null && config.integrationTest.enabled) {
    integNamespace = config.integrationTest.namespace != null ? config.integrationTest.namespace : 'integ-test'
    stage("Integration test") {
      cdNode {
        checkout([
            $class: 'GitSCM',
            branches: [[name: config.integrationTest.branch]],
            extensions: [[$class: 'GitLFSPull']],
            userRemoteConfigs: [[credentialsId: 'bitbucket', url: "https://bitbucket.org/sprinthive/${config.integrationTest.repository}.git"]]
        ])
        podTemplateYaml = readFile("jenkins/integration-test-pod.yaml")
        podLabel = "integ-test-${config.application}"
        podTemplate(yaml: podTemplateYaml, label: podLabel, namespace: integNamespace) {
          node(podLabel) {
            checkout([
                    $class: 'GitSCM',
                    branches: [[name: config.integrationTest.branch]],
                    extensions: [[$class: 'GitLFSPull']],
                    userRemoteConfigs: [[credentialsId: 'bitbucket', url: "https://bitbucket.org/sprinthive/${config.integrationTest.repository}.git"]]
            ])
            container('test') {
              exports = []
              if (config.integrationTest.envVars != null) {
                config.integrationTest.envVars.each { envVar ->
                  exports.add("${envVar.key}=${envVar.value}")
                }
              }
              sh "${exports.join(" ")} ./jenkins/integrationTest.sh"
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
    }
  }
}
