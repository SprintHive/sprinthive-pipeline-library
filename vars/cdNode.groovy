#!/usr/bin/groovy
def call(Map parameters = [:], body) {
    def label = parameters.get('label', buildId('cd'))

    def helmImage = parameters.get('helmImage', 'alpine/helm:3.12.3')
    def craneImage = parameters.get('craneImage', 'gcr.io/go-containerregistry/gcrane:debug')
    def inheritFrom = parameters.get('inheritFrom', 'default')

    echo "Starting CD node"

    podTemplate(label: label, inheritFrom: "${inheritFrom}", yaml: """
      apiVersion: v1
      kind: Pod
      spec:
      tolerations:
        - key: kubernetes.io/arch
          operator: Equal
          value: "arm64"
          effect: NoSchedule      
      nodeSelector:
        sprinthive.com/arch: "arm"
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
      """
    ) {
        node(label) {
            body()
        }
    }
}
