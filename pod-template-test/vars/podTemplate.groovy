def call(def args, Closure body) {
  steps.echo "Custom podTemplate step running with: ${args}"
  String inheritFrom = args.inheritFrom
  def possibeMatches = []
  def logStr = []
  getInfos(inheritFrom, possibeMatches, logStr)
  // check results
  if (possibeMatches.size() == 0) {
    logStr << "Nothing found, nothing to do..."
  }
  else if (possibeMatches.size() > 1) {
    logStr << "Multiple matches found, don't know what to do..."
  }
  else {
    def match = possibeMatches[0]
    logStr << "Single match found, checking cloud and defaultContainer..."
    if (!args.cloud) {
      logStr << "Setting cloud... -> '${match.cloud}'"
      args.cloud = match.cloud
    } else {
      logStr << "NOT setting cloud to '${match.cloud}'. Already set to -> '${args.cloud}'"
    }
    if (!args.defaultContainer && match.defaultContainer) {
      // We cannot set the default container here so placing an env var for use later
      // TODO - the code below is used in the node.groovy - place into a central util class
      String stageDefaultContainerKey = "CUSTOM_DEFAULT_CONTAINER"
      if (script.env.STAGE_NAME) {
        stageDefaultContainerKey + "_${STAGE_NAME}".replaceAll("[^A-Za-z0-9]", "_").toUpperCase()
      }
      logStr << "Setting env.${stageDefaultContainerKey} = '${match.defaultContainer}'..."
      script.env."${stageDefaultContainerKey}" = match.defaultContainer
    } else {
      logStr << "NOT setting cloud to '${match.cloud}'. Already set to -> '${args.cloud}'"
    }
  }
  steps.echo logStr.join('\n')
  steps.invokeMethod('podTemplate', [args, body] as Object[])
}
@NonCPS
def getInfos(String inheritFrom, def possibeMatches, def logStr) {
    // check clouds for label
    Label.get(inheritFrom)?.getClouds().each { cloud ->
      // WARNING: the first template matching the label with be taken
      def template = cloud.getTemplate(Label.get(inheritFrom))
      String defaultContainer = ''
      for (container in template.getContainers()) {
        logStr << "Checking cloud: ${cloud.name}, template: ${template.name} (${template.labelSet}), container: ${container.name}".toString()
        // take first non-jnlp as default
        if (container.name != 'jnlp') {
          defaultContainer = container.name
          break
        }
      }
      possibeMatches.add([cloud: cloud.name, inheritFrom: inheritFrom, defaultContainer: defaultContainer])
    }
}
