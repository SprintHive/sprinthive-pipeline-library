#!/usr/bin/groovy

def call(config) {
    def sentryOrg = "sprinthive-7h"
    def sentryProject = "sprinthive-console"
    def sentryEnvironment = "production"
    def releaseName = env.BUILD_NUMBER
    container('sentry') {
        withCredentials([string(credentialsId: "sentry-auth-token", variable: 'SENTRY_AUTH_TOKEN')]) {
            sh """
                export SENTRY_ORG="${sentryOrg}"
                sentry-cli releases new -p "${sentryProject}" "${releaseName}"
                sentry-cli releases set-commits "${releaseName}" --auto
                sentry-cli releases finalize "${releaseName}"
                sentry-cli releases deploys "${releaseName}" new -e "${sentryEnvironment}"
           """
        }
    }
}
