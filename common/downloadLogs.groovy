def project = testRunner.testCase.testSuite.project
def activeEnv = project.activeEnvironment

def endpoint = ""

if (activeEnv?.name == "Default environment") {
    endpoint = project.getPropertyValue("MecEndpoint") ?: ""
    log.info "Using endpoint from project property (MecEndpoint)"
} else {
    def service = activeEnv.getRestServiceAt(0) ?: activeEnv.getSoapServiceAt(0)
    endpoint = service?.getEndpoint()?.getEndpointString() ?: ""
    log.info "Using endpoint from active environment"
}

if (!endpoint) {
    throw new RuntimeException("Endpoint could not be resolved!")
}

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

    // Consume streams in parallel to prevent buffer deadlock
    def out = new StringBuilder()
    def err = new StringBuilder()
    def outThread = Thread.start { out << proc.inputStream.text }
    def errThread = Thread.start { err << proc.errorStream.text }

    proc.waitFor()
    outThread.join()
    errThread.join()

    def outStr = out.toString().trim()
    def errStr = err.toString().trim()

    if (outStr) log.info "stdout: ${outStr}"
    if (errStr) log.warn "stderr: ${errStr}"

    if (proc.exitValue() != 0) {
        throw new RuntimeException(
            "SSH command failed (exit ${proc.exitValue()}): ${remoteCmd}"
        )
    }
    return outStr
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

ssh("kill \$(cat ${omsWorkspace}/${featureId}.pid) || true")

def basePath = new File(context.testCase.testSuite.project.path).parentFile.path
def errorDir = new File(basePath, "error_logs")

if (!errorDir.exists()) {
    errorDir.mkdirs()
}

def errorDirPath = errorDir.path

scp("${omsWorkspace}/${featureId}.log", "${errorDirPath}")

ssh("rm ${omsWorkspace}/*")

log.info "Logs downloaded from ${sshHost} → ${errorDirPath}"
