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
            def buildFuture = folder.scheduleBuild2(0).getFuture()
            while (!buildFuture.isDone()) {
                println "Waiting for the scan of '${folder.fullName}' to finish..."
                sleep 10
            }
            def build = buildFuture.get()
            println "[INFO] : ${build.result} - duration: ${build.durationString}"
            processMultiBranchJobs(folder.fullName)
        } finally {
            println "[INFO] : Reinstating original NoTriggerOrganizationFolderProperty values for ${folder.fullName}... " + new Date()
            folder.getProperties().remove(tempProperty)
            folder.addProperty(propertyToReinstate)
        }
   }
}

//@NonCPS
//def processFolder(folder) {
//    println "[INFO] : Checking ${folder.fullName}... "
//    NoTriggerOrganizationFolderProperty property = folder.getProperties().get(NoTriggerOrganizationFolderProperty.class)
//    String actualRegex = property.getBranches()
//    //NoTriggerOrganizationFolderProperty propertyAfter = new NoTriggerOrganizationFolderProperty(actualRegex.replace('NEEDS_REBASING_PREFIX', ''))
//    //propertyAfter.setStrategy(property.getStrategy())
//    // check to see the no trigger property has been prefixed or name passed explicitly
//    //if (actualRegex.startsWith('NEEDS_REBASING_PREFIX') || folder.fullName == params?.ORG_FOLDER_NAME) {
//        String randomRegex = 'bla' + System.currentTimeMillis()
//        println "[INFO] : Current regex : ${actualRegex}"
//        println "[INFO] : Temp regex : ${randomRegex}"
//        //println "[INFO] : Final regex : ${propertyAfter.getBranches()}"
//    NoTriggerOrganizationFolderProperty newProperty = new NoTriggerOrganizationFolderProperty(randomRegex)
//        try {
//            folder.getProperties().remove(property)
//            folder.addProperty(newProperty)
//            println "[INFO] : Scheduling : ${folder.fullName}..." + new Date()
//            def build = folder.scheduleBuild2(0).getFuture().get()
//            println "[INFO] : ${build.result} - duration: ${build.durationString}"
//            processMultiBranchJobs(folder)
//        } finally {
//            println "[INFO] : Reinstating original NoTriggerOrganizationFolderProperty values for ${folder.fullName}..." + new Date()
//            folder.getProperties().remove(newProperty)
//            folder.addProperty(property)
//        }
//    //}
//}

//@NonCPS
//void processMultiBranchJobs(def folder) {
//    println "[INFO MB] : Processing the Multibranch jobs..."
//    def folderItems = Jenkins.instance.getAllItems(WorkflowMultiBranchProject.class)
//    folderItems.each {
//        if (it.fullName.startsWith(folder.fullName)) {
//            println "[INFO MB] : Scheduling : ${it.fullName}..." + new Date()
//            def build = it.scheduleBuild2(0).getFuture().get()
//            println "[INFO MB] : ${build.result} - duration: ${build.durationString}"
//            copyHashes(it.jobsDir)
//        }
//    }
//}
//
//@NonCPS
//def copyHashes(File parentDir) {
//    parentDir.traverse(type: groovy.io.FileType.FILES, nameFilter: ~/scm-last-seen-revision-hash.xml/) { source ->
//        def dest = new File(source.parent, 'scm-revision-hash.xml')
//        dest << source.text
//        println "[INFO MB] : copied ${source} -> ${dest}"
//    }
//}

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
        def buildFuture = multiBranchItem.scheduleBuild2(0).getFuture()
        while (!buildFuture.isDone()) {
            println "Waiting for the scan of '${multiBranchItem.fullName}' to finish..."
            sleep 10
        }
        def build = buildFuture.get()
        println "[INFO MB] : ${build.result} - duration: ${build.durationString}"
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
