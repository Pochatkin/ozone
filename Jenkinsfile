/*
 * this is default sample Jenkinsfile that will be placed in created repository if INIT_PIPELINE is true
 */

@Library("sdp-pipelines@develop") _
import ru.sber.Variables

def updateParams = false
pipeline {
    agent { node { label "sdp" } }
    options {
        ansiColor("xterm")
        // convention is here: https://sbtatlas.sigma.sbrf.ru/wiki/pages/viewpage.action?pageId=75932050
        buildDiscarder logRotator(
            artifactDaysToKeepStr: "7",
            artifactNumToKeepStr: "10",
            daysToKeepStr: "7",
            numToKeepStr: "50"
        )
        disableConcurrentBuilds()
        durabilityHint "PERFORMANCE_OPTIMIZED"
        skipStagesAfterUnstable()
    }
    parameters {
        booleanParam(
            name: "UPDATE_PARAMS",
            defaultValue: false,
            description: "Dry run, no changes, like 2 at https://dev.to/pencillr/jenkins-pipelines-and-their-dirty-secrets-2"
        )
    }
    stages {
        stage("Stage: Update Params") {
            when {
                anyOf {
                    equals expected: true, actual: params.UPDATE_PARAMS
                    equals expected: 1, actual: currentBuild.number
                }
            }
            steps {
                script {
                    sdpStageUpdateParams()
                }
            }
        }
    }
    post {
        always {
            script {
                sdpStagePostNotify()
            }
        }
    }
}
