def call() {
  def myQuickConfig = readTrusted 'jetstream.yaml'
  echo "Processing the following config and running a special pipeline..."
  echo "${myQuickConfig}"

  pipeline {
    agent none
    stages {
      stage('Test') {
        steps {
            echoMe()
          echo "hello"
            echoMe('Fred Special')
        }
      }
    }
  }
}
