#!/usr/bin/groovy
def call(Map parameters = [:], body) {
    def label = parameters.get('label', buildId('ciNode'))
    def arch = parameters.get('arch', 'amd64')
    def buildArm = arch == 'arm64' ? true : false
    def terraformImage = parameters.get('terraformImage', 'europe-west1-docker.pkg.dev/sh-qa-00/dev-containers/terraform-cicd')
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

    podTemplate(serviceAccount: "terraform",label: label, inheritFrom: "${inheritFrom}", yaml: """
    apiVersion: v1
    kind: Pod
    spec:
      serviceAccountName: terraform
      tolerations:
        ${buildArm ? armTolerations : ''}
      nodeSelector:
        ${buildArm ? armNodeSelector : ''}
      containers:
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
