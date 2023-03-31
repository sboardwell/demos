def call(def args = [:], Closure body) {
    def logStr = ["Custom podTemplate step running with: ${args} (POD_CONTAINER: '${env.POD_CONTAINER}', STAGE_NAME: '${env.STAGE_NAME}')"]
  // only process if inheritFrom is passed...
  if (args.containsKey('inheritFrom') && args.inheritFrom != '') {
    String inheritFrom = args.inheritFrom
    def possibleMatches = []
    AutoDefaultContainerUtil.getInfos(inheritFrom, possibleMatches, logStr)
    // check results
    if (possibleMatches.size() == 0) {
      logStr << "Nothing found, nothing to do..."
    }
    else if (possibleMatches.size() > 1) {
      def cloudNames = possibleMatches.collect { it.cloud } as Set
      if (cloudNames.size() == 1 && cloudNames[0] != '') {
        logStr << "Multiple matches found, but all have the same non-null cloud..."
        if (!args.cloud) {
          logStr << "Setting cloud... -> '${match.cloud}'"
          args.cloud = match.cloud
        } else {
          logStr << "NOT setting cloud to '${match.cloud}'. Already set to -> '${args.cloud}'"
        }
      } else {
        logStr << "Multiple matches found with different cloud values. Impossible to choose..."
      }
    }
    else {
      def match = possibleMatches[0]
      logStr << "Single match found, checking cloud..."
      if (!args.cloud) {
        logStr << "Setting cloud... -> '${match.cloud}'"
        args.cloud = match.cloud
      } else {
        logStr << "NOT setting cloud to '${match.cloud}'. Already set to -> '${args.cloud}'"
      }
    }
  }
  if (env.DEFAULT_CONTAINER_SOLUTION_VERBOSE == "true") { steps.echo logStr.join('\n') }
  steps.invokeMethod('podTemplate', [args, body] as Object[])
}
