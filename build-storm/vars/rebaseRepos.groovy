import jenkins.branch.*
import org.jenkinsci.plugins.workflow.multibranch.*

def call() {
    getFolderNames().each {
        processFolder(it)
    }
}

@NonCPS
def getFolderNames() {
    def folderItems = Jenkins.instance.getAllItems(OrganizationFolder.class)
    def folderItemNames = []
    folderItems.each {
        folderItemNames << it.fullName
    }
    return folderItemNames
}

@NonCPS
boolean weShouldProcessFolder(String branchesRegex, String folderFullName) {
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

//@NonCPS
def triggerScan(def item, String infoPrefix = "[INFO]") {
    println "${infoPrefix} : Waiting for the scan of '${item.fullName}' to stop before scheduling..."
    while (item.isBuildBlocked()) {
        sleep 1
        println "${infoPrefix} : Still waiting for the scan of '${item.fullName}' to stop before scheduling..."
    }
    println "${infoPrefix} : Scan stopped!"

    println "${infoPrefix} : Scheduling the scan of '${item.fullName}'..."
    item.scheduleBuild(0)

    println "${infoPrefix} : Waiting for the scan of '${item.fullName}' to start..."
    while(!item.isBuildBlocked()) {
        sleep 1
        println "${infoPrefix} : Still waiting for the scan of '${item.fullName}' to start..."
    }
    println "${infoPrefix} : Scan started!"

    println "${infoPrefix} : Scan the scan of '${item.fullName}' to start..."
    while(item.isBuildBlocked()) {
        println "${infoPrefix} : Waiting for the scan of '${item.fullName}' to stop..."
        sleep 1
    }
    println "${infoPrefix} : Scan stop!"
}

//@NonCPS
def processFolder(String folderFullName) {
    def folder = Jenkins.instance.getAllItems(OrganizationFolder.class).find { it.fullName == folderFullName}
    println "[INFO] : Checking ${folder.fullName}... "
    NoTriggerOrganizationFolderProperty currentProperty = folder.getProperties().get(NoTriggerOrganizationFolderProperty.class)
    NoTriggerOrganizationFolderProperty propertyToReinstate = sanitizePropertyIfNeeded(currentProperty)
    if (weShouldProcessFolder(currentProperty.getBranches(), folder.fullName)) {
        String randomRegex = 'bla' + System.currentTimeMillis()
        NoTriggerOrganizationFolderProperty tempProperty = new NoTriggerOrganizationFolderProperty(randomRegex)
        println "[INFO] : Current regex : ${currentProperty.getBranches()}"
        println "[INFO] : Temp regex    : ${tempProperty.getBranches()}"
        println "[INFO] : Final regex   : ${propertyToReinstate.getBranches()}"
        try {
            folder.getProperties().remove(currentProperty)
            folder.addProperty(tempProperty)
            println "[INFO] : Scheduling : ${folder.fullName}..." + new Date()
            triggerScan(folder)
            processMultiBranchJobs(folder.fullName)
        } finally {
            println "[INFO] : Reinstating original NoTriggerOrganizationFolderProperty values for ${folder.fullName}... " + new Date()
            folder.getProperties().remove(tempProperty)
            folder.addProperty(propertyToReinstate)
        }
   }
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

void processMultiBranchJobs(String folderFullName) {
    println "[INFO MB] : Processing the Multibranch jobs from folder '${folderFullName}'..."
    getMultiBranchItemNames(folderFullName).each { multiBranchItemName ->
        def multiBranchItem = Jenkins.instance.getAllItems(WorkflowMultiBranchProject.class).find { it.fullName == multiBranchItemName}
        println "[INFO MB] : Scheduling : ${multiBranchItem.fullName}..." + new Date()
        triggerScan(multiBranchItem, "[INFO MB]")
        copyHashes(multiBranchItem.jobsDir)
    }
}

@NonCPS
def copyHashes(File parentDir) {
    parentDir.traverse(type: groovy.io.FileType.FILES, nameFilter: ~/scm-last-seen-revision-hash.xml/) { source ->
        def dest = new File(source.parent, 'scm-revision-hash.xml')
        dest << source.text
        println "[INFO MB] : copied ${source} -> ${dest}"
    }
}
