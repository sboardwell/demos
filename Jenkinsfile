pipeline {
    agent any
    options {
        skipDefaultCheckout()
    }
    stages {
        stage('Build') { 
            steps {
                checkout scm 
            }
        }
    }
}
