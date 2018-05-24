#!/usr/bin/groovy

def call(scmInfo) {
    if (scmInfo == null || scmInfo.GIT_BRANCH == null) {
        currentBuild.result = 'ABORTED'
        error('Git branch is null..')
    }

    def namespace = ''
    def deployStage = ''
    def branch = scmInfo.GIT_BRANCH.substring(scmInfo.GIT_BRANCH.lastIndexOf('/')+1)
    if (branch.equals("dev")) {
        namespace = "dev"
        deployStage = 'Development'
    } else if (branch.equals('master')) {
        namespace = 'pre-prod'
        deployStage = 'Pre-Production'
    } else {
        namespace = branch
        deployStage = "${branch} Stack".capitalize()
    }

    return [
        "branch": branch,
        "namespace": namespace,
        "deployStage": deployStage
    ]
}
