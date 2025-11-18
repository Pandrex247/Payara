#!groovy
// Jenkinsfile for building a PR and running a subset of tests against it
def pom
def DOMAIN_NAME
def payaraBuildNumber
pipeline {
    agent {
        label 'general-purpose'
    }
    environment {
        MP_METRICS_TAGS='tier=integration'
        MP_CONFIG_CACHE_DURATION=0
        JAVA_HOME = tool("zulu-21")
    }
    tools {
        jdk "zulu-21"
        maven "maven-3.6.3"
    }
    stages {
        stage('Report') {
            steps {
                script {
                    pom = readMavenPom file: 'pom.xml'
                    payaraBuildNumber = "PR${env.ghprbPullId}#${currentBuild.number}"
                    DOMAIN_NAME = "test-domain"
                    echo "Payara pom version is ${pom.version}"
                    echo "Build number is ${payaraBuildNumber}"
                    echo "Domain name is ${DOMAIN_NAME}"
              }
            }
        }
        stage('Build') {
            steps {
                echo '*#*#*#*#*#*#*#*#*#*#*#*#  Building SRC  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
<<<<<<< HEAD
                sh """mvn -B -V -ff -e clean install --strict-checksums -PQuickBuild,BuildEmbedded \
=======
                sh """mvn -B -V -ff -e clean install --strict-checksums -PQuickBuild,BuildEmbedded,jakarta-staging \
>>>>>>> Test-Disappearing
                    -Djavax.net.ssl.trustStore=${env.JAVA_HOME}/lib/security/cacerts \
                    -Djavax.xml.accessExternalSchema=all -Dbuild.number=${payaraBuildNumber} \
                    -Djavadoc.skip -Dsource.skip"""
                echo '*#*#*#*#*#*#*#*#*#*#*#*#    Built SRC   *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
            }
            post {
                success{
                    archiveArtifacts artifacts: 'appserver/distributions/payara/target/payara.zip', fingerprint: true
                    archiveArtifacts artifacts: 'appserver/extras/payara-micro/payara-micro-distribution/target/payara-micro.jar', fingerprint: true
                    stash name: 'payara-target', includes: 'appserver/distributions/payara/target/**', allowEmpty: true
                    stash name: 'payara-micro', includes: 'appserver/extras/payara-micro/payara-micro-distribution/target/**', allowEmpty: true
                    stash name: 'payara-embedded-all', includes: 'appserver/extras/embedded/all/target/**', allowEmpty: true
                    stash name: 'payara-embedded-web', includes: 'appserver/extras/embedded/web/target/**', allowEmpty: true
                    dir('/home/ubuntu/.m2/repository/'){
                        stash name: 'payara-m2-repository', includes: '**', allowEmpty: true
                    }
                }
                always {
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'appserver/distributions/payara/target/stage/payara7/glassfish/logs/server.log'
                }
            }
        }
        stage('Run Tests'){
            parallel {
                stage('Quicklook Tests') {
                    agent {
                        label 'general-purpose'
                    }
                    options {
                        retry(3)
                    }
                    steps {
                        setupDomain()

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh """rm  ~/test\\|sa.mv.db  || true"""
<<<<<<< HEAD
                        sh """mvn -B -V -ff -e clean test --strict-checksums -Pall \
                        -Dglassfish.home=\"${pwd()}/appserver/distributions/payara/target/stage/payara6/glassfish\" \
=======
                        sh """mvn -B -V -ff -e clean test --strict-checksums -Pall,jakarta-staging \
                        -Dglassfish.home=\"${pwd()}/appserver/distributions/payara/target/stage/payara7/glassfish\" \
>>>>>>> Test-Disappearing
                        -Djavax.net.ssl.trustStore=${env.JAVA_HOME}/lib/security/cacerts \
                        -Djavax.xml.accessExternalSchema=all \
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
                        -f appserver/tests/quicklook/pom.xml"""
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Ran test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                    }
                    post {
                        always {
                            junit 'appserver/tests/quicklook/test-output/QuickLookTests/*.xml'
                            stopDomain()
                        }
                        cleanup {
                            saveLogsAndCleanup 'quicklook-log.zip'
                        }
                    }
                }
<<<<<<< HEAD
                stage('Payara Samples Tests') {
=======
                 stage('Payara Samples Tests') {
                     agent {
                         label 'general-purpose'
                    }
                     options {
                         retry(3)
                    }
                     steps {
                         setupDomain()
                         echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                         sh """mvn -V -B -ff clean install --strict-checksums -Ppayara-server-remote,playwright \
                         -Djavax.net.ssl.trustStore=${env.JAVA_HOME}/lib/security/cacerts \
                         -Djavax.xml.accessExternalSchema=all \
                         -Dsurefire.rerunFailingTestsCount=2 \
                         -Dfailsafe.rerunFailingTestsCount=2 \
                         -f appserver/tests/payara-samples """
                         echo '*#*#*#*#*#*#*#*#*#*#*#*#  Ran test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                     }
                     post {
                         always {
                             processReportAndStopDomain()
                         }
                         cleanup {
                             saveLogsAndCleanup 'samples-log.zip'
                         }
                     }
                 }
                stage('MicroProfile Config TCK') {
>>>>>>> Test-Disappearing
                    agent {
                        label 'general-purpose'
                    }
                    options {
                        retry(3)
                    }
<<<<<<< HEAD
                    steps {
                        setupDomain()

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh """mvn -V -B -ff clean install --strict-checksums -Ppayara-server-remote,playwright \
                        -Djavax.net.ssl.trustStore=${env.JAVA_HOME}/lib/security/cacerts \
                        -Djavax.xml.accessExternalSchema=all \
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
                        -f appserver/tests/payara-samples """
=======
                    steps{
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checking out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        checkout changelog: false, poll: false, scm: [$class: 'GitSCM',
                            branches: [[name: "*/microprofile-6.1-Payara7"]],
                            userRemoteConfigs: [[url: "https://github.com/payara/MicroProfile-TCK-Runners.git"]]]
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checked out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'

                        setupDomain()
                        updatePomPayaraVersion("${pom.version}")

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh """mvn -B -V -ff -e clean verify --strict-checksums \
                        -Djavax.net.ssl.trustStore=${env.JAVA_HOME}/lib/security/cacerts \
                        -Djavax.xml.accessExternalSchema=all \
                        -Dpayara_domain=${DOMAIN_NAME} -Dpayara.home="${pwd()}/appserver/distributions/payara/target/stage/payara7" \
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
                        -Ppayara-server-remote,full \
                        -f MicroProfile-Config"""
>>>>>>> Test-Disappearing
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Ran test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                    }
                    post {
                        always {
                            processReportAndStopDomain()
                        }
                        cleanup {
<<<<<<< HEAD
                            saveLogsAndCleanup 'samples-log.zip'
                        }
                    }
                }

                // MicroProfile TCK runners
                stage('MicroProfile-Config TCK') {
=======
                            saveLogsAndCleanup 'mp-tck-log.zip'
                        }
                    }
                }
                stage('MicroProfile Fault Tolerance TCK') {
>>>>>>> Test-Disappearing
                    agent {
                        label 'general-purpose'
                    }
                    options {
                        retry(3)
                    }
                    steps{
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checking out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        checkout changelog: false, poll: false, scm: [$class: 'GitSCM',
<<<<<<< HEAD
                            branches: [[name: "*/microprofile-6.1"]],
=======
                            branches: [[name: "*/microprofile-6.1-Payara7"]],
>>>>>>> Test-Disappearing
                            userRemoteConfigs: [[url: "https://github.com/payara/MicroProfile-TCK-Runners.git"]]]
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checked out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'

                        setupDomain()
                        updatePomPayaraVersion("${pom.version}")

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh """mvn -B -V -ff -e clean verify --strict-checksums \
                        -Djavax.net.ssl.trustStore=${env.JAVA_HOME}/lib/security/cacerts \
                        -Djavax.xml.accessExternalSchema=all \
<<<<<<< HEAD
                        -Dpayara_domain=${DOMAIN_NAME} -Dpayara.home="${pwd()}/appserver/distributions/payara/target/stage/payara6" \
                        -Ppayara-server-remote,full \
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
                        -f MicroProfile-Config"""
=======
                        -Dpayara_domain=${DOMAIN_NAME} -Dpayara.home="${pwd()}/appserver/distributions/payara/target/stage/payara7" \
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
                        -Ppayara-server-remote,full \
                        -f MicroProfile-Fault-Tolerance"""
>>>>>>> Test-Disappearing
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Ran test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                    }
                    post {
                        always {
                            processReportAndStopDomain()
                        }
                        cleanup {
                            saveLogsAndCleanup 'mp-tck-log.zip'
                        }
                    }
                }
<<<<<<< HEAD

                stage('MicroProfile-Fault-Tolerance TCK') {
=======
                stage('MicroProfile Health TCK') {
>>>>>>> Test-Disappearing
                    agent {
                        label 'general-purpose'
                    }
                    options {
                        retry(3)
                    }
                    steps{
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checking out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        checkout changelog: false, poll: false, scm: [$class: 'GitSCM',
<<<<<<< HEAD
                            branches: [[name: "*/microprofile-6.1"]],
=======
                            branches: [[name: "*/microprofile-6.1-Payara7"]],
>>>>>>> Test-Disappearing
                            userRemoteConfigs: [[url: "https://github.com/payara/MicroProfile-TCK-Runners.git"]]]
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checked out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'

                        setupDomain()
                        updatePomPayaraVersion("${pom.version}")

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh """mvn -B -V -ff -e clean verify --strict-checksums \
                        -Djavax.net.ssl.trustStore=${env.JAVA_HOME}/lib/security/cacerts \
                        -Djavax.xml.accessExternalSchema=all \
<<<<<<< HEAD
                        -Dpayara_domain=${DOMAIN_NAME} -Dpayara.home="${pwd()}/appserver/distributions/payara/target/stage/payara6" \
                        -Ppayara-server-remote,full \
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
                        -f MicroProfile-Fault-Tolerance"""
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Ran test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                    }
                    post {
                        always {
                            processReportAndStopDomain()
                        }
                        cleanup {
                            saveLogsAndCleanup 'mp-tck-log.zip'
                        }
                    }
                }

                stage('MicroProfile-Health TCK') {
                    agent {
                        label 'general-purpose'
                    }
                    options {
                        retry(3)
                    }
                    steps{
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checking out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        checkout changelog: false, poll: false, scm: [$class: 'GitSCM',
                            branches: [[name: "*/microprofile-6.1"]],
                            userRemoteConfigs: [[url: "https://github.com/payara/MicroProfile-TCK-Runners.git"]]]
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checked out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'

                        setupDomain()
                        updatePomPayaraVersion("${pom.version}")

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh """mvn -B -V -ff -e clean verify --strict-checksums \
                        -Djavax.net.ssl.trustStore=${env.JAVA_HOME}/lib/security/cacerts \
                        -Djavax.xml.accessExternalSchema=all \
                        -Dpayara_domain=${DOMAIN_NAME} -Dpayara.home="${pwd()}/appserver/distributions/payara/target/stage/payara6" \
=======
                        -Dpayara_domain=${DOMAIN_NAME} -Dpayara.home="${pwd()}/appserver/distributions/payara/target/stage/payara7" \
>>>>>>> Test-Disappearing
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
                        -Ppayara-server-remote,full \
                        -f MicroProfile-Health"""
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Ran test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                    }
                    post {
                        always {
                            processReportAndStopDomain()
                        }
                        cleanup {
                            saveLogsAndCleanup 'mp-tck-log.zip'
                        }
                    }
                }
<<<<<<< HEAD


                stage('MicroProfile-JWT-Auth TCK') {
=======
                stage('MicroProfile JWT Auth TCK') {
>>>>>>> Test-Disappearing
                    agent {
                        label 'general-purpose'
                    }
                    options {
                        retry(3)
                    }
                    steps{
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checking out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        checkout changelog: false, poll: false, scm: [$class: 'GitSCM',
<<<<<<< HEAD
                            branches: [[name: "*/microprofile-6.1"]],
=======
                            branches: [[name: "*/microprofile-6.1-Payara7"]],
>>>>>>> Test-Disappearing
                            userRemoteConfigs: [[url: "https://github.com/payara/MicroProfile-TCK-Runners.git"]]]
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checked out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'

                        setupDomain()
                        updatePomPayaraVersion("${pom.version}")

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh """mvn -B -V -ff -e clean verify --strict-checksums \
                        -Djavax.net.ssl.trustStore=${env.JAVA_HOME}/lib/security/cacerts \
                        -Djavax.xml.accessExternalSchema=all \
<<<<<<< HEAD
                        -Dpayara_domain=${DOMAIN_NAME} -Dpayara.home="${pwd()}/appserver/distributions/payara/target/stage/payara6" \
                        -Ppayara-server-remote,full \
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
=======
                        -Dpayara_domain=${DOMAIN_NAME} -Dpayara.home="${pwd()}/appserver/distributions/payara/target/stage/payara7" \
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
                        -Ppayara-server-remote,full \
>>>>>>> Test-Disappearing
                        -f MicroProfile-JWT-Auth"""
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Ran test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                    }
                    post {
                        always {
                            processReportAndStopDomain()
                        }
                        cleanup {
                            saveLogsAndCleanup 'mp-tck-log.zip'
                        }
                    }
                }
<<<<<<< HEAD

                stage('MicroProfile-Metrics TCK') {
=======
                stage('MicroProfile Metrics TCK') {
>>>>>>> Test-Disappearing
                    agent {
                        label 'general-purpose'
                    }
                    options {
                        retry(3)
                    }
                    steps{
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checking out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        checkout changelog: false, poll: false, scm: [$class: 'GitSCM',
<<<<<<< HEAD
                            branches: [[name: "*/microprofile-6.1"]],
=======
                            branches: [[name: "*/microprofile-6.1-Payara7"]],
>>>>>>> Test-Disappearing
                            userRemoteConfigs: [[url: "https://github.com/payara/MicroProfile-TCK-Runners.git"]]]
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checked out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'

                        setupDomain()
                        updatePomPayaraVersion("${pom.version}")

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh """mvn -B -V -ff -e clean verify --strict-checksums \
                        -Djavax.net.ssl.trustStore=${env.JAVA_HOME}/lib/security/cacerts \
                        -Djavax.xml.accessExternalSchema=all \
<<<<<<< HEAD
                        -Dpayara_domain=${DOMAIN_NAME} -Dpayara.home="${pwd()}/appserver/distributions/payara/target/stage/payara6" \
                        -Ppayara-server-remote,full \
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
=======
                        -Dpayara_domain=${DOMAIN_NAME} -Dpayara.home="${pwd()}/appserver/distributions/payara/target/stage/payara7" \
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
                        -Ppayara-server-remote,full \
>>>>>>> Test-Disappearing
                        -f MicroProfile-Metrics"""
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Ran test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                    }
                    post {
                        always {
                            processReportAndStopDomain()
                        }
                        cleanup {
                            saveLogsAndCleanup 'mp-tck-log.zip'
                        }
                    }
                }
<<<<<<< HEAD

               stage('MicroProfile-OpenAPI TCK') {
=======
                stage('MicroProfile OpenAPI TCK') {
>>>>>>> Test-Disappearing
                    agent {
                        label 'general-purpose'
                    }
                    options {
                        retry(3)
                    }
                    steps{
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checking out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        checkout changelog: false, poll: false, scm: [$class: 'GitSCM',
<<<<<<< HEAD
                            branches: [[name: "*/microprofile-6.1"]],
=======
                            branches: [[name: "*/microprofile-6.1-Payara7"]],
>>>>>>> Test-Disappearing
                            userRemoteConfigs: [[url: "https://github.com/payara/MicroProfile-TCK-Runners.git"]]]
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checked out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'

                        setupDomain()
                        updatePomPayaraVersion("${pom.version}")

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh """mvn -B -V -ff -e clean verify --strict-checksums \
                        -Djavax.net.ssl.trustStore=${env.JAVA_HOME}/lib/security/cacerts \
                        -Djavax.xml.accessExternalSchema=all \
<<<<<<< HEAD
                        -Dpayara_domain=${DOMAIN_NAME} -Dpayara.home="${pwd()}/appserver/distributions/payara/target/stage/payara6" \
                        -Ppayara-server-remote,full \
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
=======
                        -Dpayara_domain=${DOMAIN_NAME} -Dpayara.home="${pwd()}/appserver/distributions/payara/target/stage/payara7" \
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
                        -Ppayara-server-remote,full \
>>>>>>> Test-Disappearing
                        -f MicroProfile-OpenAPI"""
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Ran test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                    }
                    post {
                        always {
                            processReportAndStopDomain()
                        }
                        cleanup {
                            saveLogsAndCleanup 'mp-tck-log.zip'
                        }
                    }
                }
<<<<<<< HEAD

                stage('MicroProfile-OpenTelemetry-Tracing TCK') {
=======
                stage('MicroProfile OpenTelemetry Tracing TCK') {
>>>>>>> Test-Disappearing
                    agent {
                        label 'general-purpose'
                    }
                    options {
                        retry(3)
                    }
                    steps{
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checking out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        checkout changelog: false, poll: false, scm: [$class: 'GitSCM',
<<<<<<< HEAD
                            branches: [[name: "*/microprofile-6.1"]],
=======
                            branches: [[name: "*/microprofile-6.1-Payara7"]],
>>>>>>> Test-Disappearing
                            userRemoteConfigs: [[url: "https://github.com/payara/MicroProfile-TCK-Runners.git"]]]
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checked out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'

                        setupDomain()
                        updatePomPayaraVersion("${pom.version}")

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh """mvn -B -V -ff -e clean verify --strict-checksums \
                        -Djavax.net.ssl.trustStore=${env.JAVA_HOME}/lib/security/cacerts \
                        -Djavax.xml.accessExternalSchema=all \
<<<<<<< HEAD
                        -Dpayara_domain=${DOMAIN_NAME} -Dpayara.home="${pwd()}/appserver/distributions/payara/target/stage/payara6" \
=======
                        -Dpayara_domain=${DOMAIN_NAME} -Dpayara.home="${pwd()}/appserver/distributions/payara/target/stage/payara7" \
>>>>>>> Test-Disappearing
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
                        -Ppayara-server-remote,full \
                        -f MicroProfile-OpenTelemetry-Tracing"""
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Ran test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                    }
                    post {
                        always {
                            processReportAndStopDomain()
                        }
                        cleanup {
                            saveLogsAndCleanup 'mp-tck-log.zip'
                        }
                    }
                }
<<<<<<< HEAD

                stage('MicroProfile-OpenTracing TCK') {
=======
                stage('MicroProfile OpenTracing TCK') {
>>>>>>> Test-Disappearing
                    agent {
                        label 'general-purpose'
                    }
                    options {
                        retry(3)
                    }
                    steps{
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checking out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        checkout changelog: false, poll: false, scm: [$class: 'GitSCM',
<<<<<<< HEAD
                            branches: [[name: "*/microprofile-6.1"]],
=======
                            branches: [[name: "*/microprofile-6.1-Payara7"]],
>>>>>>> Test-Disappearing
                            userRemoteConfigs: [[url: "https://github.com/payara/MicroProfile-TCK-Runners.git"]]]
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checked out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'

                        setupDomain()
                        updatePomPayaraVersion("${pom.version}")

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh """mvn -B -V -ff -e clean verify --strict-checksums \
                        -Djavax.net.ssl.trustStore=${env.JAVA_HOME}/lib/security/cacerts \
                        -Djavax.xml.accessExternalSchema=all \
<<<<<<< HEAD
                        -Dpayara_domain=${DOMAIN_NAME} -Dpayara.home="${pwd()}/appserver/distributions/payara/target/stage/payara6" \
=======
                        -Dpayara_domain=${DOMAIN_NAME} -Dpayara.home="${pwd()}/appserver/distributions/payara/target/stage/payara7" \
>>>>>>> Test-Disappearing
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
                        -Ppayara-server-remote,full \
                        -f MicroProfile-OpenTracing"""
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Ran test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                    }
                    post {
                        always {
                            processReportAndStopDomain()
                        }
                        cleanup {
                            saveLogsAndCleanup 'mp-tck-log.zip'
                        }
                    }
                }
<<<<<<< HEAD

              stage('MicroProfile-Rest-Client TCK') {
=======
                stage('MicroProfile REST Client TCK') {
>>>>>>> Test-Disappearing
                    agent {
                        label 'general-purpose'
                    }
                    options {
                        retry(3)
                    }
                    steps{
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checking out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        checkout changelog: false, poll: false, scm: [$class: 'GitSCM',
<<<<<<< HEAD
                            branches: [[name: "*/microprofile-6.1"]],
=======
                            branches: [[name: "*/microprofile-6.1-Payara7"]],
>>>>>>> Test-Disappearing
                            userRemoteConfigs: [[url: "https://github.com/payara/MicroProfile-TCK-Runners.git"]]]
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checked out MP TCK Runners  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'

                        setupDomain()
                        updatePomPayaraVersion("${pom.version}")

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh """mvn -B -V -ff -e clean verify --strict-checksums \
                        -Djavax.net.ssl.trustStore=${env.JAVA_HOME}/lib/security/cacerts \
                        -Djavax.xml.accessExternalSchema=all \
<<<<<<< HEAD
                        -Dpayara_domain=${DOMAIN_NAME} -Dpayara.home="${pwd()}/appserver/distributions/payara/target/stage/payara6" \
                        -Ppayara-server-remote,full \
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
=======
                        -Dpayara_domain=${DOMAIN_NAME} -Dpayara.home="${pwd()}/appserver/distributions/payara/target/stage/payara7" \
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
                        -Ppayara-server-remote,full \
>>>>>>> Test-Disappearing
                        -f MicroProfile-Rest-Client"""
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Ran test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                    }
                    post {
                        always {
                            processReportAndStopDomain()
                        }
                        cleanup {
                            saveLogsAndCleanup 'mp-tck-log.zip'
                        }
                    }
                }
<<<<<<< HEAD

=======
>>>>>>> Test-Disappearing
                stage('EE8 Tests') {
                    agent {
                        label 'general-purpose'
                    }
                    options {
                        retry(3)
                    }
                    steps{
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checking out EE8 tests  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        checkout changelog: false, poll: false, scm: [$class: 'GitSCM',
<<<<<<< HEAD
                            branches: [[name: "*/Payara6"]],
=======
                            branches: [[name: "*/Payara7"]],
>>>>>>> Test-Disappearing
                            userRemoteConfigs: [[url: "https://github.com/payara/patched-src-javaee8-samples.git"]]]
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checked out EE8 tests  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'

                        setupDomain()
                        updatePomPayaraVersion("${pom.version}")

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh "mvn -B -V -ff -e clean install --strict-checksums -Dsurefire.useFile=false \
                        -Djavax.net.ssl.trustStore=${env.JAVA_HOME}/lib/security/cacerts \
                        -Djavax.xml.accessExternalSchema=all \
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
                        -Ppayara-server-remote,stable"
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Ran test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                    }
                    post {
                        always {
                            processReportAndStopDomain()
                        }
                        cleanup {
                            saveLogsAndCleanup 'ee8-samples-log.zip'
                        }
                    }
                }
                stage('CargoTracker Tests') {
                    agent {
                        label 'general-purpose'
                    }
                    options {
                        retry(3)
                    }
                    steps{
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checking out cargoTracker tests  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        checkout changelog: false, poll: false, scm: [$class: 'GitSCM',
<<<<<<< HEAD
                            branches: [[name: "*/Payara6"]],
=======
                            branches: [[name: "*/Payara7"]],
>>>>>>> Test-Disappearing
                            userRemoteConfigs: [[url: "https://github.com/payara/cargoTracker.git"]]]
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checked out cargoTracker tests  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'

                        setupDomain()
                        updatePomPayaraVersion("${pom.version}")

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Cleaning CargoTracker Database in /tmp  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh "rm -rf /tmp/cargo*"

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh  """mvn -B -V -ff -e clean verify --strict-checksums -Dsurefire.useFile=false \
                         -Djavax.net.ssl.trustStore=${env.JAVA_HOME}/lib/security/cacerts \
                         -Djavax.xml.accessExternalSchema=all \
                         -Dsurefire.rerunFailingTestsCount=2 \
                         -Dfailsafe.rerunFailingTestsCount=2 \
                         -Ppayara-server-remote -DtrimStackTrace=false"""
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Ran test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                    }
                    post {
                        always {
                            processReportAndStopDomain()
                        }
                        cleanup {
                            saveLogsAndCleanup 'cargotracker-log.zip'
                        }
                    }
                }
                stage('EE7 Tests') {
                    agent {
                        label 'general-purpose'
                    }
                    options {
                        retry(3)
                    }
                    steps{
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checking out EE7 tests  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        checkout changelog: false, poll: false, scm: [$class: 'GitSCM',
<<<<<<< HEAD
                            branches: [[name: "*/Payara6"]],
=======
                            branches: [[name: "*/Payara7"]],
>>>>>>> Test-Disappearing
                            userRemoteConfigs: [[url: "https://github.com/payara/patched-src-javaee7-samples.git"]]]
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Checked out EE7 tests  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'

                        setupDomain()
                        updatePomPayaraVersion("${pom.version}")

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh """mvn -B -V -ff -e clean install --strict-checksums \
                        -Djavax.net.ssl.trustStore=${env.JAVA_HOME}/lib/security/cacerts \
                        -Djavax.xml.accessExternalSchema=all \
                        -Dpayara_domain=${DOMAIN_NAME} \
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
<<<<<<< HEAD
                        -Ppayara-server-remote,stable,payara6"""
=======
                        -Ppayara-server-remote,stable"""
>>>>>>> Test-Disappearing
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Ran test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                    }
                    post {
                        always {
                            processReportAndStopDomain()
                        }
                        cleanup {
                            saveLogsAndCleanup 'ee7-samples-log.zip'
                        }
                    }
                }
                stage('Payara Functional Tests') {
                    agent {
                        label 'general-purpose'
                    }
                    options {
                        retry(3)
                    }
                    steps {
                        setupM2RepositoryOnly()
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Unstash Micro and Embedded *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        unstash name: 'payara-micro'
                        unstash name: 'payara-embedded-all'
                        unstash name: 'payara-embedded-web'

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Building dependencies  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh """mvn -V -B -ff clean install --strict-checksums \
                        -Djavax.net.ssl.trustStore=${env.JAVA_HOME}/lib/security/cacerts \
                        -Djavax.xml.accessExternalSchema=all \
                        -DskipTests \
                        -f appserver/tests/payara-samples -pl fish.payara.samples:payara-samples \
                        -pl fish.payara.samples:samples-test-utils -pl fish.payara.samples:test-domain-setup \
                        -pl fish.payara.samples:payara-samples-profiled-tests"""

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running test with Payara Micro  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh """mvn -V -B -ff clean install --strict-checksums -Ppayara-micro-managed,install-deps \
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
                        -f appserver/tests/functional/payara-micro """

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running test with Payara Embedded  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        sh """mvn -V -B -ff clean verify --strict-checksums -PFullProfile \
<<<<<<< HEAD
=======
                        -Dversion=${pom.version} \
>>>>>>> Test-Disappearing
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
                        -f appserver/tests/functional/embeddedtest """

                        sh """mvn -V -B -ff clean verify --strict-checksums -PWebProfile \
<<<<<<< HEAD
=======
                        -Dversion=${pom.version} \
>>>>>>> Test-Disappearing
                        -Dsurefire.rerunFailingTestsCount=2 \
                        -Dfailsafe.rerunFailingTestsCount=2 \
                        -f appserver/tests/functional/embeddedtest """

                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Running asadmin tests  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                        setupDomain()
                        sh """python3 appserver/tests/functional/asadmin/run_all_tests.py \
<<<<<<< HEAD
                        --asadmin ${pwd()}/appserver/distributions/payara/target/stage/payara6/bin/asadmin"""
=======
                        --asadmin ${pwd()}/appserver/distributions/payara/target/stage/payara7/bin/asadmin"""
>>>>>>> Test-Disappearing
                        echo '*#*#*#*#*#*#*#*#*#*#*#*#  Ran test  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
                    }
                    post {
                        always {
                            stopDomain()
                        }
                        cleanup {
                            processReport()
                            saveLogsAndCleanup 'asadmin-log.zip'
                        }
                    }
                }
            }
        }
    }
}

void makeDomain() {
    script{
        ASADMIN = "./appserver/distributions/payara/target/stage/payara7/bin/asadmin"
        DOMAIN_NAME = "test-domain"
    }
    sh "${ASADMIN} create-domain --nopassword ${DOMAIN_NAME}"
}

void setupDomain() {
    echo '*#*#*#*#*#*#*#*#*#*#*#*#  Unstash distributions  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
    unstash name: 'payara-target'
    echo '*#*#*#*#*#*#*#*#*#*#*#*#  Unstash maven repository  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
    dir('/home/ubuntu/.m2/repository/'){
        unstash name: 'payara-m2-repository'
    }
    echo '*#*#*#*#*#*#*#*#*#*#*#*#  Setting up tests  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
    makeDomain()
    sh "${ASADMIN} start-domain ${DOMAIN_NAME}"
    sh "${ASADMIN} start-database || true"
}

void setupM2RepositoryOnly() {
    echo '*#*#*#*#*#*#*#*#*#*#*#*#  Unstash maven repository  *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#'
    dir('/home/ubuntu/.m2/repository/'){
        unstash name: 'payara-m2-repository'
    }
}

void processReportAndStopDomain() {
    junit '**/target/*-reports/*.xml'
    sh "${ASADMIN} stop-domain ${DOMAIN_NAME}"
    sh "${ASADMIN} stop-database || true"
}

void processReport() {
    junit '**/target/*-reports/*.xml'
}

void stopDomain() {
    sh "${ASADMIN} stop-domain ${DOMAIN_NAME}"
    sh "${ASADMIN} stop-database || true"
}

void saveLogsAndCleanup(String logArchiveName) {
    zip archive: true, dir: "appserver/distributions/payara/target/stage/payara7/glassfish/domains/${DOMAIN_NAME}/logs", glob: 'server.*', zipFile: logArchiveName
    echo 'tidying up after tests: '
    sh "rm -f -v *.zip"
    sh "${ASADMIN} delete-domain ${DOMAIN_NAME}"
}

void updatePomPayaraVersion(String payaraVersion) {
    echo '*#*#*#*#*#*#*#*#*#*#*#*#  Updating pom.xml payara.version property for Shrinkwrap resolver  *#*#*#*#*#*#*#*#*#*#*#*#'
    sh script: "sed -i \"s/payara\\.version>.*<\\/payara\\.version>/payara\\.version>${payaraVersion}<\\/payara\\.version>/g\" pom.xml", label: "Update pom.xml payara.version property"
}
