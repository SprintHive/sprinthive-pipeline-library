def call() {
    return sh(script: 'gradle properties -q  | grep "^version: " | awk \'{print $2}\'', returnStdout: true).toString().trim()
}
