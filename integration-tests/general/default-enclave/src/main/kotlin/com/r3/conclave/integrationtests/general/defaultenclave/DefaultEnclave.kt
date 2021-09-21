package com.r3.conclave.integrationtests.general.defaultenclave

import com.r3.conclave.enclave.Enclave
import com.r3.conclave.integrationtests.general.commonenclave.AbstractTestActionEnclave

/**
 * This represents an enclave in its default configuration. Do not override any of the functions from [Enclave] nor the
 * default config in build.gradle.
 *
 * Because this enclave doesn't override any of the defaults in its build.gradle, it should be signed by a random key
 * each time it's built.
 */
class DefaultEnclave : AbstractTestActionEnclave()
