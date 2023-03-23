def call(String label, Closure body) {
  boolean nothingSetAndInStage = (script.env.POD_CONTAINER == null && script.env.STAGE_NAME)
  if (script.env.POD_CONTAINER == null && script.env.STAGE_NAME) {
    // String defaultContainer = script.env."${STAGE_NAME}_CUSTOM_DEFAULT_CONTAINER" ?: script.env.PIPELINE_CUSTOM_DEFAULT_CONTAINER ? "" 
    steps.invokeMethod('node', [label, body] as Object[])
  } else {
    steps.invokeMethod('node', [label, body] as Object[])
  }
}
