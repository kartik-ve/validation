def envConfig = [
    "8342": [
        endpoint: "http://illnqw8342:11111",
        jdbc: "jdbc:oracle:thin:OMS1OMS/OMS1OMS@illnqw8393:1521:CHCDB8342"
    ],
    "8365": [
        endpoint: "http://illnqw8365:11111",
        jdbc: "jdbc:oracle:thin:OMS1OMS/OMS1OMS@illnqw8404:1521:CHCDB8365"
    ],
    "8645": [
        endpoint: "http://illnqw8645:11111",
        jdbc: "jdbc:oracle:thin:OMS1OMS/OMS1OMS@illnqw8655:1521:CHCDB8645"
    ],
    "8665": [
        endpoint: "http://illnqw8665:11111",
        jdbc: "jdbc:oracle:thin:OMS1OMS/OMS1OMS@illnqw8696:1521:CHCDB8665"
    ],
    "8666": [
        endpoint: "http://illnqw8666:11111",
        jdbc: "jdbc:oracle:thin:OMS1OMS/OMS1OMS@illnqw8695:1521:CHCDB8666"
    ],
    "8667": [
        endpoint: "http://illnqw8667:11111",
        jdbc: "jdbc:oracle:thin:OMS1OMS/OMS1OMS@illnqw8697:1521:CHCDB8667"
    ],
    "8731": [
        endpoint: "http://illnqw8731:11111",
        jdbc: "jdbc:oracle:thin:OMS1OMS/OMS1OMS@illnqw8740:1521:CHCDB8731"
    ],
    "SIT1": [
        endpoint: "https://sit1-oe-npe.amdocs.spectrum.com",
        jdbc: "jdbc:oracle:thin:OMS1OMS/OMS1OMS@mwhlvchcadb01:1521:CHCDB1"
    ],
    "QA1": [
        endpoint: "https://qa1-oe-npe.amdocs.spectrum.com",
        jdbc: "jdbc:oracle:thin:OMS1OMS/OMS1OMS@mwhlvchcadb02:1521:CHCDB2"
    ],
    "UAT1": [
        endpoint: "https://uat1-oe-npe.amdocs.spectrum.com",
        jdbc: "jdbc:oracle:thin:OMS1OMS/OMS1OMS@mwhlvchcadb03:1521:CHCDB3"
    ],
    "HF1": [
        endpoint: "https://hf1-oe-npe.amdocs.spectrum.com",
        jdbc: "jdbc:oracle:thin:OMS1OMS/OMS1OMS@mwhlvchcadb04:1521:CHCDB4"
    ],
    "PLAB": [
        endpoint: "https://plab-oe-omni.amdocs.spectrum.com",
        jdbc: "jdbc:oracle:thin:CHCOMS/chc_4c0nn@chcplscn1:1521/CHCOMS1"
    ]
]

def project = null

try {
    project = context?.testSuite?.project
} catch (ignored) {}

if (!project) {
    project = testRunner.testCase.testSuite.project
}

def activeEnv = project.activeEnvironment

if (activeEnv && activeEnv.name != "Default environment" && activeEnv.name != "Default") {
    log.info "ENV found: ${activeEnv.name}. Continuing..."

    def service = activeEnv.getRestServiceAt(0) ?: activeEnv.getSoapServiceAt(0)
    def endpoint = service?.getEndpoint()?.getEndpointString() ?: ""

    if (endpoint) {
        def matcher = endpoint =~ /illnqw(\d+)/
        if (matcher.find()) {
            def env = matcher[0][1]
            project.setPropertyValue("ENV", env)
        }
    }

    return
}

def env = System.getProperty("env") ?: project.getPropertyValue("ENV")

if (!env) {
    log.error "ENV not provided! Stopping TestSuite execution..."
    throw new RuntimeException("ENV not provided")
}

env = env.trim().toUpperCase()

def config = envConfig[env]

if (!config) {
    throw new RuntimeException("Invalid ENV: " + env)
}

project.setPropertyValue("ENV", env)
project.setPropertyValue("MecEndpoint", config.endpoint)
project.setPropertyValue("MecDBConnection", config.jdbc)

log.info "ENV: ${env}"
log.info "Endpoint: ${config.endpoint}"
log.info "JDBC URL: ${config.jdbc}"
