package com.r3.conclave.cordapp.sample.host;

import com.r3.conclave.cordapp.host.EnclaveHostService;
import net.corda.core.node.AppServiceHub;
import net.corda.core.node.services.CordaService;
import org.jetbrains.annotations.NotNull;

/**
 * This just loads the specific enclave we want. We can have as many of these as RAM permits.
 */
@CordaService
public class ReverseEnclaveService extends EnclaveHostService {
    public ReverseEnclaveService(@NotNull AppServiceHub serviceHub) {
        super("com.r3.conclave.cordapp.sample.enclave.ReverseEnclave");
    }
}
