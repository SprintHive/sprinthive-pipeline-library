#!/usr/bin/groovy
def call(Map parameters = [:], body) {
    def defaultLabel = buildId('nodejs')
    def label = parameters.get('label', defaultLabel)

    def nodejsImage = parameters.get('nodejsImage', 'mhart/alpine-node:8')
    def dockerImage = parameters.get('dockerImage', 'docker:stable')
    def clairScannerImage = parameters.get('clairScannerImage', 'objectiflibre/clair-scanner:latest')
    def helmImage = parameters.get('helmImage', 'lachlanevenson/k8s-helm:v2.9.1')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    echo "Starting pod with node and docker"

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
      - name: clairscanner
        image: ${clairScannerImage}
        command:
        - cat
        tty: true
        securityContext:
          privileged: true
        env:
        - name: DOCKER_HOST
          value: unix:///var/run/docker.sock
        - name: POD_IP
          valueFrom:
            fieldRef:
              fieldPath: status.podIP
        volumeMounts:
        - name: docker-socket
          mountPath: /var/run/docker.sock
        ports:
        - containerPort: 9279
          name: http
          protocol: TCP
      - name: helm
        image: ${helmImage}
        command:
        - cat
        tty: true
      - name: nodejs
        image: ${nodejsImage}
        command:
        - cat
        tty: true
        workingDir: /home/jenkins/
      volumes:
      - name: docker-socket
        hostPath:
          path: /var/run/docker.sock
          type: Socket
    """
    ) {
    body()
  }
}
