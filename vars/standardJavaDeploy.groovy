#!/usr/bin/groovy

def call(config) {
    def versionTag
    def shortCommitSha
    def appVersion
    def containerImageTagless
    def containerImage
    def contextDirectory
    def targetNamespace

    javaNode(config.nodeParameters != null ? config.nodeParameters : [:]) {
        def scmInfo = checkout scm
        def envInfo = environmentInfo(scmInfo)
        shortCommitSha = getNewVersion{}
        targetNamespace = envInfo.deployStage
        echo "Current branch is: ${envInfo.branch}"
        echo "Deploy namespace is: ${envInfo.deployStage}"
        echo "Build commit sha is: ${shortCommitSha}"

        stage('Compile source') {
            container(name: config.buildContainerOverride != null ? config.buildContainerOverride : 'gradle') {
                appVersion = javaAppVersion()
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
                def testCommand
                if (config.buildCommandOverride != null) {
                    testCommand = config.buildCommandOverride
                } else {
                    testCommand = "gradle test"
                    if (config.jacocoEnabled) {
                        testCommand += " jacocoTestReport"
                    }
                }
                sh """
                    export ENV_STAGE=${envInfo.deployStage}
                    export ENV_BRANCH=${envInfo.branch}
                    ${testCommand}
                """
            }
        }

        if (config.sonarQubeEnabled) {
            stage('SonarQube') {
                container(name: config.buildContainerOverride != null ? config.buildContainerOverride : 'gradle') {
                    withSonarQubeEnv() {
                        sh 'gradle sonarqube'
                    }
                }
            }
        }

        if (config.jacocoEnabled) {
            stage('JaCoCo') {
                jacoco exclusionPattern: '**/*Test.class', inclusionPattern: '**/*.class', sourceExclusionPattern: 'generated/**/*.java,generated/**/*.kt', sourceInclusionPattern: '**/*.java,**/*.kt', sourcePattern: '**/src/main/java,**/src/main/kotlin'
            }
        }

        stage('Build container image') {
            container('kaniko') {
                versionTag = "${appVersion}-${shortCommitSha}"
                containerImageTagless = "${config.dockerTagBase}/${config.componentName}"
                containerImage = "${containerImageTagless}:${versionTag}"

                if (config.subModuleName != null) {
                    contextDirectory = "${env.WORKSPACE}/${config.subModuleName}"
                } else {
                    contextDirectory = env.WORKSPACE
                }
                sh "/kaniko/executor --context $contextDirectory --tar-path container.tar --destination ${containerImageTagless}:${versionTag} --build-arg SOURCE_VERSION=${scmInfo.GIT_COMMIT} --log-format text --no-push"
            }
        }

        if (config.containerScanEnabled != false) {
            stage('Container scan') {
                grypeScan("container.tar")
            }
        }

        stage('Push container image') {
            container('kaniko') {
                sh "/kaniko/executor --context $contextDirectory --destination ${containerImageTagless}:${versionTag} --build-arg SOURCE_VERSION=${scmInfo.GIT_COMMIT} --log-format text"
                sh "/kaniko/executor --context $contextDirectory --destination ${containerImageTagless}:${envInfo.branch} --build-arg SOURCE_VERSION=${scmInfo.GIT_COMMIT} --log-format text"
            }
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

            build job: env.POST_BUILD_TRIGGER_JOB, parameters: [string(name: 'imageTag', value: versionTag), string(name: 'changeLog', value: changeLog())], wait: false
        }
    }

    if (config.postDeploySteps != null) {
        config.postDeploySteps ([
            releaseName:  config.releaseName,
            namespace:  targetNamespace,
            chartName:  config.chartNameOverride != null ? config.chartNameOverride : config.componentName,
            imageTag:  versionTag,
            overrides: config.chartOverrides,
            chartRepoOverride: config.chartRepoOverride
        ])
    }
}
