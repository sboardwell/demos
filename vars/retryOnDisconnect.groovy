def call(Map vars = [:], Closure body) {

  int retries = vars.get('retries', 3) as int
  int intervalInSeconds = vars.get('intervalInSeconds', 0) as int

  // BODY
  int iteration = 0
  if (body) {
    while (iteration < retries) {
      iteration++
      try {
        body()
        break
      } catch (x) {
        if (nodeWasKilled(x)) {
        continue
      } else {
        throw x
      }
    }
  }
}

@NonCPS
def nodeWasKilled(def x) {
  return x instanceof org.jenkinsci.plugins.workflow.steps.FlowInterruptedException &&
  x.causes*.getClass().contains(org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution.RemovedNodeCause)
}

