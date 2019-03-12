#!/usr/bin/groovy

def call(config) {
    def versionTag = ''
    def dockerImage
    def targetNamespace

    javaNode {
        def scmInfo = checkout scm
        def envInfo = environmentInfo(scmInfo)
        targetNamespace = envInfo.deployStage
        echo "Current branch is: ${envInfo.branch}"
        echo "Deploy namespace is: ${envInfo.deployStage}"
        if (envInfo.multivariateTest != null) {
            echo "Multivariate test: ${envInfo.multivariateTest}"
        }

        stage('Compile source') {
            versionTag = getNewVersion{}
            dockerImage = "${config.dockerTagBase}/${config.componentName}:${versionTag}"

            container(name: config.buildContainerOverride != null ? config.buildContainerOverride : 'gradle') {
                def buildCommand = config.buildCommandOverride != null ? config.buildCommandOverride : "gradle bootJar"
                sh """
                    export ENV_STAGE=${envInfo.deployStage}
                    export ENV_BRANCH=${envInfo.branch}
                    ${buildCommand}
                """
            }
        }

        stage('Unit test') {
            container(name: config.buildContainerOverride != null ? config.buildContainerOverride : 'gradle') {
                def testCommand = config.buildCommandOverride != null ? config.buildCommandOverride : "gradle test"
                sh """
                    export ENV_STAGE=${envInfo.deployStage}
                    export ENV_BRANCH=${envInfo.branch}
                    ${testCommand}
                """
            }
        }

        stage('Build docker image') {
            container('docker') {
                sh "docker build -t ${dockerImage} --build-arg SOURCE_VERSION=${scmInfo.GIT_COMMIT} ."
            }
        }

        if (config.containerScanEnabled != false) {
            stage('Container scan') {
                container('clairscanner') {
                    sh '/clair -w /config/whitelist.yaml -c http://clair.infra:6060 --ip $POD_IP ' + dockerImage
                }
            }
        }

        stage('Push docker image') {
            container('docker') {
                docker.withRegistry(config.registryUrl, config.registryCredentialsId) {
                    docker.image(dockerImage).push()
                    docker.image(dockerImage).push(envInfo.branch)
                }
            }
        }

        stage("Rollout to ${envInfo.deployStage.capitalize()}") {
            helmDeploy([
                releaseName:  config.releaseName,
                namespace:  envInfo.deployStage,
                multivariateTest: envInfo.multivariateTest,
                chartName:  config.chartNameOverride != null ? config.chartNameOverride : config.componentName,
                imageTag:  versionTag,
                overrides: config.chartOverrides,
                chartRepoOverride: config.chartRepoOverride
            ])
        }
    }

    if (config.integrationTestPod != null) {
        def label = "java-test-${UUID.randomUUID().toString()}"
        stage("Integration test") {
              config.integrationTestPod(label, targetNamespace) {
                node(label) {
                    checkout scm
                    container('gradle') {
                        if (env.INTEGRATION_TEST_SPRING_PROFILES == null) {
                            sh "gradle -i integrationTest"
                        } else {
                            sh "gradle -i integrationTest -Pprofiles=${env.INTEGRATION_TEST_SPRING_PROFILES}"
                        }
                    }
                }
            }
        }
    }

    if (env.POST_BUILD_TRIGGER_JOB != null) {
        stage("Trigger ${env.POST_BUILD_TRIGGER_JOB}") {

            build job: env.POST_BUILD_TRIGGER_JOB, parameters: [string(name: 'IMAGE_TAG', value: versionTag), string(name: 'CHANGE_LOG', value: changeLog())], wait: false
        }
    }
}
