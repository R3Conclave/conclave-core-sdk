package com.r3.conclave.common

/**
 * A [MockConfiguration] is used to configure the environment the enclave runs in when using mock mode.
 *
 * When you build an enclave in release, debug mode or simulation mode, there are certain environmental parameters
 * that are defined by the physical system the enclave is running on configured at build time in the enclave
 * build configuration files. When using mock mode it is convenient to be able to change these parameters
 * programatically to allow test cases to be written for checking correct enclave operation around
 * version increments and rollbacks. This class can be used to configure these parameters for use when
 * the mock enclave is loaded.
 *
 * The configuration can only be applied when using mock mode. If you attempt to load a non-mock enclave
 * passing a mock configuration then IllegalArgumentException is thrown.
 *
 * @property codeHash The mock value to use for the hash reported in a remote attestation. This can be null
 * in which case the mock host will create a codeHash from the hash of the enclave class name.
 *
 * @property codeSigningKeyHash The mock value to use for the hash of the public key that signed the enclave.
 * If not specified then the default value is a hash with a value of 32 zeros.
 *
 * @property productID The mock value for the product ID that identifies different enclave products/lineages signed
 * by the same key. Valid values are between 1 and 65535.
 *
 * @property revocationLevel The mock value for the revocation level that is incremented by an enclave author
 * when a weakness in the enclave code is fixed. Valid values are between 0 and 65534.
 *
 * @property tcbLevel The mock value that represents the TCB level of the trusted computing base. In terms
 * of SGX this value is used to provide a mock value for the SGX CPU Security Version Number (CPUSVN). The
 * tcbLevel mock value must be between 1 and 65535. The value represents an ordered version number allowing for
 * testing of TCB recovery.
 *
 * @property enablePersistentMap Whether to enable the persistent map for the enclave. The persistent
 * map is a persistent key-value store whose state is synchronized to the processing of mail in such a way as to
 * make the store resistant to rollbacks. Enabling the persistent map has performance implications, so it is disabled
 * by default.
 *
 * @property maxPersistentMapSize The maximum size of the persistent map in bytes. Default value is 16MiB.
 */
class MockConfiguration {
    // TODO - Have values in MockConfiguration reflect those specified in the build configuration (see CON-692)
    var codeHash: SHA256Hash? = null
    var codeSigningKeyHash: SHA256Hash? = null

    var productID: Int = 1
        set(data) {
            require(data in 0..65535) { "Product ID must be between 0 and 65535" }
            field = data
        }

    var revocationLevel: Int = 0
        set(data) {
            require(data in 0..65534) { "Revocation level must be between 0 and 65534" }
            field = data
        }

    var tcbLevel: Int = 1
        set(data) {
            require(data in 0..65535) { "TCB level must be between 0 and 65535" }
            field = data
        }

    var enablePersistentMap: Boolean = false
    var maxPersistentMapSize: Long = 16 * 1024 * 1024;
}
