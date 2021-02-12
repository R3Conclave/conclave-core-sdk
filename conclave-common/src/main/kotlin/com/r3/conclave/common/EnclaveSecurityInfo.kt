package com.r3.conclave.common

import java.time.Instant

/**
 * Information about how secure an enclave is from the perspective of the
 * platform (no value judgements about code quality are made). As time goes
 * by and people find attacks against the enclave platform the manufacturer
 * may issue updates to re-secure it. This class exposes to what extent the
 * host platform is out of date and allow the client to make
 * privacy/security/availability tradeoffs suitable for its use cases.
 *
 * @property summary A gloss over the state of the enclave platform.
 * @property reason A textual explanation suitable for developers or sysadmins.
 * @property timestamp When the security of the platform was evaluated.
 */
sealed class EnclaveSecurityInfo(val summary: Summary, val reason: String, val timestamp: Instant) {
    /** A simple three-state summary of how trustworthy the remote platform is. */
    enum class Summary {
        /**
         * The platform is known to be insecure in a meaningful way. Any secrets the
         * enclave has produced should be viewed as compromised.
         *
         * Meaningful here means:
         *
         * 1. The enclave is running in debug or simulation mode.
         * 2. That there are real attacks or public attack tools that work against
         *    this version of the technology.
         */
        INSECURE,

        /**
         * There is a software/firmware/microcode update available for the
         * platform that improves security in some way. The client may wish
         * to observe when this starts being reported and define a time span
         * in which the remote enclave operator must upgrade.
         */
        STALE,

        /**
         * The remote platform is up to date and considered secure against
         * known attacks.
         */
        SECURE
    }
}

/**
 * Provides lower level SGX-specific security info, useful only in specific scenarios.
 *
 * @property cpuSVN Opaque security data about the CPU. Although advertised as a
 * "security version number" in fact it's 16 bytes of non-numeric data of a format
 * that Intel doesn't document. You can compare two CPU SVNs together to discover if
 * they are of equal security, but not order them relative to each other.
 */
class SGXEnclaveSecurityInfo(
    summary: Summary,
    reason: String,
    timestamp: Instant,
    val cpuSVN: OpaqueBytes
) : EnclaveSecurityInfo(summary, reason, timestamp) {
    override fun toString(): String {
        return "SGXEnclaveSecurityInfo(summary=$summary, reason=$reason, timestamp=$timestamp, cpuSVM=$cpuSVN)"
    }
}
