@Library("sdp-pipelines@develop")
@Library('ru.sbrf.devsecops@master')

import ru.sber.Variables

import javax.lang.model.element.VariableElement

def artifactVersion = "1.2.0"
Variables.componentVersion = artifactVersion

def componentName = "ozone"
Variables.componentName = componentName

def dockerRegistry = "tkles-fauna0080.vm.esrt.cloud.sbrf.ru:5000"

properties([
  parameters([
    booleanParam(
            name: "SastCheckmarxOnly",
            defaultValue: false,
            description: "Run SAST Chekmarks without build"
    ),
    booleanParam(
            name: "updateParams",
            defaultValue: false,
            description: "Dry run, no changes, like 2 at https://dev.to/pencillr/jenkins-pipelines-and-their-dirty-secrets-2"
    )
   ])
])


pipeline {
    agent { node { label "linux" } }
    triggers {
        GenericTrigger (
            causeString: "Triggered by PR merge",
            token: "${env.JOB_NAME.replace('/', '_').replace('%2F', '_')}"
        )
    }
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
    stages {
        stage("Update params") {
            when {
                anyOf {
                    equals expected: true, actual: params.updateParams
                    equals expected: 1, actual: currentBuild.number
                }
            }
            steps {
                script {
                    sdpStageUpdateParams()
                }
            }
        }
        stage("SAST-Checkmarx") {
            when {
                allOf {
                    equals expected: false, actual: params.updateParams
                    equals expected: true, actual: params.SastCheckmarxOnly
                    }
                }
            steps {
                script {
                    sdpStageSastCheckmarks()
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
