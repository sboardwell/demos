def call(String label, Closure body) {
  boolean nothingSetAndInStage = (script.env.POD_CONTAINER == null && script.env.STAGE_NAME)
  if (script.env.POD_CONTAINER == null && script.env.STAGE_NAME) {
    String defaultContainerKey = "CUSTOM_DEFAULT_CONTAINER"
    String stageDefaultContainerKey = "CUSTOM_DEFAULT_CONTAINER_${STAGE_NAME}".replaceAll("[^A-Za-z0-9]", "_").toUpperCase()
    String defaultContainer = script.env."${stageDefaultContainerKey}" ?: script.env."${defaultContainerKey} ? ""
    if (defaultContainer) {
      container(defaultContainer) {
        steps.invokeMethod('node', [label, body] as Object[])
      }
    }
  } else {
    steps.invokeMethod('node', [label, body] as Object[])
  }
}
