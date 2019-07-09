pipeline {
    
    agent any
    
    tools {
        maven 'Maven 3.5.2'
    }

    stages {
        stage ('Initialize') {
            steps {  
                script {
                    def causes = currentBuild.getBuildCauses('com.cloudbees.jenkins.GitHubPushCause').shortDescription
                    if(!causes) {
                        causes = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause').shortDescription
                    }
                    currentBuild.displayName = "#${BUILD_NUMBER} ${GIT_BRANCH} ${causes}"
                    currentBuild.description = "${GIT_BRANCH}"
                }
      			sh '''
               		echo "PATH = ${PATH}"
               		echo "M2_HOME = ${M2_HOME}"
               	'''
            }
        }

        stage ('Test') {
            steps {
                sh 'mvn clean test' 
            }
        }

        stage ('Package') {
        	steps {
        		sh 'mvn clean package'
        	}
        }

        stage ('Verify') {
        	steps {
                wrap([$class: 'Xvfb', additionalOptions: '', assignedLabels: '', autoDisplayName: true, debug: true, displayNameOffset: 0, installationName: 'default', parallelBuild: true, screen: '']) {
                    sh 'mvn clean verify'
                }
        	}
        }
    }
    post {
        always {
            script {
            def commits = ""
            for(int i = 0; i < currentBuild.changeSets.size(); i++) {
                for(int j = 0; j < currentBuild.changeSets.getItems().length; j++) {
                    commits += "${currentBuild.changeSets.getAt(i).getItems()[j].getMsg()} \t From:${currentBuild.changeSets.getAt(i).getItems()[j].getAuthor()} \n"
                }
            }
            emailext body: "Result can be found at:'${currentBuild.absoluteUrl}' \nAffected commits: ${commits}", subject: "Unsuccessful Job '${currentBuild.description}'", to: 'c.orsinger@tu-braunschweig.de'
            
                currentBuild.description = ""
            }
        }
        //unsuccessful {
        // One or more steps need to be included within each condition's block.
        //}
    } 
}
