#!/usr/bin/groovy
def call(Map parameters = [:], body) {
    def label = parameters.get('label', buildId('cd'))

    def dockerImage = parameters.get('dockerImage', 'docker:19.03.8')
    def helmImage = parameters.get('helmImage', 'quay.io/roboll/helmfile:v0.138.7')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    echo "Starting CD node"

    podTemplate(label: label, inheritFrom: "${inheritFrom}", yaml: """
      apiVersion: v1
      kind: Pod
      spec:
        serviceAccount: helm
        containers:
        - name: docker
          image: ${dockerImage}
          command:
          - cat
          tty: true
          securityContext:
            privileged: true
          env:
          - name: DOCKER_HOST
            value: unix:///var/run/docker.sock
          volumeMounts:
          - name: docker-socket
            mountPath: /var/run/docker.sock
        - name: helm
          image: ${helmImage}
          command:
          - cat
          tty: true
          stdin: true
        volumes:
        - name: docker-socket
          hostPath:
            path: /var/run/docker.sock
            type: Socket
      """
    ) {
        node(label) {
            body()
        }
    }
}
