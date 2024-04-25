def call(subModuleName) {
    return sh(script: "gradle ${subModuleName != null ? "${subModuleName}:" : ""}properties -q  | grep \"^version: \" | awk '{print \$2}'", returnStdout: true).toString().trim()
}
