def call(String label, Closure body) {

  // BODY
  if (body) {
    try {
      steps.invokeMethod('node', [label, body] as Object[])
    } catch (x) {
      if (nodeWasKilled(x)) {
        echo "[rebuildOnDisconnect] Detected agent disconnect. Retriggering build before aborting..."
        build(
            wait: false,
            job: env.JOB_NAME,
            parameters: myParams()
        )
      }
      throw x
    }
  }
}

def myParams() {
    return currentBuild.rawBuild.getAction(ParametersAction).getParameters()
}

@NonCPS
def nodeWasKilled(def x) {
  return x instanceof org.jenkinsci.plugins.workflow.steps.FlowInterruptedException &&
  x.causes*.getClass().contains(org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution.RemovedNodeCause)
}
