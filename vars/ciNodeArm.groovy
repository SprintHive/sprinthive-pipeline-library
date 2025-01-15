#!/usr/bin/groovy
def call(Map parameters = [:], body) {
    def label = parameters.get('label', buildId('ciNode'))

    def gradleImage = parameters.get('gradleImage', 'gradle:5.1-jdk-alpine')
    def grypeScannerImage = parameters.get('grypeScannerImage', 'anchore/grype:debug-arm64v8')
    def kanikoImage = parameters.get('kanikoImage', 'gcr.io/kaniko-project/executor:debug')
    def craneImage = parameters.get('craneImage', 'gcr.io/go-containerregistry/gcrane:debug')
    def helmImage = parameters.get('helmImage', 'alpine/helm:3.12.3')
    def nodejsImage = parameters.get('nodejsImage', 'node:20-alpine')
    def inheritFrom = parameters.get('inheritFrom', 'default')

    echo "Starting CI node"

    podTemplate(inheritFrom: "${inheritFrom}", yaml: """
    apiVersion: v1
    kind: Pod
    spec:
      tolerations:
      - key: kubernetes.io/arch
        operator: Equal
        value: "arm64"
        effect: NoSchedule
      nodeSelector:
        sh_arch: "arm"
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
        entrypoint:
        - ['/bin/sh']
        command:
        - sleep
        args:
        - infinity

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
        node(POD_LABEL) {
            body()
        }
    }
}
