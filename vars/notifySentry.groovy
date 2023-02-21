#!/usr/bin/groovy

def call(config) {
    def sentryOrg = "sprinthive-7h"
    def sentryProject = "sprinthive-console"
    def sentryEnvironment = "production"
    def releaseName = env.BUILD_NUMBER
    container('sentry') {
        sh """
                export SENTRY_AUTH_TOKEN=${credentials('sentry-auth-token')}
                export SENTRY_ORG="${sentryOrg}"
                export SENTRY_PROJECT="${sentryProject}"
                export SENTRY_ENVIRONMENT="${sentryEnvironment}"
                export SENTRY_RELEASE="${releaseName}"
                sentry-cli releases new -p $SENTRY_PROJECT $SENTRY_RELEASE
                sentry-cli releases set-commits $SENTRY_RELEASE --auto
                sentry-cli releases finalize $SENTRY_RELEASE
                sentry-cli releases deploys $SENTRY_RELEASE new -e $SENTRY_ENVIRONMENT
           """
    }
}
