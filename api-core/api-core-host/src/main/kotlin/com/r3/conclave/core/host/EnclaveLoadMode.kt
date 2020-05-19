package com.r3.conclave.core.host

/**
 * The load mode of the enclave. This determines several things:
 *
 * | EnclaveLoadMode | Host libraries | Enclave DEBUG mode | Attestation roundtrip |
 * | --------------- | -------------- | ------------------ | --------------------- |
 * | SIMULATION      | Simulation     | true               | false                 |
 * | DEBUG           | Debug          | true               | true                  |
 * | RELEASE         | Release        | false              | true                  |
 */
enum class EnclaveLoadMode {
    SIMULATION, DEBUG, RELEASE
}
