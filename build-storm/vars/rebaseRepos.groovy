import jenkins.branch.*
import org.jenkinsci.plugins.workflow.multibranch.*

def call() {
    def folderItems = Jenkins.instance.getAllItems(OrganizationFolder.class)
    def folderItemNames = []
    folderItems.each {
        folderItemNames << it.fullName
    }
    folderItemNames.each {
        processFolder(it)
    }
}

//@NonCPS
def processFolder(String folderFullName) {
    def folder = Jenkins.instance.getAllItems(OrganizationFolder.class).find { it.fullName == folderFullName}
    println "[INFO] : Checking ${folder.fullName}... "
    NoTriggerOrganizationFolderProperty property = folder.getProperties().get(NoTriggerOrganizationFolderProperty.class)
    String actualRegex = property.getBranches()
    //NoTriggerOrganizationFolderProperty propertyAfter = new NoTriggerOrganizationFolderProperty(actualRegex.replace('NEEDS_REBASING_PREFIX', ''))
    //propertyAfter.setStrategy(property.strategy)
    // check to see the no trigger property has been prefixed or name passed explicitly
    //if (actualRegex.startsWith('NEEDS_REBASING_PREFIX') || folder.fullName == params?.ORG_FOLDER_NAME) {
        String randomRegex = 'bla' + System.currentTimeMillis()
        println "[INFO] : Current regex : ${actualRegex}"
        println "[INFO] : Temp regex : ${randomRegex}"
        //println "[INFO] : Final regex : ${propertyAfter.getTriggeredBranchesRegex()}"
    NoTriggerOrganizationFolderProperty newProperty = new NoTriggerOrganizationFolderProperty(randomRegex)
        try {
            folder.getProperties().remove(property)
            //property = new NoTriggerOrganizationFolderProperty(randomRegex)
            folder.addProperty(newProperty)
            println "[INFO] : Scheduling : ${folder.fullName}..." + new Date()
            def build = folder.scheduleBuild2(0).getFuture().get()
            println "[INFO] : ${build.result} - duration: ${build.durationString}"
            processMultiBranchJobs(folder)
        } finally {
            println "[INFO] : Reinstating original NoTriggerOrganizationFolderProperty values for ${folder.fullName}..." + new Date()
            folder.getProperties().remove(newProperty)
            folder.addProperty(property)
        }
   // }
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

//@NonCPS
void processMultiBranchJobs(def folder) {
    println "[INFO MB] : Processing the Multibranch jobs..."
    def folderItems = Jenkins.instance.getAllItems(WorkflowMultiBranchProject.class)
    def folderItemNames = []
    folderItems.each {
        if (it.fullName.startsWith(folder.fullName)) {
            folderItemNames << it.fullName
        }
    }
    folderItemNames.each { folderItemName ->
        def folderItem = Jenkins.instance.getAllItems(WorkflowMultiBranchProject.class).find { it.fullName == folderItemName}
        println "[INFO MB] : Scheduling : ${folderItem.fullName}..." + new Date()
        def build = folderItem.scheduleBuild2(0).getFuture().get()
        println "[INFO MB] : ${build.result} - duration: ${build.durationString}"
        copyHashes(folderItem.jobsDir)
    }
}

//@NonCPS
def copyHashes(File parentDir) {
    parentDir.traverse(type: groovy.io.FileType.FILES, nameFilter: ~/scm-last-seen-revision-hash.xml/) { source ->
        def dest = new File(source.parent, 'scm-revision-hash.xml')
        dest << source.text
        println "[INFO MB] : copied ${source} -> ${dest}"
    }
}
