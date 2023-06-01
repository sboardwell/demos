// Deletes the libs directory of a build.
// WARNING: EXECUTE AT THE END OF THE BUILD ONLY 
def call() {
  call(env.JOB_NAME, env.BUILD_NUMBER)
}

def call(String jobName, String buildNumber) {
  deleteLibsDir(jobName, buildNumber)  
}

@NonCPS
def deleteLibsDir(String jobName, String buildNumber) {
  try {
    def jobObj = Jenkins.instance.getItem(jobName)
    def jobRootDir = jobObj.rootDir
    def buildLibsDir = new File(jobRootDir, "builds/${buildNumber}/libs")
    if (buildLibsDir.exists()) {
      println "Deleting libs dir: ${buildLibsDir}"
      buildLibsDir.deleteDir()
    } else {
      println "Libs dir not found: ${buildLibsDir}"
    }
  } catch (def ignore) {
    println "Caught and ignoring exception: ${ignore.message}"
  }
}
