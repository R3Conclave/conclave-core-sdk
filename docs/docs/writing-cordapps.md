# Writing the sample CorDapp

[Corda](https://www.corda.net) is R3's open source enterprise blockchain platform. You can think of it as a peer
to peer database in which user-written apps can be installed. Enclaves can be used inside "CorDapps" to
provide multi-party computation over the peer to peer network. Or look at it the other way around: Corda can provide
communication and identity services to your enclave.

The sample CorDapp builds on the [hello-world](writing-hello-world.md) sample allowing nodes to perform the 
reverse string task between two nodes, one of which loads the enclave. Smart contracts and identity aren't used in this 
sample, only flows are needed.

The sample divides the code into several pieces. The packages that _don't_ have `samples` in the name are
intended to be copy/pasted into your own code. They'll be turned into a full API in future. The code
expects a DCAP capable host, but if you want to use EPID you can edit the enclave startup code to make it use your
Intel API keys.

!!! important
    To understand this tutorial you are expected to have read both the [Conclave Hello World tutorial](writing-hello-world.md) 
    and [the Corda tutorials](https://docs.corda.net/docs/corda-os/4.7/hello-world-introduction.html) already. 

## Configure your workflow CorDapp module

Both Conclave and Corda rely on Gradle build system plugins. Follow the instructions in the 
[hello world tutorial](writing-hello-world.md) to configure Gradle to include an enclave mode. Add the host libraries
to your Corda workflows module:
 
```
dependencies {
    compile project(path: ":enclave", configuration: mode)
    compile "com.r3.conclave:conclave-host:$conclaveVersion"
    compile "com.r3.conclave:conclave-client:$conclaveVersion"
    compile "com.r3.conclave:conclave-common:$conclaveVersion"
    compile "com.r3.conclave:conclave-mail:$conclaveVersion"

    // Corda dependencies.
    cordaCompile "$corda_core_release_group:corda-core:$corda_core_release_version"
    cordaRuntime "$corda_release_group:corda:$corda_release_version"
    testCompile "$corda_release_group:corda-node-driver:$corda_release_version"
}
```

!!! important
    You must use Gradle's `compile` configurations for the host dependencies. If you use `implementation` then you
    will get errors about missing classes when the node starts up, due to issues with fat JARing. The host `dependencies`
    section should look like above.

## Write an enclave host service 

The enclave will be loaded into a service object. This singleton will be available to flows for later usage, and lets
us integrate with the lifecycle of the node. The sample project contains a helper class called `EnclaveHostService` that
you can copy into your project and sub-class. At the moment it isn't a complete or supported API so you may need to
edit it to make it work for your use case.

```java
@CordaService
public class ReverseEnclaveService extends EnclaveHostService {
    public ReverseEnclaveService(@NotNull AppServiceHub serviceHub) {
        super("com.r3.conclave.cordapp.sample.enclave.ReverseEnclave");
    }
}
```

This will make a best effort to enable SGX support on the host if necessary, then it loads the sample `ReverseEnclave` 
class (which is exactly the same as in the hello world tutorial). The `EnclaveHostService` class exposes methods to
send and receive mail with the enclave, in a way that lets flows suspend waiting for the enclave to deliver mail.

## Relaying mail from a flow to the enclave

We've already seen how to [create a new subclass of Enclave](writing-hello-world.md#create-a-new-subclass-of-enclave) and how to
[receive and post mail in the enclave](writing-hello-world.md#receiving-and-post-mail-in-the-enclave) in the 
[hello-world](writing-hello-world.md) tutorial.

In this tutorial, reversing a string involves two parties: one is the initiator that sends the secret string to reverse, 
the other is the responder that reverses the string inside the enclave.

To implement this we will need a flow used by clients, and a 'responder' flow used by the host node.

Here's the responder flow to get the host service, get the enclave attestation and send it to the other party for
verification, get the other party's encrypted mail with the string to reverse, deliver it to the enclave, and retrieve
the enclave encrypted mail to send to the other party for decryption.

```java
// We start with a few lines of boilerplate: read the Corda tutorials if you aren't sure what these are about.
@InitiatedBy(ReverseFlow.class)
public class ReverseFlowResponder extends FlowLogic<Void> {
    private final FlowSession counterpartySession;

    public ReverseFlowResponder(FlowSession counterpartySession) {
        this.counterpartySession = counterpartySession;
    }

    @Suspendable
    @Override
    public Void call() throws FlowException {
        // We get the service and thus, a handle to the enclave.
        final ReverseEnclaveService enclave = this.getServiceHub().cordaService(ReverseEnclaveService.class);

        // Send the other party the enclave identity (remote attestation) for verification.
        counterpartySession.send(enclave.getAttestationBytes());

        // Receive a mail, send it to the enclave, get a reply and send it back to the peer.
        relayMessageToFromEnclave(enclave);

        return null;
    }

    @Suspendable
    private void relayMessageToFromEnclave(EnclaveHostService host) throws FlowException {
        // Other party sends us an encrypted mail.
        byte[] encryptedMail = counterpartySession.receive(byte[].class).unwrap(it -> it);
        // Deliver and wait for the enclave to reply. The flow will suspend until the enclave chooses to deliver a mail
        // to this flow, which might not be immediately.
        byte[] encryptedReply = await(host.deliverAndPickUpMail(this, encryptedMail));
        // Send back to the other party the encrypted enclave's reply
        counterpartySession.send(encryptedReply);
    }
}
```

This flow starts by sending the remote attestation. Typically, an interaction with the enclave should start this way,
although the attestation can be cached if you wish to add such logic.

We then implement a simple request/response type protocol by receiving a byte array and then using the 
`deliverAndPickUpMail` method of the sample's `EnclaveHostService` class. This returns an operation that can be passed
to `await`. The flow will suspend (thus freeing up its thread), potentially for a long period. There is no requirement
that the enclave reply immediately. It can return from processing the delivered mail without replying. When it does
choose to reply, the flow will be re-awakened and the encrypted mail returned to the other side.

!!! important
    Although the Corda flow framework has built in support for it, this sample code does not handle node restarts.

And here's the initiator code. It makes use of a helper class you can find in the sample called `EnclaveClientHelper`. 
Again, you can copy this into your own projects. 

```java
@InitiatingFlow
@StartableByRPC
public class ReverseFlow extends FlowLogic<String> {
    private final Party receiver;
    private final String message;

    public ReverseFlow(Party receiver, String message) {
        this.receiver = receiver;
        this.message = message;
    }

    @Override
    @Suspendable
    public String call() throws FlowException {
        FlowSession session = initiateFlow(receiver);
        // Creating and starting the helper will receive the remote attestation from the receiver party, and verify it
        // against this constraint. Obviously in a real app you'd not use SEC:INSECURE, however this makes the sample
        // work in simulation mode.
        EnclaveClientHelper enclave = new EnclaveClientHelper(
                session,
                "S:4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4 PROD:1 SEC:INSECURE"
        ).start();

        // We can now send and receive messages. They'll be encrypted automatically.
        enclave.sendToEnclave(message.getBytes(StandardCharsets.UTF_8));
        return new String(enclave.receiveFromEnclave());
    }
}
```

!!! important
    **DO NOT BLINDLY COPY THE `SEC:INSECURE` PART INTO PRODUCTION CODE.** IT DOES NOT TAKE SHERLOCK HOLMES TO DEDUCE THAT YOUR APP WILL BE
    INSECURE IF YOU KEEP THAT BIT. :face_with_monocle:

We start in the usual manner for a Corda flow. Once we've established a session with the hosting node, we instantiate
the helper class giving it the session and a constraint. Then we call `start` to receive the `EnclaveInstanceInfo` and
check it against the constraint. If no exception is thrown we can then use `sendToEnclave` and `receiveFromEnclave` with
byte arrays.

The unit tests are completely normal for Corda. However, as the code above will load a real or simulated Linux enclave,
they won't run on Windows or macOS. You can build your enclave in [mock mode](mockmode.md) to fix this or on macOS, you can use the 
`container-gradle` script to run the tests inside a Linux VM.