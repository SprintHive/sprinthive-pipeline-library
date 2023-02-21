#!/usr/bin/groovy

def call(config) {
    container('sentry') {
        environment {
            SENTRY_AUTH_TOKEN = credentials('sentry-auth-token')
            SENTRY_ORG = 'sprinthive-7h'
            SENTRY_PROJECT = 'sprinthive-console'
            SENTRY_ENVIRONMENT = 'production'
        }
        steps {
            sh '''
                    export SENTRY_RELEASE=$BUILD_NUMBER
                    sentry-cli releases new -p $SENTRY_PROJECT $SENTRY_RELEASE
                    sentry-cli releases set-commits $SENTRY_RELEASE --auto
                    sentry-cli releases finalize $SENTRY_RELEASE
                    sentry-cli releases deploys $SENTRY_RELEASE new -e $SENTRY_ENVIRONMENT
                '''
        }
    }
}
