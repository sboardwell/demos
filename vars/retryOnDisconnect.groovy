def call(Map vars = [:], Closure body) {

  int retries = vars.get('retries', 3) as int

  // BODY
  int attemptedRetries = 0
  if (body) {
    while (attemptedRetries < retries) {
      try {
        body()
        break
      } catch (x) {
        if (nodeWasKilled(x)) {
          if (attemptedRetries < retries) {
            attemptedRetries++
            continue
          }
          echo "Max retry attempts reached. Failing..."
          throw x
        } else {
          throw x
        }
      }
    }
  }
}

@NonCPS
def nodeWasKilled(def x) {
  return x instanceof org.jenkinsci.plugins.workflow.steps.FlowInterruptedException &&
  x.causes*.getClass().contains(org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution.RemovedNodeCause)
}

