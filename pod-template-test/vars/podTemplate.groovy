def call(def args, Closure body) {
  echo "Steve's custom step running with: ${args}"
  String inheritFrom = args.inheritFrom
  def possibeMatches = []
  def logStr = []
  // inheritFrom
  def label = Label.get(inheritFrom)
  // check clouds for label
  label?.getClouds().each { cloud ->
    // WARNING: the first template matching the label with be taken
    def template = cloud.getTemplate(label)
    String defaultContainer = ''
    for (container in template.getContainers()) {
      logStr << "Checking cloud: ${cloud.name}, template: ${template.name} (${template.labelSet}), container: ${container.name}"
      // take first non-jnlp as default
      if (container.name != 'jnlp') {
        defaultContainer = container.name
        break
      }
    }
    possibeMatches.add([cloud: cloud.name, inheritFrom: inheritFrom, defaultContainer: defaultContainer])
  }
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
    }
    if (!args.defaultContainer && match.defaultContainer) {
      logStr << "Setting defaultContainer -> '${match.defaultContainer}'..."
      args.cloud = match.defaultContainer
    }
  }
  echo logStr.join('\n')
  
  steps.invokeMethod('podTemplate', [args, body] as Object[])
}
