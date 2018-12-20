#!/usr/bin/groovy

def call(Map parameters = [:], body) {
    def defaultLabel = buildId('gradle')
    def label = parameters.get('label', defaultLabel)

    def gradleImage = parameters.get('gradleImage', 'bitstack701/base-gradle:v3.0.2')
    def clairScannerImage = parameters.get('clairScannerImage', 'objectiflibre/clair-scanner:latest')
    def dockerImage = parameters.get('dockerImage', 'docker:stable')
    def helmImage = parameters.get('helmImage', 'lachlanevenson/k8s-helm:v2.9.1')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    echo "Starting java-centric jenkins worker"

    podTemplate(label: label, inheritFrom: "${inheritFrom}", yaml: """
    apiVersion: v1
    kind: Pod
    spec:
      securityContext:
        runAsUser: 1000
        fsGroup: 2000
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
          runAsUser: 0
          runAsGroup: 0
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
          runAsUser: 0
          runAsGroup: 0
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
        resources:
          limits:
            ephemeral-storage: 3Gi
          requests:
            ephemeral-storage: 3Gi
      - name: helm
        image: ${helmImage}
        env:
        - name: HELM_HOME
          value: /tmp
        command:
        - cat
        tty: true
      - name: gradle
        image: ${gradleImage}
        command:
        - cat
        tty: true
        env:
        - name: MAVEN_OPTS
          value: -Duser.home=/root/
        volumeMounts:
        - name: maven-settings
          mountPath: /root/.m2
      volumes:
      - name: docker-socket
        hostPath:
          path: /var/run/docker.sock
          type: Socket
      - name: maven-settings
        configMap:
          name: jenkins-maven-settings
      - name: clair-whitelist
        emptyDir: {}
    """
    ) {
    body()
  }
}
