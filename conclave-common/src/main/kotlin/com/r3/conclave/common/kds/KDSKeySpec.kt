package com.r3.conclave.common.kds

/**
 * A KDSKeySpec defines a configuration for a specific key we want to obtain from the KDS
 *
 * @property name This is the name of the specific key we want to obtain. This is part of the identity of the key:
 * for a different name, the KDS will produce a different key.
 *
 * @property masterKeyType This is the type of the key we want to obtain from the KDS.
 *
 * @property policyConstraint This is the constraint policy of the enclave for which the KDS provides the key.
 * The enclave must satisfy this constraint
 */
class KDSKeySpec(val name: String, val masterKeyType: MasterKeyType, val policyConstraint: String)
