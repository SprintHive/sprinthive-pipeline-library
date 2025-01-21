#!/usr/bin/groovy

def call(config) {
    def versionTag
    def shortCommitSha
    def appVersion
    def containerImage
    def contextDirectory
    def targetNamespace
    def containerImageTagless = "${config.dockerTagBase}/${config.componentName}".toString()
    def arch = config.arch ?: "amd64" 
    nodeParameters = config.nodeParameters ?: [:]  
    nodeParameters += [arch: arch]

    ciNode(nodeParameters) {
        def scmInfo = checkout scm
        def envInfo = environmentInfo(scmInfo)
        shortCommitSha = getNewVersion{}
        targetNamespace = envInfo.deployStage
        if (config.subModuleName != null) {
            contextDirectory = "${env.WORKSPACE}/${config.subModuleName}"
        } else {
            contextDirectory = env.WORKSPACE
        }
        echo "Current branch is: ${envInfo.branch}"
        echo "Deploy namespace is: ${envInfo.deployStage}"
        echo "Build commit sha is: ${shortCommitSha}"
        echo "Context directory is: ${contextDirectory}"

        stage('Compile source') {
            container(name: config.buildContainerOverride != null ? config.buildContainerOverride : 'gradle') {
                appVersion = javaAppVersion(config.subModuleName)
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
            versionTag = "${appVersion}-${shortCommitSha}-${arch}"
            kanikoBuild(contextDirectory, "container.tar", "${containerImageTagless}:${versionTag}", scmInfo.GIT_COMMIT)
        }

        if (config.containerScanEnabled != false) {
            stage('Container scan') {
                grypeScan("container.tar", contextDirectory)
            }
        }

        stage('Push container image') {
            cranePush("${containerImageTagless}:${versionTag}", "container.tar")
            cranePush("${containerImageTagless}:${envInfo.branch}", "container.tar")
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
