import groovy.json.JsonOutput

def call(application, namespaces) {
    echo "Publishing deployment metric"
    def deploymentResult = [
            'buildId': currentBuild.number,
            'startTime': currentBuild.startTimeInMillis,
            'finishTime': java.time.Instant.now().toEpochMilli(),
            'duration': currentBuild.duration,
            'job': currentBuild.fullDisplayName,
            'jobPath': currentBuild.fullProjectName,
            'jobName': currentBuild.projectName,
            'success': true,
            'version': params.imageTag,
            'application': application,
            'namespaces': namespaces,
            'changeLog': params.changeLog
    ]
    json = JsonOutput.toJson(deploymentResult)
    httpRequest contentType: 'APPLICATION_JSON', httpMode: 'POST', authentication: 'elasticsearch', requestBody: json, responseHandle: 'NONE', url: "${env.ELASTICSEARCH_URL}/deployment/_doc"
}
