package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.CallAcceptor
import com.r3.conclave.common.internal.CallInitiator
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.HostCallType

abstract class HostCallInterface : CallInitiator<HostCallType>, CallAcceptor<EnclaveCallType>() {}
