def call(String label, Closure body) {
  call(['label': label, body])
}

def call(Map vars = [:], Closure body) {

  int retries = vars.get('retries', 3) as int

  // BODY
  int attemptedRetries = 0
  if (body) {
    while (attemptedRetries < retries) {
      try {
        steps.invokeMethod('node', [label, body] as Object[])
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
