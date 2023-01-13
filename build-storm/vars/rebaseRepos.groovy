import jenkins.branch.*
import org.jenkinsci.plugins.workflow.multibranch.*

def call() {
    getFolderItemNamesAndNoTriggerProperty().each { orgFolderName, currentProperty ->
        if (shouldProcessFolder(currentProperty.getBranches(), orgFolderName)) {
            def propertyToReinstate = sanitizePropertyIfNeeded(currentProperty)
            def tempProperty = new NoTriggerOrganizationFolderProperty('bla' + System.currentTimeMillis())
            println """\
                [INFO] : Current regex      : ${currentProperty.getBranches()}
                [INFO] : Temp regex         : ${tempProperty.getBranches()}
                [INFO] : Regex to reinstate : ${propertyToReinstate.getBranches()}
                """.stripIndent()
            try {
                // set temp regex
                println "[INFO] : Setting temporary NoTriggerOrganizationFolderProperty values for ${orgFolderName}... " + new Date()
                replaceNoTriggerProperty(orgFolderName, currentProperty, tempProperty)

                // trigger org folder scan
                println "[INFO] : Waiting for the scan of '${orgFolderName}' to stop before scheduling..."
                while (isBuildBlocked(orgFolderName, OrganizationFolder.class)) {
                    sleep 1
                    println "[INFO] : Still waiting for the scan of '${orgFolderName}' to stop before scheduling..."
                }
                println "[INFO] : Scan stopped! Scheduling the scan of '${orgFolderName}'..."
                scheduleBuild(orgFolderName, OrganizationFolder.class)

                println "[INFO] : Waiting for the scan of '${orgFolderName}' to start..."
                while(!isBuildBlocked(orgFolderName, OrganizationFolder.class)) {
                    sleep 1
                    println "[INFO] : Still waiting for the scan of '${orgFolderName}' to start..."
                }
                println "[INFO] : Scan started! Waiting until it stops..."
                while(isBuildBlocked(orgFolderName, OrganizationFolder.class)) {
                    println "[INFO] : Still waiting for the scan of '${orgFolderName}' to stop..."
                    sleep 1
                }
                println "[INFO] : Scan stopped!"

                // iterate through child projects
                println "[INFO MB] : Processing the Multibranch jobs from folder '${orgFolderName}'..."
                getMultiBranchItemNames(orgFolderName).each { multiBranchItemName ->
                    println "[INFO MB] : Waiting for the scan of '${multiBranchItemName}' to stop before scheduling..."
                    while (isBuildBlocked(multiBranchItemName, WorkflowMultiBranchProject.class)) {
                        sleep 1
                        println "[INFO MB] : Still waiting for the scan of '${multiBranchItemName}' to stop before scheduling..."
                    }
                    println "[INFO MB] : Scan stopped! Scheduling the scan of '${multiBranchItemName}'..."
                    scheduleBuild(multiBranchItemName, WorkflowMultiBranchProject.class)

                    println "[INFO MB] : Waiting for the scan of '${multiBranchItemName}' to start..."
                    while(!isBuildBlocked(multiBranchItemName, WorkflowMultiBranchProject.class)) {
                        sleep 1
                        println "[INFO MB] : Still waiting for the scan of '${multiBranchItemName}' to start..."
                    }
                    println "[INFO MB] : Scan started! Waiting until it stops..."
                    while(isBuildBlocked(multiBranchItemName, WorkflowMultiBranchProject.class)) {
                        println "[INFO MB] : Still waiting for the scan of '${multiBranchItemName}' to stop..."
                        sleep 1
                    }
                    println "[INFO MB] : Scan stopped!"

                    copyHashes(multiBranchItemName, WorkflowMultiBranchProject.class)
                }
            } finally {
                println "[INFO] : Reinstating original NoTriggerOrganizationFolderProperty values for ${orgFolderName}... " + new Date()
                replaceNoTriggerProperty(orgFolderName, tempProperty, propertyToReinstate)
            }
        }
    }
}

@NonCPS
def getItem(def fullName, def clazz) {
    def item = Jenkins.instance.getAllItems(clazz).find { it.fullName == fullName}
    if (item == null) {
        error "Could not find item '${fullName}' of type '${clazz.name}'"
    }
    return item
}

@NonCPS
def isBuildBlocked(def fullName, def clazz) {
    return getItem(fullName, clazz).isBuildBlocked()
}

@NonCPS
boolean scheduleBuild(def fullName, def clazz) {
    if (!getItem(fullName, clazz).scheduleBuild(0)) {
        error "Could not schedule build for '${fullName}' of type '${clazz.name}'"
    }
}

@NonCPS
def replaceNoTriggerProperty(String fullName, NoTriggerOrganizationFolderProperty actual, NoTriggerOrganizationFolderProperty replacement) {
    def folder = getItem(fullName, OrganizationFolder.class)
    folder.getProperties().remove(actual)
    folder.addProperty(replacement)
}

@NonCPS
def getFolderItemNamesAndNoTriggerProperty() {
    def folderItems = Jenkins.instance.getAllItems(OrganizationFolder.class)
    def folderItemNamesAndNoTriggerProperty = [:]
    folderItems.each {
        NoTriggerOrganizationFolderProperty currentProperty = it.getProperties().get(NoTriggerOrganizationFolderProperty.class)
        folderItemNamesAndNoTriggerProperty.put(it.fullName, currentProperty)
    }
    return folderItemNamesAndNoTriggerProperty
}

@NonCPS
boolean shouldProcessFolder(String branchesRegex, String folderFullName) {
    return (branchesRegex.startsWith('NEEDS_REBASING_PREFIX') || folderFullName == params?.ORG_FOLDER_NAME)
}

@NonCPS
def sanitizePropertyIfNeeded(def currentProperty) {
    NoTriggerOrganizationFolderProperty propertyToReinstate = currentProperty
    if (currentProperty.getBranches().startsWith('NEEDS_REBASING_PREFIX')) {
        // We need to remove the prefix
        propertyToReinstate = new NoTriggerOrganizationFolderProperty(currentProperty.getBranches().replace('NEEDS_REBASING_PREFIX', ''))
        // Bitbucket does not have a strategy option
        if (currentProperty.strategy) {
            propertyToReinstate.setStrategy(currentProperty.strategy)
        }
    }
    return propertyToReinstate
}

@NonCPS
def getMultiBranchItemNames(String folderFullName) {
    def multiBranchItems = Jenkins.instance.getAllItems(WorkflowMultiBranchProject.class)
    def multiBranchItemNames = []
    multiBranchItems.each {
        if (it.fullName.startsWith(folderFullName)) {
            multiBranchItemNames << it.fullName
        }
    }
    return multiBranchItemNames
}

@NonCPS
def copyHashes(def jobName, def clazz) {
    def parentDir = getItem(jobName, clazz).jobsDir
    parentDir.traverse(type: groovy.io.FileType.FILES, nameFilter: ~/scm-last-seen-revision-hash.xml/) { source ->
        def dest = new File(source.parent, 'scm-revision-hash.xml')
        dest << source.text
        println "[INFO MB] : copied ${source} -> ${dest}"
    }
}
