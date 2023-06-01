// Deletes the libs directory of a build.
// WARNING: EXECUTE AT THE END OF THE BUILD ONLY 
def call() {
  call(JOB_NAME, BUILD_NUMBER)
}

def call(String jobName, String buildNumber) {
  deleteLibsDir(String jobName, String buildNumber)  
}

@NonCPS
def deleteLibsDir(String jobName, String buildNumber) {
    def jobObj = Jenkins.instance.getItem(jobName)
    def jobRootDir = jobObj.rootDir
    def buildLibsDir = new File(jobRootDir, "builds/${buildNumber}/libs")
    if (buildLibsDir.exists()) {
        println "Deleting libs dir: ${buildLibsDir}"
        buildLibsDir.deleteDir()
    } else {
        println "Libs dir not found: ${buildLibsDir}"
    }
}
