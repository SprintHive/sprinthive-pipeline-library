#!/usr/bin/groovy

def call() {
    def changeLogText = ""
    def changeLogSets = currentBuild.changeSets
    for (int i = 0; i < changeLogSets.size(); i++) {
        def entries = changeLogSets[i].items
        for (int j = 0; j < entries.length; j++) {
            def entry = entries[j]
            if (changeLogText != "") {
                changeLogText += "\n"
            }
            changeLogText += "`${entry.commitId.substring(0, 8)}` - ${entry.msg}"
        }
    }

    return changeLogText
}
