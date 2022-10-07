#!/usr/bin/groovy

def call(Map parameters = [:], body) {
    def defaultLabel = buildId('gradle')
    def label = parameters.get('label', defaultLabel)

    def gradleImage = parameters.get('gradleImage', 'gradle:5.1-jdk-alpine')
    def grypeScannerImage = parameters.get('grypeScannerImage', 'anchore/grype:debug')
    def kanikoImage = parameters.get('kanikoImage', 'gcr.io/kaniko-project/executor:debug')
    def helmImage = parameters.get('helmImage', 'quay.io/roboll/helmfile:v0.138.7')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    echo "Starting java-centric jenkins worker"

    podTemplate(label: label, inheritFrom: "${inheritFrom}", yaml: """
    apiVersion: v1
    kind: Pod
    spec:
      serviceAccount: jenkins-worker
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
      volumes:
      - name: maven-settings
        configMap:
          name: jenkins-maven-settings
          optional: true
    """
    ) {
    body()
  }
}
