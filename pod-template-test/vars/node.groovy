def call(String label = '', Closure body) {
  Closure newBody = null
  def logStr = ["Custom node step with label: ${label} - start"]
  // USE IF WE SHOULD NOT SET DEFAULT CONTAINER AT PIPELINE ROOT (outside the stages block)
  // if (env.POD_CONTAINER == null && env.STAGE_NAME ) {
  // USE IF WE SHOULD ALLOW SETTING DEFAULT CONTAINER AT PIPELINE ROOT (outside the stages block)
  if (env.POD_CONTAINER == null ) {

    String inheritFrom = label
    def possibleMatches = []
    String defaultContainer = ''
    AutoDefaultContainerUtil.getInfos(inheritFrom, possibleMatches, logStr)
    if (possibleMatches.size() == 0) {
      logStr << "Nothing found, nothing to do..."
    }
    else if (possibleMatches.size() > 1) {
      def defaultContainerNames = possibleMatches.collect { it.defaultContainer } as Set
      if (defaultContainerNames.size() == 1 && defaultContainerNames[0] != '') {
        logStr << "Multiple matches found, but all have the same non-null defaultContainer..."
        defaultContainer = possibleMatches[0].defaultContainer
      } else {
        logStr << "Multiple matches found with different values. Impossible to choose..."
      }
    }
    else {
      if (possibleMatches[0].defaultContainer) {
        logStr << "Single match found with non-null defaultContainer..."
        defaultContainer = possibleMatches[0].defaultContainer
      } else {
        logStr << "Single match found, but without a defaultContainer..."
      }
    }
    if (defaultContainer) {
      newBody = {
        container(defaultContainer) {
          body.call()
        }
      }
    }
    if (newBody) {
      logStr << "Custom node step with label: ${label} - invoke newBody within container '${defaultContainer}' (POD_CONTAINER: '${env.POD_CONTAINER}', STAGE_NAME: '${env.STAGE_NAME}')"
      if (env.DEFAULT_CONTAINER_SOLUTION_VERBOSE == "true") { steps.echo logStr.join('\n') }
      steps.invokeMethod('node', [label, newBody] as Object[])
    } else {
      logStr << "Custom node step with label: ${label} - invoke current body - no defaultContainer (POD_CONTAINER: '${env.POD_CONTAINER}', STAGE_NAME: '${env.STAGE_NAME}')"
      if (env.DEFAULT_CONTAINER_SOLUTION_VERBOSE == "true") { steps.echo logStr.join('\n') }
      steps.invokeMethod('node', [label, body] as Object[])
    }
  } else {
    logStr << "Custom node step with label: ${label} - invoke current body (POD_CONTAINER: '${env.POD_CONTAINER}', STAGE_NAME: '${env.STAGE_NAME}')"
    if (env.DEFAULT_CONTAINER_SOLUTION_VERBOSE == "true") { steps.echo logStr.join('\n') }
    steps.invokeMethod('node', [label, body] as Object[])
  }
}
