#!/usr/bin/groovy
def call(Map parameters = [:], body) {
    def label = parameters.get('label', buildId('cd'))

    def helmImage = parameters.get('helmImage', 'quay.io/roboll/helmfile:v0.144.0')
    def craneImage = parameters.get('craneImage', 'gcr.io/go-containerregistry/gcrane:debug')
    def sentryImage = parameters.get('sentryImage', 'getsentry/sentry-cli:2.13.0')
    def inheritFrom = parameters.get('inheritFrom', 'default')

    echo "Starting CD node"

    podTemplate(label: label, inheritFrom: "${inheritFrom}", yaml: """
      apiVersion: v1
      kind: Pod
      spec:
        containers:
        - name: crane
          image: ${craneImage}
          command:
          - busybox
          args:
          - cat
          tty: true
          resources:
            requests:
              memory: 128Mi
        - name: helm
          image: ${helmImage}
          env:
          - name: HELM_HOME
            value: /tmp
          command:
          - cat
          tty: true
          resources:
            requests:
              memory: 128Mi
        - name: sentry
          image: ${sentryImage}
          command:
          - cat
          tty: true
      """
    ) {
        node(label) {
            body()
        }
    }
}
