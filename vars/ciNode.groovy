#!/usr/bin/groovy
def call(Map parameters = [:], body) {
    def label = parameters.get('label', buildId('ciNode'))
    def arch = parameters.get('arch', 'amd64')
    def buildArm = arch == 'arm64' ? true : false
    def gradleImage = parameters.get('gradleImage', 'gradle:5.1-jdk-alpine')
    def grypeScannerImage = parameters.get('grypeScannerImage', buildArm ? 'anchore/grype:debug-arm64v8' : 'anchore/grype:debug')
    def kanikoImage = parameters.get('kanikoImage', 'gcr.io/kaniko-project/executor:debug')
    def craneImage = parameters.get('craneImage', 'gcr.io/go-containerregistry/gcrane:debug')
    def helmImage = parameters.get('helmImage', 'ghcr.io/helmfile/helmfile:v0.155.1')
    def terraformImage = parameters.get('terraformImage', 'europe-west1-docker.pkg.dev/sh-qa-00/dev-containers/terraform-deploy:0.4')
    def nodejsImage = parameters.get('nodejsImage', 'node:20-alpine')
    def inheritFrom = parameters.get('inheritFrom', 'default')


    def armTolerations = """
        - key: kubernetes.io/arch
          operator: Equal
          value: "arm64"
          effect: NoSchedule
    """
    def armNodeSelector = """
        sprinthive.com/instance-type: "c4a"
    """
    echo "Starting CI node"
    echo "Building for: ${arch}"

    podTemplate(label: label, inheritFrom: "${inheritFrom}", yaml: """
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
      - name: terraform
        image: ${terraformImage}
        env:
        - name: AWS_ACCESS_KEY_ID
          valueFrom:
            secretKeyRef:
              name: terraform-dev-aws-credentials
              key: accessKey
        - name: AWS_SECRET_ACCESS_KEY
          valueFrom:
            secretKeyRef:
              name: terraform-dev-aws-credentials
              key: secretKey
        volumeMounts:
        - name: ssh-config
          mountPath: "/dump/"
        tty: true
        resources:
          requests:
            memory: 128Mi
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
      - name: ssh-config
        secret:
          secretName: jenkins-ssh-config
    """
    ) {
        node(label) {
            body()
        }
    }
}
