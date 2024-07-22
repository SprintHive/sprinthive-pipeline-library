def call() {
    return sh(script: "node -p 'require(\"./client/package.json\").version'", returnStdout: true).toString().trim()
}
