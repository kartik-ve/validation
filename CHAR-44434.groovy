def basePath = new File(context.testCase.testSuite.project.path).parentFile.path
def errorDir = new File(basePath, "error_logs")

def featureId = testRunner.testCase.testSuite.name

def file = new File(errorDir, "${featureId}.log")
def searchText = "isNpcActivityFound"

if (!file.exists()) {
    assert false : "File not found: ${file.path}"
}

def found = false

file.withReader { reader ->
    String line
    while ((line = reader.readLine()) != null) {
        if (line.contains(searchText)) {
            log.info "Found text: ${searchText}"
            found = true
            break
        }
    }
}

if (!found) {
    throw new AssertionError("'${searchText}' not found in log file")
}
