def activeEnv = testRunner.testCase.testSuite.project.activeEnvironment
def service   = activeEnv.getRestServiceAt(0) ?: activeEnv.getSoapServiceAt(0)
def endpoint  = service.getEndpoint().getEndpointString() ?: ""

def matcher = endpoint =~ /illnqw(\d+)/
if (!matcher.find()) {
    throw new RuntimeException(
        "Cannot parse environment number from endpoint: '${endpoint}'. " +
        "Expected format: 'http://illnqw<env_number>:<port>'"
    )
}

def env          = matcher[0][1]
def featureId    = testRunner.testCase.testSuite.name
def omsBase      = "/users/gen/omswrk1/JEE/OMS/logs/OmsDomain/OmsServer"
def omsWorkspace = "${omsBase}/validation"
def sshUser      = "omswrk1"
def sshHost      = "illnqw${env}"

def ssh = { String remoteCmd ->
    def fullCmd = ["ssh", "${sshUser}@${sshHost}", remoteCmd]
    log.info "SSH >> ${remoteCmd}"

    def proc = fullCmd.execute()

    def out = proc.inputStream.text?.trim()
    def err = proc.errorStream.text?.trim()

    proc.waitFor()
    
    if (out) log.info  "stdout: ${out}"
    if (err) log.warn  "stderr: ${err}"

    if (proc.exitValue() != 0) {
        throw new RuntimeException(
            "SSH command failed (exit ${proc.exitValue()}): ${remoteCmd}"
        )
    }
    return out
}

def scp = { String remotePath, String localPath ->
    def fullCmd = ["scp", "${sshUser}@${sshHost}:${remotePath}", "${localPath}"]
    log.info "SCP >> ${remotePath}"

    def proc = fullCmd.execute()

    def out = proc.inputStream.text?.trim()
    def err = proc.errorStream.text?.trim()

    proc.waitFor()
    
    if (out) log.info  "stdout: ${out}"
    if (err) log.warn  "stderr: ${err}"

    if (proc.exitValue() != 0) {
        throw new RuntimeException(
            "SSH command failed (exit ${proc.exitValue()}): ${remoteCmd}"
        )
    }
    return out
}

ssh("kill $(cat ${omsWorkspace}/${featureId}.pid) || true")

def basePath = new File(project.path).parentFile.path
def errorDir = new File(basePath, "error_logs")

if (!errorDir.exists()) {
    errorDir.mkdirs()
}

def errorDirPath = errorDir.path

scp("${omsWorkspace}/${featureId}.log", "${errorDirPath}")

ssh("rm ${omsWorkspace}/*")
