def project = testRunner.testCase.testSuite.project
def activeEnv = project.activeEnvironment

def endpoint = ""

if (activeEnv?.name == "DefaultEnvironment") {
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

log.info "Endpoint    : ${endpoint}"
log.info "Environment : ${env}"
log.info "Host        : ${sshHost}"
log.info "Feature ID  : ${featureId}"

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

// ─── 1. Kill any tail processes older than 6 hours ───────────────────────────
ssh($/ps -eo pid,etimes,cmd | awk '$2 >= 21600 && $0 ~ /tail -fn 0 \/users\/gen\/omswrk1\/JEE\/OMS\/logs\/OmsDomain\/OmsServer\/weblogic/ {print $1}' | xargs -r kill/$)

// ─── 2. Create the workspace directory if not exists ──────────────────────────────────
ssh("mkdir -p ${omsWorkspace}")

// ─── 3. Clean up workspace files older than 6 hours ──────────────────────────
ssh("find ${omsWorkspace} -mindepth 1 -mmin +360 -exec rm -rf {} +")

// ─── 4. Start tailing the latest weblogic log into a feature-specific file ───
def tailCmd = """
LATEST_WEBLOGIC=\$(ls -t ${omsBase}/weblogic.*.log 2>/dev/null | head -1)

if [ -z "\${LATEST_WEBLOGIC}" ]; then
  echo "No weblogic log found" >&2
  exit 1
fi

setsid nohup tail -fn 0 "\${LATEST_WEBLOGIC}" > ${omsWorkspace}/${featureId}.log 2>&1 < /dev/null &
echo \$! > ${omsWorkspace}/${featureId}.pid
"""

ssh("bash -c '${tailCmd}'")

log.info "Log recording started on ${sshHost} → ${omsWorkspace}/${featureId}.log"
