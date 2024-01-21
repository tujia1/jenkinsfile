#!groovy

@Library('jenkinslibrary@master') _


def tools = new org.devops.tools()
def build = new org.devops.build()
def gitlab = new org.devops.gitlab()
def toemail = new org.devops.toemail()
def sonar = new org.devops.sonarqube()
def sonarapi = new org.devops.sonarapi()
def talk = new org.devops.dingtalk()
def runOpts = "GitlabPush"

String buildType = "${env.buildType}"
String buildShell = "${env.buildShell}"
String srcUrl = "${env.srcUrl}"
//String branchName = "${env.branchName}"

pipeline{
    agent{
        kubernetes{
            inheritFrom 'jenkins-slave'
//             yaml '''
// ---
// kind: Pod
// apiVersion: v1
// metadata:
//   labels:
//     k8s-app: jenkinsagent
//   name: jenkinsagent
//   namespace: devops
// spec:
// containers:
//   - name: jenkinsagent
//     image: jenkins/inbound-agent:4.3-4
//     imagePullPolicy: IfNotPresent
//     resources:
//       limits:
//         cpu: 1000m
//         memory: 2Gi
//       requests:
//         cpu: 500m
//         memory: 512Mi
//     volumeMounts:
//       - name: jenkinsagent-workdir
//         mountPath: /home/jenkins/workspace
//       - name: buildtools
//         mountPath: /home/jenkins/buildtools
//     env:
//       - name: JENKINS_AGENT_WORKDIR
//         value: /home/jenkins/workspace
// volumes:
//   - name: jenkinsagent-workdir
//     persistentVolumeClaim:
//       claimName: slave-pvc
//   - name: buildtools
//     persistentVolumeClaim:
//       claimName: buildtools-pvc
// '''
        }
    }
    stages{
        stage("下载代码"){
          steps{
            script{
                //判断分支增加输出构建信息（构建者、构建分支）
                if ("${runOpts}" == "GitlabPush"){
                    // branchName = branch - "refs/heads/"
                    branchName = branch.substring("refs/heads/".length())
                    println("${branchName}")
                    currentBuild.description = "Trigger by ${userName} ${branch}"
                    gitlab.ChangeCommitStatus(projectId,commitSha,"running")

                } else {
                    userEmail = "######@qq.com"
                }
                ACTION = "${branchName}"    
                tools.PrintMes('开始下载代码','green')
                checkout([$class: 'GitSCM', branches: [[name: "${branchName}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'gitlab-root', url: "${srcUrl}"]]])
            }
          }
        }
        stage("mvn打包"){
          when {
            equals expected: 'release', 
            actual: ACTION
          }
          steps{
            script{
              tools.PrintMes('开始执行编译打包操作.......','green')
              // mvn -q 减少不必要的输出
              sh "../buildtools/maven/bin/mvn clean package -q"
            }
          }
        }
        stage("docker build"){
          steps{
            script{
              tools.PrintMes('开始docker构建镜像。。。。。','green')
              jarPackage = sh(script: 'cd target && ls *.jar', returnStdout: true).trim()
              println("${jarPackage}")
              // 将本地的docker执行文件和套接字符文件挂载到工作目录
              //动态节点挂载本地docker命名，需修改套接字符文件权限 chmod 666 /run/docker.sock 远程使用命令 docker -H unix:///docker.sock + 命令
              //sh "/home/jenkins/agent/workspace/docker -H  unix:///home/jenkins/agent/workspace/docker.sock images"
              sh "/home/jenkins/agent/workspace/docker -H  unix:///home/jenkins/agent/workspace/docker.sock build --build-arg jarFile=${jarPackage} -t myapp-v${BUILD_ID} . "
            }
          }
        }
        
    }
    post{
       always{
           script{
               println("执行完毕")
           }
       }
       
       success{
           script{
               println("success")
               gitlab.ChangeCommitStatus(projectId,commitSha,"success")
               toemail.Email("流水线成功",userEmail)
           }

       }

       failure{
          script{
              println("failure")
              gitlab.ChangeCommitStatus(projectId,commitSha,"failed")
              toemail.Email("流水线失败",userEmail)
          }

       }

       aborted{
          script{
              println("aborted")
              gitlab.ChangeCommitStatus(projectId,commitSha,"canceled")
              toemail.Email("流水线取消",userEmail)

          }
       }
    }
}
