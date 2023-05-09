@NonCPS
static def getInfos(String inheritFrom, def possibleMatches, def logStr) {
  def label = Label.get(inheritFrom)
  // check clouds for label
  label?.clouds.each { def cloud ->
    cloud.getTemplatesFor(label).each { def template ->
        String defaultContainer = ''
        logStr << "Checking cloud: ${cloud.name}, template: ${template.name} (${template.labelSet}), templatesInnerInheritFrom: ${template.inheritFrom}, containers: ${template.containers.collect { it.name }}".toString()
        for (container in template.getContainers()) {
            // take first non-jnlp as default
            if (container.name != 'jnlp') {
                defaultContainer = container.name
                break
            }
        }
        // if we don't find a defaultContainer but the template has an inheritFrom value, check the parent template
        if (!defaultContainer && template.inheritFrom && template.inheritFrom != 'null' && template.inheritFrom != '') {
            def parentTemplate = cloud.getAllTemplates().find { it.name == template.inheritFrom }
            if (parentTemplate) {
                logStr << "Checking cloud: ${cloud.name}, parentTemplate: ${parentTemplate.name} (${parentTemplate.labelSet}), templatesInnerInheritFrom: ${parentTemplate.inheritFrom}, containers: ${parentTemplate.containers.collect { it.name }}".toString()
                for (container in parentTemplate.getContainers()) {
                    // take first non-jnlp as default
                    if (container.name != 'jnlp') {
                        defaultContainer = container.name
                        break
                    }
                }
            }
        }
        possibleMatches.add([cloud: cloud.name, template: template.name, inheritFrom: inheritFrom, defaultContainer: defaultContainer])
    }
  }
  // check nodes
  label?.getNodes().each { node ->
    // in the case of actual nodes, we only want
    // - nodes of type KubernetesSlave
    // - having the exact same name as the requested label (POD_LABEL)
    // e.g. template 'debian' could create a node with the name 'debian-xxxxx'
    String defaultContainer = ''
    if (node.class.simpleName == 'KubernetesSlave' && node.name == inheritFrom) {
      for (container in node.template.getContainers()) {
        logStr << "Checking node: ${node.name}, template: ${template.name} (${template.labelSet}), container: ${container.name}".toString()
        // take first non-jnlp as default
        if (container.name != 'jnlp') {
            defaultContainer = container.name
            break
        }
      }
    }
    possibleMatches.add([cloud: '', inheritFrom: inheritFrom, defaultContainer: defaultContainer])
  }
}
