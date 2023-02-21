import jenkins.branch.*
import org.jenkinsci.plugins.workflow.multibranch.*
import static groovy.json.JsonOutput.*

def call(def args = [:]) {
    // configurable properties
    int sleepIntervalInSeconds = args.sleepIntervalInSeconds ? Integer.parseInteger(args.sleepIntervalInSeconds) : 5
    int timeoutStartOrgScanInMinutes = args.timeoutStartOrgScanInMinutes ? Integer.parseInteger(args.timeoutStartOrgScanInMinutes) : 10
    int timeoutAllScansCompletedInHours = args.timeoutAllScansCompletedInHours ? Integer.parseInteger(args.timeoutAllScansCompletedInHours) : 2

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

                    scheduleBuild(orgFolderName, OrganizationFolder.class)

                    timeout(10) {
                        println "[INFO] : Waiting for the scan of '${orgFolderName}' to start..."
                        while(!isBuildBlocked(orgFolderName, OrganizationFolder.class)) {
                            sleep sleepIntervalInSeconds
                            println "[INFO] : Still waiting for the scan of '${orgFolderName}' to start..."
                        }
                    }
                    println "[INFO] : Scan started! Processing..."

                    def result = [jobName: orgFolderName]
                    while (!result.processed) {
                        parseOrgLogFile(result)
                        // check for scans not triggered...
                        result.manualScan?.each { multiBranchItemName ->
                            println "[INFO] : Branch indexing not automatically triggered for '${multiBranchItemName}'. Triggering manually..."
                            scheduleBuild(multiBranchItemName, WorkflowMultiBranchProject.class)
                        }
                        result.manualScan = []
                        if (!result.processed) {
                            sleep sleepIntervalInSeconds
                        }
                    }
                    println prettyPrint(toJson(result))
                    if (!result.summary.contains('Finished: SUCCESS')) {
                        error "It seems the org scan did not complete successfully. Please check."
                    }
                }
            } finally {
                println "[INFO] : Reinstating original NoTriggerOrganizationFolderProperty values for ${orgFolderName}... " + new Date()
                replaceNoTriggerProperty(orgFolderName, tempProperty, propertyToReinstate)
            }
        }
    }
}

@NonCPS
def extractDate(def logFile, def regex) {
    def logLines = logFile.readLines().grep(regex)
    if (logLines) {
        def logLine = logLines.first()
        def logDateStr = logLine?.replace('[','').replaceAll('].*','')
        if (logDateStr) {
            return Date.parseToStringDate(logDateStr)
        }
    }
    return null
}

@NonCPS
def parseOrgLogFile(def result) {
    def INFO = "[INFO ORG - ${result.jobName}]"
    def rootDir = getItem(result.jobName, OrganizationFolder.class).getRootDir()
    def logFile = new File(rootDir, '/computation/computation.log')
    if (logFile.exists()) {
        println "${INFO} : Using log file '${logFile}'"

        // start
        result.start = extractDate(logFile, ~/^.*Starting organization scan.*/)

        // stop
        result.stop = extractDate(logFile, ~/^.*Finished organization scan.*/)

        def summary = logFile.readLines().grep(~/^.*(Starting organization scan|Finished organization scan|Proposing|Finished examining|Finished: ).*$/)
        result.summary = summary
        println "${INFO} : Current scan summary..."
        println prettyPrint(toJson(summary))

        // examined repos
        logFile.readLines().grep(~/^Proposing.*/).each {
            result.examined = result.examined?: [:]
            result.manualScan = result.manualScan?: []
            if (!result.examined.containsKey(it)) {
                String repoNameWithOrg = it.replaceAll('Proposing[ ]+', '')
                String repoName = repoNameWithOrg.replaceAll('.*/', '')
                String jobName = "${result.jobName}/${repoName}"
                result.examined.put(it, [jobName: jobName])
            }
            def childResult = result.examined.get(it)
            if (!childResult.processed) {
                parseMultiBranchLogFile(childResult, result)
            }
        }

        // processed
        if (result.summary.any { it.startsWith('Finished: ') } ) {
            // ensure all child repos have been correctly processed
            boolean allProcessed = true
            result.examined.each { childRepoLogStr, childRepo ->
                if (!childRepo.processed) {
                    allProcessed = false
                }
            }
            result.processed = allProcessed
        }
    } else {
        println "${INFO} : Ignoring since we can't find the log file."
        result.processed = true
    }
}


@NonCPS
def parseMultiBranchLogFile(def result, def orgResult) {
    // TODO remove:
    boolean failOnMissingRepoJobs = false
    def INFO = "[INFO MB - ${result.jobName}]"
    def item = getItem(result.jobName, WorkflowMultiBranchProject.class, failOnMissingRepoJobs)
    if (!item) {
        if (orgResult.stop) {
            println "${INFO} : WARNING - Item NOT found after org scan has finished. Assuming the criteria wasn't met and skipping."
            result.processed = true
        } else {
            println "${INFO} : WARNING - Item NOT found but org scan still running. we'll wait..."
        }
        return
    }
    def rootDir = getItem(result.jobName, WorkflowMultiBranchProject.class).getRootDir()
    def logFile = new File(rootDir, '/indexing/indexing.log')
    if (logFile.exists()) {
        println "${INFO} : Using log file '${logFile}'"

        // start
        result.start = extractDate(logFile, ~/^.*Starting branch indexing.*/)

        // stop
        result.stop = extractDate(logFile, ~/^.*Finished branch indexing.*/)

        // sanity check
        if (result.start.after(orgResult.start)) {
            println "${INFO} : Sanity check OK - Scan (${result.start}) performed after the org scan (${orgResult.start})."
            result.sanityCheck = 'OK'
        } else {
            println "${INFO} : Sanity check FAILED - Scan (${result.start}) performed before the org scan (${orgResult.start}). Triggering scan..."
            result.sanityCheck = 'FAILED'
            orgResult.manualScan << result.jobName
            return
        }

        // processed
        def summary = logFile.readLines().grep(~/^.*(Starting branch indexing|Finished branch indexing|were processed|Finished examining|Finished: ).*$/)
        result.summary = summary
        if (result.stop) {
            result.processed = true
            result.hashesCopied = copyHashes(result.jobName, WorkflowMultiBranchProject.class)
            println "${INFO} : Repo processed. Summary below..."
            println prettyPrint(toJson(result))
        }
    }
}

@NonCPS
def getItem(def fullName, def clazz, boolean strict = true) {
    def item = Jenkins.instance.getAllItems(clazz).find { it.fullName == fullName}
    if (item == null && strict) {
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
        if (currentProperty) {
            println "[INFO] : adding NoTriggerOrganizationFolderProperty for ${it.fullName} = ${currentProperty}"
            folderItemNamesAndNoTriggerProperty.put(it.fullName, currentProperty)
        } else {
            error "NoTriggerOrganizationFolderProperty for ${it.fullName} = ${currentProperty}"
        }
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
