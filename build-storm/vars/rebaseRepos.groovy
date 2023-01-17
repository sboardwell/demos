import jenkins.branch.*
import org.jenkinsci.plugins.workflow.multibranch.*
import java.time.LocalDateTime
import java.time.Duration

def call(def args = [:]) {
    // configurable properties
    int sleepIntervalInSeconds = args.sleepIntervalInSeconds ? Integer.parseInteger(args.sleepIntervalInSeconds) : 5
    int timeoutStartOrgScanInMinutes = args.timeoutStartOrgScanInMinutes ? Integer.parseInteger(args.timeoutStartOrgScanInMinutes) : 10
    int timeoutAllScansCompletedInHours = args.timeoutAllScansCompletedInHours ? Integer.parseInteger(args.timeoutAllScansCompletedInHours) : 2

    def summary = []
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
                timeout(time: timeoutAllScansCompletedInHours, unit: 'HOURS') {
                    // set temp regex
                    println "[INFO] : Setting temporary NoTriggerOrganizationFolderProperty values for ${orgFolderName}... " + new Date()
                    replaceNoTriggerProperty(orgFolderName, currentProperty, tempProperty)

                    // trigger org folder scan
                    println "[INFO] : Waiting for the scan of '${orgFolderName}' to stop before scheduling..."
                    while (isBuildBlocked(orgFolderName, OrganizationFolder.class)) {
                        sleep sleepIntervalInSeconds
                        println "[INFO] : Still waiting for the scan of '${orgFolderName}' to stop before scheduling..."
                    }
                    println "[INFO] : Scan stopped! Scheduling the scan of '${orgFolderName}'..."

                    // get a list of all child projects which are currently running a scan pre org scan.
                    // This will:
                    // - allow us to detect which child projects are running due to the intitial org folder scan
                    // - in turn, this means we can skip rescanning those which had already started
                    def preOrgScans = []
                    getMultiBranchItemNames(orgFolderName).each { multiBranchItemName ->
                        if (isBuildBlocked(multiBranchItemName, WorkflowMultiBranchProject.class)) {
                            preOrgScans << multiBranchItemName
                        }
                    }
                    println "[INFO] : Child projects detected (project -> scanRunning): ${preOrgScans}"
                    def orgScanStart = LocalDateTime.now()
                    scheduleBuild(orgFolderName, OrganizationFolder.class)

                    timeout(10) {
                        println "[INFO] : Waiting for the scan of '${orgFolderName}' to start..."
                        while(!isBuildBlocked(orgFolderName, OrganizationFolder.class)) {
                            sleep sleepIntervalInSeconds
                            println "[INFO] : Still waiting for the scan of '${orgFolderName}' to start..."
                        }
                    }
                    println "[INFO] : Scan started! Waiting until it stops..."
                    while(isBuildBlocked(orgFolderName, OrganizationFolder.class)) {
                        println "[INFO] : Still waiting for the scan of '${orgFolderName}' to stop..."
                        sleep sleepIntervalInSeconds
                    }
                    addSummary(summary, orgFolderName, orgScanStart, LocalDateTime.now())
                    println "[INFO] : Scan stopped!"

                    // iterate through child projects and categorise
                    def waitForPreOrgScanToFinish = []
                    def waitForPostOrgScanToFinish = []
                    def scanFinished = []
                    println "[INFO MB] : Processing the Multibranch jobs from folder '${orgFolderName}'..."
                    getMultiBranchItemNames(orgFolderName).each { multiBranchItemName ->
                        if (isBuildBlocked(multiBranchItemName, WorkflowMultiBranchProject.class)) {
                            if (preOrgScans.contains(multiBranchItemName)) {
                                println "[INFO MB] : Previosuly running scan detected for '${multiBranchItemName}'. Will restart after this has finished."
                                waitForPreOrgScanToFinish << multiBranchItemName
                            } else {
                                println "[INFO MB] : Freshly started scan detected for '${multiBranchItemName}' due to upstream organisation scan."
                                waitForPostOrgScanToFinish << multiBranchItemName
                            }
                        } else {
                            println "[INFO MB] : No scan has been detected for '${multiBranchItemName}'. Will start one and wait for completion."
                            scheduleBuild(multiBranchItemName, WorkflowMultiBranchProject.class)
                            waitForPostOrgScanToFinish << multiBranchItemName
                        }
                    }

                    // Now wait until all scans have finished...
                    def childScanTimes = [:]
                    while(waitForPreOrgScanToFinish || waitForPostOrgScanToFinish) {
                        sleep sleepIntervalInSeconds
                        println "[INFO MB] : Waiting for the scans of to stop (preOrgScan = ${waitForPreOrgScanToFinish.size()}, postOrgScan = ${waitForPostOrgScanToFinish.size()}, finished = ${scanFinished.size()})."
                        getMultiBranchItemNames(orgFolderName).each { multiBranchItemName ->
                            childScanTimes.put(multiBranchItemName, LocalDateTime.now())
                            if (!isBuildBlocked(multiBranchItemName, WorkflowMultiBranchProject.class)) {
                                if (waitForPreOrgScanToFinish.contains(multiBranchItemName)) {
                                    println "[INFO MB] : Pre org scan build finished for '${multiBranchItemName}'. Starting final official scan..."
                                    waitForPreOrgScanToFinish.remove(multiBranchItemName)
                                    waitForPostOrgScanToFinish << multiBranchItemName
                                    scheduleBuild(multiBranchItemName, WorkflowMultiBranchProject.class)
                                } else if (waitForPostOrgScanToFinish.contains(multiBranchItemName)) {
                                    println "[INFO MB] : Final scan has finished for '${multiBranchItemName}'. Copying hashes..."
                                    waitForPostOrgScanToFinish.remove(multiBranchItemName)
                                    scanFinished << multiBranchItemName
                                    def hashesCopied = copyHashes(multiBranchItemName, WorkflowMultiBranchProject.class)
                                    addSummary(summary, multiBranchItemName, childScanTimes.get(multiBranchItemName), LocalDateTime.now(), hashesCopied)
                                }
                            } else {
                                println "[INFO MB] : Scan still running for '${multiBranchItemName}'."
                            }
                        }
                    }
                }
            } finally {
                println "[INFO] : Reinstating original NoTriggerOrganizationFolderProperty values for ${orgFolderName}... " + new Date()
                replaceNoTriggerProperty(orgFolderName, tempProperty, propertyToReinstate)
            }
        }
    }
    println "Build Summary:\n${summary.join('\n')}"
}

@NonCPS
def addSummary(def summary, String itemName, LocalDateTime start, LocalDateTime stop, def hashesCopied = '') {
    summary << "Item '${itemName}' scan started at ${start}."
    summary << "Item '${itemName}' scan stopped at ${stop} (approximate duration: ${Duration.between(start, stop).getSeconds()} seconds)"
    if (hashesCopied) {
        summary << "Item '${itemName}' hashes copied: ${hashesCopied}"
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
    def lines = []
    def parentDir = getItem(jobName, clazz).jobsDir
    parentDir.traverse(type: groovy.io.FileType.FILES, nameFilter: ~/scm-last-seen-revision-hash.xml/) { source ->
        def dest = new File(source.parent, 'scm-revision-hash.xml')
        dest << source.text
        lines << "${source} -> ${dest}"
    }
    println "[INFO MB] : copied source -> dest...\n${lines.join('\n')}"
    return lines.size()
}
