#!/usr/bin/groovy

def call(scmInfo) {
    if (scmInfo == null || scmInfo.GIT_BRANCH == null) {
        currentBuild.result = 'ABORTED'
        error('Git branch is null..')
    }

    def deployStage = ''
    def branch = scmInfo.GIT_BRANCH.substring(scmInfo.GIT_BRANCH.lastIndexOf('/')+1)
    if (branch.equals("dev")) {
        deployStage = "dev"
    } else if (branch.equals('master')) {
        deployStage = 'pre-prod'
    } else {
        deployStage = branch
    }

    return [
        "branch": branch,
        "deployStage": deployStage
    ]
}
