package com.r3.conclave.enclave.kds

import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.kds.KDSKeySpecification

class KDSConfiguration(val kdsKeySpec: KDSKeySpecification, val kdsEnclaveConstraint: EnclaveConstraint)