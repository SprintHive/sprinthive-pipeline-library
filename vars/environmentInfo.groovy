#!/usr/bin/groovy

def call(scmInfo) {
    if (scmInfo == null || scmInfo.GIT_BRANCH == null) {
        currentBuild.result = 'ABORTED'
        error('Git branch is null..')
    }

    def deployStage = null
    echo "Local branch is: ${scmInfo.GIT_LOCAL_BRANCH}"
    def rawBranch = scmInfo.GIT_BRANCH.substring(scmInfo.GIT_BRANCH.lastIndexOf('/')+1)
    def branchParts = rawBranch.split('-')
    def inferredBranch = null
    def multivariateTest = null
    // A multivariate test has a branch with naming convention <originbranch-mvt-mvtname>. E.g. dev-mvt-lowagereq
    if (branchParts.length > 2 && branchParts[1] == "mvt") {
        inferredBranch = branchParts[0]
        multivariateTest = branchParts[2]
    } else {
        inferredBranch = rawBranch
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
