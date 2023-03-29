def call(String label, Closure body) {
  echo "Custom node step with label: ${label}"
  boolean nothingSetAndInStage = (env.POD_CONTAINER == null && env.STAGE_NAME)
  if (env.POD_CONTAINER == null && env.STAGE_NAME) {
    String defaultContainerKey = "CUSTOM_DEFAULT_CONTAINER"
    String stageDefaultContainerKey = "CUSTOM_DEFAULT_CONTAINER_${STAGE_NAME}".replaceAll("[^A-Za-z0-9]", "_").toUpperCase()
    String defaultContainer = env."${stageDefaultContainerKey}" ?: env."${defaultContainerKey}" ?: ""
    if (defaultContainer) {
      Closure newBody = {
        container(defaultContainer) {
          body.call()
        }
      }
      echo "Custom node step with label: ${label} - invoke newBody"
      steps.invokeMethod('node', [label, newBody] as Object[])
    }
  } else {
    echo "Custom node step with label: ${label} - invoke current body"
    steps.invokeMethod('node', [label, body] as Object[])
  }
}
