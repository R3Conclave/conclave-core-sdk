package com.r3.sgx.enclavelethost.client

data class EpidAttestationVerificationBuilder(
        private val acceptGroupOutOfDate: Boolean,
        private val acceptConfigurationNeeded: Boolean,
        private val acceptDebug: Boolean,
        private val quoteConstraints: List<QuoteConstraint>
) {
    constructor(quoteConstraints: List<QuoteConstraint>) : this(
            acceptGroupOutOfDate = false,
            acceptConfigurationNeeded = false,
            acceptDebug = false,
            quoteConstraints = quoteConstraints
    )
    constructor(vararg quoteConstraint: QuoteConstraint) : this(listOf(*quoteConstraint))

    fun withAcceptGroupOutOfDate(value: Boolean) = copy(acceptGroupOutOfDate = value)
    fun withAcceptConfigurationNeeded(value: Boolean) = copy(acceptConfigurationNeeded = value)
    fun withAcceptDebug(value: Boolean) = copy(acceptDebug = value)

    fun build(): EpidAttestationVerification {
        return EpidAttestationVerification(
                acceptGroupOutOfDate = acceptGroupOutOfDate,
                acceptConfigurationNeeded = acceptConfigurationNeeded,
                acceptDebug = acceptDebug,
                quoteConstraints = quoteConstraints
        )
    }
}