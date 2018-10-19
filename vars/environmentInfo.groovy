#!/usr/bin/groovy

def call(scmInfo) {
    if (scmInfo == null || scmInfo.GIT_BRANCH == null) {
        currentBuild.result = 'ABORTED'
        error('Git branch is null..')
    }

    def deployStage = null
    def inferredBranch = null
    def multivariateTest = null
    if (scmInfo.GIT_LOCAL_BRANCH) {
        inferredBranch = scmInfo.GIT_LOCAL_BRANCH
        echo "Using local branch: ${inferredBranch}"
    } else {

        // TODO: incorporate multivariate tests in local branch config?

        def rawBranch = scmInfo.GIT_BRANCH.substring(scmInfo.GIT_BRANCH.lastIndexOf('/')+1)
        def branchParts = rawBranch.split('-')
        // A multivariate test has a branch with naming convention <originbranch-mvt-mvtname>. E.g. dev-mvt-lowagereq
        if (branchParts.length > 2 && branchParts[1] == "mvt") {
            inferredBranch = branchParts[0]
            multivariateTest = branchParts[2]
        } else {
            inferredBranch = rawBranch
        }
    }

    if (inferredBranch.equals("dev")) {
        deployStage = "dev"
    } else if (inferredBranch.equals('master')) {
        deployStage = 'pre-prod'
    } else {
        deployStage = inferredBranch
    }



    return [
        "branch": inferredBranch,
        "deployStage": deployStage,
        "multivariateTest": multivariateTest
    ]
}
