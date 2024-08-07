#!/usr/bin/groovy
def call(Map parameters = [:], body) {
    def label = parameters.get('label', buildId('ciNode'))

    def gradleImage = parameters.get('gradleImage', 'gradle:5.1-jdk-alpine')
    def grypeScannerImage = parameters.get('grypeScannerImage', 'anchore/grype:debug')
    def kanikoImage = parameters.get('kanikoImage', 'gcr.io/kaniko-project/executor:debug')
    def craneImage = parameters.get('craneImage', 'gcr.io/go-containerregistry/gcrane:debug')
    def helmImage = parameters.get('helmImage', 'quay.io/roboll/helmfile:v0.144.0')
    def nodejsImage = parameters.get('nodejsImage', 'node:20-alpine')
    def inheritFrom = parameters.get('inheritFrom', 'default')

    echo "Starting CI node"

    podTemplate(label: label, inheritFrom: "${inheritFrom}", yaml: """
    apiVersion: v1
    kind: Pod
    spec:
      containers:
      - name: kaniko
        image: ${kanikoImage}
        command:
        - busybox
        args:
        - cat
        tty: true
        resources:
          requests:
            memory: 128Mi
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
      - name: grype-scanner
        image: ${grypeScannerImage}
        command:
        - busybox
        args:
        - cat
        tty: true
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
        resources:
          requests:
            memory: 2Gi
      - name: nodejs
        image: ${nodejsImage}
        command:
        - cat
        tty: true
      volumes:
      - name: maven-settings
        configMap:
          name: jenkins-maven-settings
          optional: true
    """
    ) {
        node(label) {
            body()
        }
    }
}
