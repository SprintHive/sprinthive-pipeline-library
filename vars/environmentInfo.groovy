#!/usr/bin/groovy

def call(scmInfo) {
    if (scmInfo == null || scmInfo.GIT_BRANCH == null) {
        currentBuild.result = 'ABORTED'
        error('Git branch is null..')
    }

    def deployStage
    def branch = scmInfo.GIT_BRANCH.substring(scmInfo.GIT_BRANCH.lastIndexOf('/')+1)

    if (env.NAMESPACE_OVERRIDE != null) {
        deployStage = env.NAMESPACE_OVERRIDE
    }
    else if (branch.equals("dev")) {
        deployStage = "dev"
    } else if (branch.equals('master') || branch.equals('main')) {
        deployStage = 'pre-prod'
    } else {
        deployStage = branch
    }

    return [
        "branch": branch,
        "deployStage": deployStage
    ]
}
