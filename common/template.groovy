def project = testRunner.testCase.testSuite.project
def username = project.getPropertyValue("USER")
def hostname = project.getPropertyValue("HOST")

def ssh = { String remoteCmd ->
    def fullCmd = ["ssh", "${username}@${hostname}", remoteCmd]
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
    return [outStr, errStr]
}

def logDir = "/users/gen/omswrk1/JEE/OMS/logs/OmsDomain/OmsServer"
def txnId = context.expand( '${Product-Configuration - Validate&Save - Other#Response#$[\'ImplProductConfigurationsResponse\'][\'transactionId\']}' )
def orderId = context.expand( '${Create Order#Response#$[\'ImplCreateOrderResponse\'][\'implCreateOrderOutput\'][\'orderId\']}' )
def searchPattern = ~/Changed LOBs for CO Order ID: ${orderId} = \[[A-Z]{2}\]/

int retries = 5
int waitMs = 3000
boolean found = false

while (retries-- > 0 && !found) {
    def command = "ls -t ${logDir}/weblogic*.log 2>/dev/null | head -1 | xargs -r grep '${txnId}'"
    
    def (logOutput, errorOutput) = ssh(command)
    
    if (errorOutput?.toLowerCase()?.contains("no such file")) {
        assert false : "Invalid path: ${errorOutput}"
    }

    if (logOutput =~ searchPattern) {
        found = true
    } else {
        log.info("Not found, retrying...")
        sleep(waitMs)
    }
}

assert found : "'${searchPattern}' not found in logs"
log.info("Validation passed!")
