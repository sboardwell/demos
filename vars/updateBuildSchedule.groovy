import hudson.model.*
import hudson.triggers.*
import org.jenkinsci.plugins.parameterizedscheduler.ParameterizedTimerTrigger

def call(def vars = [:]) {
  def jobStr = vars.jobStr 
  def cronStr = vars.cronStr
  if (!jobStr || !cronStr) {
    error "You must pass both jobStr and cronStr as variables"
  }
  
  TriggerDescriptor TIMER_TRIGGER_DESCRIPTOR = Hudson.instance.getDescriptorOrDie(ParameterizedTimerTrigger.class)

  def item = Jenkins.instance.getItemByFullName(jobStr)
  if (!item) {
    error "Job '${jobStr}' not found."
  }
  def timertrigger = item.getTriggers().get(TIMER_TRIGGER_DESCRIPTOR)
  if (timertrigger) {
    def actualSpec = timertrigger.parameterizedSpecification
   if (cronStr.equals(actualSpec)) {
     println "Cron spec identical ${getCronWithLineBreaks(cronStr)}. Doing nothing..."
   } else {
     println "Cron specs differ expected: ${getCronWithLineBreaks(cronStr)} vs actual: ${getCronWithLineBreaks(actualSpec)}. Changing..."
     timertrigger = new ParameterizedTimerTrigger(cronStr)
     item.addTrigger(timertrigger);
     item.save();
   }
  } else {
   println "Cron spec missing. Adding ${getCronWithLineBreaks(cronStr)}..."
   timertrigger = new ParameterizedTimerTrigger(cronStr)
   item.addTrigger(timertrigger);
   item.save();
  }
  
}

def getCronWithLineBreaks(String cronStr) {
   return """
---
${cronStr}
---
"""
}
