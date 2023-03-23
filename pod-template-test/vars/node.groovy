def call(String label, Closure body) {
  boolean nothingSetAndInStage = (env.POD_CONTAINER == null && env.STAGE_NAME)
  if (env.POD_CONTAINER == null && env.STAGE_NAME) {
    String defaultContainerKey = "CUSTOM_DEFAULT_CONTAINER"
    String stageDefaultContainerKey = "CUSTOM_DEFAULT_CONTAINER_${STAGE_NAME}".replaceAll("[^A-Za-z0-9]", "_").toUpperCase()
    String defaultContainer = env."${stageDefaultContainerKey}" ?: env."${defaultContainerKey} ? ""
    if (defaultContainer) {
      container(defaultContainer) {
        steps.invokeMethod('node', [label, body] as Object[])
      }
    }
  } else {
    steps.invokeMethod('node', [label, body] as Object[])
  }
}
