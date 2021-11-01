package com.r3.conclave.common

import java.lang.IllegalArgumentException
import java.util.*

/**
 * A [MockConfiguration] is used to configure the environment the enclave runs in when using mock mode.
 *
 * When you build an enclave in release, debug mode or simulation mode, some parameters of the enclave are defined by
 * the system environment, hardware or are otherwise configured at build time in the enclave build configuration files.
 * It is however, for testing purposes, convenient to be able to change these parameters programmatically when writing
 * test cases. For instance, tests which check for correct behaviour during version increments and rollbacks. This class
 * can be used to configure these parameters. Members are nullable and set to null by default. Setting a member to a
 * non-null value will override the value specified in the build.gradle for your enclave target. In modes other than
 * Mock mode, the mock configuration is ignored.
 *
 * @property codeHash The mock value to use for the hash reported in a remote attestation. This can be null in which
 * case the mock host will create a codeHash from the hash of the enclave class name.
 *
 * @property codeSigningKeyHash The mock value to use for the hash of the public key that signed the enclave. If not
 * specified then the default value is a hash with a value of 32 zeros.
 *
 * @property productID The mock value for the product ID that identifies different enclave products/lineages signed by
 * the same key. Valid values are between 1 and 65535.
 *
 * @property revocationLevel The mock value for the revocation level that is incremented by an enclave author when a
 * weakness in the enclave code is fixed. Valid values are between 0 and 65534.
 *
 * @property tcbLevel The mock value that represents the TCB level of the trusted computing base. In terms of SGX this
 * value is used to provide a mock value for the SGX CPU Security Version Number (CPUSVN). The tcbLevel mock value must
 * be between 1 and 65535. The value represents an ordered version number allowing for testing of TCB recovery. The
 * default TCB level value is 1.
 *
 * @property enablePersistentMap Whether to enable the persistent map for the enclave. The persistent map is a
 * persistent key-value store whose state is synchronized to the processing of mail in such a way as to make the store
 * resistant to rollbacks. Enabling the persistent map has performance implications, see the Conclave docs for more
 * information.
 *
 * @property maxPersistentMapSize The maximum size of the persistent map in bytes. Default value is 16MiB.
 */
class MockConfiguration {
    var codeHash: SHA256Hash? = null
    var codeSigningKeyHash: SHA256Hash? = null

    var productID: Int? = null
        set(data) {
            require(data in 0..65535) { "Product ID must be between 0 and 65535" }
            field = data
        }

    var revocationLevel: Int? = null
        set(data) {
            require(data in 0..65534) { "Revocation level must be between 0 and 65534" }
            field = data
        }

    var tcbLevel: Int? = null
        set(data) {
            require(data in 0..65535) { "TCB level must be between 0 and 65535" }
            field = data
        }

    var enablePersistentMap: Boolean? = null
    var maxPersistentMapSize: Long? = null
}
