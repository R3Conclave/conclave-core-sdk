package com.r3.conclave.integrationtests.tribuo.client

/**
 * This class abstracts the [EnclaveInstanceInfoChecker] verification and file handling
 * depending on the enclave mode.
 * @param enclaveMode the configured enclave mode.
 */
class EnclaveConfiguration(client: Client, enclaveMode: String) {
    val fileManager: FileManager
    val enclaveInstanceInfoChecker: EnclaveInstanceInfoChecker
    init {
        if ("mock" == enclaveMode) {
            fileManager = MockFileManager(client)
            enclaveInstanceInfoChecker = MockEnclaveInstanceInfoChecker()
        } else {
            fileManager = FileManager(client)
            enclaveInstanceInfoChecker = EnclaveInstanceInfoChecker()
        }
    }
}