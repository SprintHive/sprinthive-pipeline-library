#!/usr/bin/groovy

def call(config) {
    def versionTag
    def shortCommitSha
    def appVersion
    def javaContextDirectory
    def pythonContextDirectory
    def targetNamespace
    def containerImageTaglessJava = "${config.dockerTagBase}/${config.javaComponentName}".toString()
    def containerImageTaglessPython = "${config.dockerTagBase}/${config.pythonComponentName}".toString()
    def arch = config.arch ? config.arch : "amd64"
    config.nodeParameters = config.nodeParameters + ['arch': arch]

    ciNode(config.nodeParameters != null ? config.nodeParameters : [:]) {
        def scmInfo = checkout scm
        def envInfo = environmentInfo(scmInfo)
        shortCommitSha = getNewVersion{}
        targetNamespace = envInfo.deployStage
        if (config.javaSubModuleName != null) {
            javaContextDirectory = "${env.WORKSPACE}/${config.javaSubModuleName}"
        } else {
            javaContextDirectory = env.WORKSPACE
        }
        if (config.pythonSubModuleName != null) {
            pythonContextDirectory = "${env.WORKSPACE}/${config.pythonSubModuleName}"
        } else {
            pythonContextDirectory = env.WORKSPACE
        }
        echo "Current branch is: ${envInfo.branch}"
        echo "Deploy namespace is: ${envInfo.deployStage}"
        echo "Build commit sha is: ${shortCommitSha}"
        echo "Java context directory is: ${javaContextDirectory}"
        echo "Python context directory is: ${pythonContextDirectory}"

        stage('Compile source: Java') {
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

        stage('Unit test: Java') {
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

        if (config.jacocoEnabled) {
            stage('JaCoCo') {
                jacoco exclusionPattern: '**/*Test.class', inclusionPattern: '**/*.class', sourceExclusionPattern: 'generated/**/*.java,generated/**/*.kt', sourceInclusionPattern: '**/*.java,**/*.kt', sourcePattern: '**/src/main/java,**/src/main/kotlin'
            }
        }

        stage('Build Java container image') {
            versionTag = "${appVersion}-${shortCommitSha}-${arch}"
            kanikoBuild(javaContextDirectory, "container-java.tar", "${containerImageTaglessJava}:${versionTag}", scmInfo.GIT_COMMIT)
        }

        if (config.containerScanEnabled != false) {
            stage('Container scan: Java') {
                grypeScan("container-java.tar", javaContextDirectory)
            }
        }

        stage('Push container image: Java') {
            cranePush("${containerImageTaglessJava}:${versionTag}", "container-java.tar")
            cranePush("${containerImageTaglessJava}:${envInfo.branch}", "container-java.tar")
        }

        stage('Build Python container image') {
            versionTag = "${appVersion}-${shortCommitSha}-${arch}"
            kanikoBuild(pythonContextDirectory, "container-python.tar", "${containerImageTaglessPython}:${versionTag}", scmInfo.GIT_COMMIT)
        }

        if (config.containerScanEnabled != false) {
            stage('Container scan: Python') {
                grypeScan("container-python.tar", pythonContextDirectory)
            }
        }

        stage('Push container image: Python') {
            cranePush("${containerImageTaglessPython}:${versionTag}", "container-python.tar")
            cranePush("${containerImageTaglessPython}:${envInfo.branch}", "container-python.tar")
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
}
