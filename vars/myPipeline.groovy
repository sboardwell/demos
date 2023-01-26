def call() {
  def myQuickConfig = readTrusted 'jetstream.yaml'
  echo "Processing the following config and running a special pipeline..."
  echo "${myQuickConfig}"
  boolean shouldTest = myQuickConfig.contains("test: true")
  boolean shouldBuild = myQuickConfig.contains("build: true")

  if(shouldTest) {
      stage('Test') {
            echoMe()
          echo "hello"
            echoMe('Fred Special')
      }
  }
  if(shouldBuild) {
      stage('Build') {
            echoMe()
          echo "hello"
            echoMe('Fred Special')
      }
  }
}
