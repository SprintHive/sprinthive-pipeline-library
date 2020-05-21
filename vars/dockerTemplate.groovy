#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('docker')
    def label = parameters.get('label', defaultLabel)

    def dockerImage = parameters.get('dockerImage', 'docker:19.03.8')
    def clairScannerImage = parameters.get('clairScannerImage', 'objectiflibre/clair-scanner:latest')
    def helmImage = parameters.get('helmImage', 'lachlanevenson/k8s-helm:v2.13.0')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    echo "Starting docker jenkins worker"

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
        resources:
          limits:
            ephemeral-storage: 3Gi
          requests:
            ephemeral-storage: 3Gi
      - name: helm
        image: ${helmImage}
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
