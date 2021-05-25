package com.r3.conclave.cordapp.sample.host;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.conclave.cordapp.sample.client.EnclaveClientHelper;
import com.r3.conclave.cordapp.sample.client.EnclaveFlowResponder;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.InitiatedBy;

@InitiatedBy(ReverseFlow.class)
public class ReverseFlowResponder extends FlowLogic<Void> {
    private final FlowSession counterpartySession;

    public ReverseFlowResponder(FlowSession counterpartySession) {
        this.counterpartySession = counterpartySession;
    }

    @Suspendable
    @Override
    public Void call() throws FlowException {
        EnclaveFlowResponder session =
                EnclaveClientHelper.initiateResponderFlow(this, counterpartySession, ReverseEnclaveService.class);

        session.relayMessageToFromEnclave();

        return null;
    }
}
