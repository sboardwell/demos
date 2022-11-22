// vars/buildMe.groovy
def call(String bucket, String bucketFolder, String deployUrl = "", Closure body) {
  def podYaml = libraryResource 'pod-templates/ubuntu.yaml'
  def label = "gsutil-${UUID.randomUUID().toString()}"
  if(bucketFolder) {
    bucketFolder = "/${bucketFolder}"
  } else {
   bucketFolder = "" 
  }
  podTemplate(name: 'gsutil', label: label, yaml: podYaml) {
    node(label) {
      body()
      container(name: 'shell') {
        sh "echo 'deploying something to -> gs://${bucket}${bucketFolder}'"
      }
    }
  }
}
