#!/usr/bin/groovy
def call(Map parameters = [:], body) {
    def defaultLabel = buildId('nodejs')
    def label = parameters.get('label', defaultLabel)

    def nodejsImage = parameters.get('nodejsImage', 'node:12-alpine')
    def dockerImage = parameters.get('dockerImage', 'docker:19.03.8')
    def clairScannerImage = parameters.get('clairScannerImage', 'objectiflibre/clair-scanner:latest')
    def helmImage = parameters.get('helmImage', 'quay.io/roboll/helmfile:v0.138.7')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    echo "Starting pod with node and docker"

    podTemplate(label: label, inheritFrom: "${inheritFrom}", yaml: """
    apiVersion: v1
    kind: Pod
    spec:
      serviceAccount: helm
      initContainers:
      - name: clair-whitelist-init
        image: alpine/git
        args:
        - clone
        - --single-branch
        - --depth=1
        - --
        - https://bitbucket.org/sprinthive/clair-whitelist.git
        - /config
        securityContext:
          runAsUser: 1
          allowPriviledgeEscalation: false
          readOnlyRootFilesystem: true
        volumeMounts:
        - name: clair-whitelist
          mountPath: /config
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
        - name: clair-whitelist
          mountPath: /config
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
      volumes:
      - name: docker-socket
        hostPath:
          path: /var/run/docker.sock
          type: Socket
      - name: clair-whitelist
        emptyDir: {}
    """
    ) {
    body()
  }
}
