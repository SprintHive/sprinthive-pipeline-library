#!/usr/bin/groovy
def call(Map parameters = [:], body) {
    def label = parameters.get('label', buildId('ciNode'))
    def imageArch = parameters.get('imageArch', 'arm64')
    def buildArm = imageArch == 'arm64' ? true : false
    def gradleImage = parameters.get('gradleImage', 'gradle:5.1-jdk-alpine')
    def grypeScannerImage = parameters.get('grypeScannerImage', buildArm ? 'anchore/grype:debug-arm64v8' : 'anchore/grype:debug')
    def kanikoImage = parameters.get('kanikoImage', 'gcr.io/kaniko-project/executor:debug')
    def craneImage = parameters.get('craneImage', 'gcr.io/go-containerregistry/gcrane:debug')
    def helmImage = parameters.get('helmImage', 'alpine/helm:3.12.3')
    def nodejsImage = parameters.get('nodejsImage', 'node:20-alpine')
    def inheritFrom = parameters.get('inheritFrom', 'default')


    def armTolerations = """
        - key: kubernetes.io/arch
          operator: Equal
          value: "arm64"
          effect: NoSchedule
    """
    def armNodeSelector = """
        sh_arch: "arm"
    """
    echo "Starting CI node"
    echo "Building for: ${imageArch}"

    podTemplate(inheritFrom: "${inheritFrom}", yaml: """
    apiVersion: v1
    kind: Pod
    spec:
      tolerations:
        ${buildArm ? armTolerations : ''}
      nodeSelector:
        ${buildArm ? armNodeSelector : ''}
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