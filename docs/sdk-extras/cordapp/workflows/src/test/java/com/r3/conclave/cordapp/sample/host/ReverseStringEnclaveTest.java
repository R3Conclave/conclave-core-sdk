package com.r3.conclave.cordapp.sample.host;

import com.google.common.collect.ImmutableList;
import net.corda.core.concurrent.CordaFuture;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNetworkParameters;
import net.corda.testing.node.StartedMockNode;
import net.corda.testing.node.TestCordapp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReverseStringEnclaveTest {
    private static MockNetwork network;
    private static StartedMockNode client;
    private static StartedMockNode host;

    // DO NOT BLINDLY COPY PASTE THIS. The SEC:INSECURE part does what it sounds like: turns off security for convenience
    // when unit testing and developing.
    //
    // Obviously in a real app you'd not use SEC:INSECURE, however this makes the sample work in simulation mode.
    private final String constraint = "S:4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4 PROD:1 SEC:INSECURE";
    private final String mock_constraint = "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE";

    @BeforeAll
    static void setup() {
        System.setProperty("log4j.configurationFile", "logging.xml");
        network = new MockNetwork(new MockNetworkParameters().withCordappsForAllNodes(
                ImmutableList.of(TestCordapp.findCordapp("com.r3.conclave.cordapp.sample.host"))
        ));
        client = network.createPartyNode(null);
        host = network.createPartyNode(null);
        host.registerInitiatedFlow(ReverseFlowResponder.class);
        network.runNetwork();
    }

    @AfterAll
    static void tearDown() {
        if (network != null) {
            network.stopNodes();
            network = null;
        }
        client = null;
        host = null;
    }

    @Test
    public void reverseStringAsNonAnonymous() throws ExecutionException, InterruptedException {
        CordaFuture<String> flow = client.startFlow(new ReverseFlow(host.getInfo().getLegalIdentities().get(0), "zipzop",
                getConstraint()));
        network.runNetwork();
        assertEquals("Reversed string: pozpiz; Sender name: O=Mock Company 1,L=London,C=GB",
                flow.get());
    }

    @Test
    public void reverseStringAsAnonymous() throws ExecutionException, InterruptedException {
        CordaFuture<String> flow = client.startFlow(new ReverseFlow(host.getInfo().getLegalIdentities().get(0), "hello",
                getConstraint(), true));
        network.runNetwork();
        assertEquals("Reversed string: olleh; Sender name: <Anonymous>", flow.get());
    }

    private String getConstraint() {
        String mode = System.getProperty("enclaveMode");
        if (mode == null || !mode.toLowerCase().equals("mock"))
            return constraint;
        return mock_constraint;
    }
}
