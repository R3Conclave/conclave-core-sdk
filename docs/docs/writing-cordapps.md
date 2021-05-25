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
class (which expands on the one already seen in the hello world tutorial). The `EnclaveHostService` class exposes methods to
send and receive mail with the enclave, in a way that lets flows suspend waiting for the enclave to deliver mail.

In the next section about [relaying a mail from a flow to the Enclave](writing-cordapps.md#relaying-mail-from-a-flow-to-the-enclave),
the above class is used as a parameter to initiate the responder flow that ensures the host service is started.

## Relaying mail from a flow to the Enclave

We've already seen how to [create a new subclass of Enclave](writing-hello-world.md#create-a-new-subclass-of-enclave) and how to
[receive and post mail in the enclave](writing-hello-world.md#receiving-and-post-mail-in-the-enclave) in the 
[hello-world](writing-hello-world.md) tutorial, and in the coming sections we'll see how some of this boilerplate has been
wrapped into an API to simplify setting up secure flows and exchange secure messages between parties.

In this tutorial, reversing a string involves two parties: one is the initiator that sends the secret string to reverse, 
the other is the responder that reverses the string inside the enclave.

To implement this we will need a flow used by clients, and a 'responder' flow used by the host node.

Here's the responder flow to get the enclave host service up and running, get the enclave attestation and send it to the other
party for verification, get, verify and acknowledge the other party's encrypted identity, get the other party's encrypted mail
with the string to reverse, deliver it to the enclave, and retrieve the enclave encrypted mail to send to the other party for 
decryption.

```java
// We start with a few lines of boilerplate: read the Corda tutorials if you aren't sure what these are about.
@InitiatedBy(ReverseFlow.class)
public class ReverseFlowResponder extends FlowLogic<Void> {

    // private variable
    private final FlowSession counterpartySession;

    // Constructor
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
```

Looking into the responder's flow initiation, we see that this side of the flow starts by ensuring a specific instance of the 
`EnclaveHostService` is started and sending the remote attestation. Typically, an interaction with the enclave should start 
this way, although the attestation may be cached if such logic is required.

```java
@Suspendable
public static <T extends EnclaveHostService> EnclaveFlowResponder initiateResponderFlow(@NotNull FlowLogic<?> flow,
                                                            @NotNull FlowSession counterPartySession,
                                                            @NotNull Class<T> serviceType) {
    // Starts an instance of the EnclaveHostService specified by serviceType
    EnclaveHostService host = (EnclaveHostService) flow.getServiceHub().cordaService(serviceType);

    // Send the other party the enclave identity (remote attestation) for verification.
    counterPartySession.send(host.getAttestationBytes());

    return new EnclaveFlowResponder(flow, counterPartySession, host);
}
```

The `EnclaveFlowResponder` implements a simple request/response type protocol that receives an encrypted byte array
and uses the `deliverAndPickUpMail` method of the `EnclaveHostService` class. This returns an operation that can be passed
to `await`. The flow will suspend (thus freeing up its thread), potentially for a long period. There is no requirement
that the enclave reply immediately. It can return from processing the delivered mail without replying. When it does
choose to reply, the flow will be re-awakened and the encrypted mail returned to the other side.

```java
@Suspendable
public void relayMessageToFromEnclave() throws FlowException {
    // Other party sends us an encrypted mail.
    byte[] encryptedMail = session.receive(byte[].class).unwrap(it -> it);
    // Deliver and wait for the enclave to reply. The flow will suspend until the enclave chooses to deliver a mail
    // to this flow, which might not be immediately.
    byte[] encryptedReply = flow.await(host.deliverAndPickUpMail(flow, encryptedMail));
    // Send back to the other party the encrypted enclave's reply
    session.send(encryptedReply);
}
```

!!! important
    Although the Corda flow framework has built in support for it, this sample code does not handle node restarts.

And here's the initiator flow that initiates a session with the responder party, get the enclave attestation, validate it, 
build and send a verifiable identity for the enclave to validate, send the encrypted mail with the string to reverse and 
read and decrypt the enclave's response.

!!! important
    **DO NOT BLINDLY COPY THE `SEC:INSECURE` PART INTO PRODUCTION CODE.** IT DOES NOT TAKE SHERLOCK HOLMES TO DEDUCE THAT YOUR APP WILL BE
    INSECURE IF YOU KEEP THAT BIT. :face_with_monocle:

```java
@InitiatingFlow
@StartableByRPC
public class ReverseFlow extends FlowLogic<String> {
    private final Party receiver;
    private final String message;
    private final String constraint;

    public ReverseFlow(Party receiver, String message) {
        this.receiver = receiver;
        this.message = message;
        this.constraint = "S:4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4 PROD:1 SEC:INSECURE";
    }

    public ReverseFlow(Party receiver, String message, String constraint) {
        this.receiver = receiver;
        this.message = message;
        this.constraint = constraint;
    }

    @Override
    @Suspendable
    public String call() throws FlowException {
        EnclaveFlowInitiator session = EnclaveClientHelper.initiateFlow(this, receiver, constraint);

        byte[] response = session.sendAndReceive(message.getBytes(StandardCharsets.UTF_8));

        return new String(response);
    }
}
```

The flow initiation starts in the usual manner for a Corda flow. Once a session with the hosting node is established, the
flow waits for an `EnclaveInstanceInfo` to verify it against the constraint passed. If the enclave verifies successfullly
and doesn't throw an exception, the flow can continue by sending the initiator party identity to the enclave for
[authentication](writing-cordapps.md#authenticating-senders-identity).


```java
@Suspendable
public static EnclaveFlowInitiator initiateFlow(FlowLogic<?> flow, Party receiver, String constraint) throws FlowException {
    FlowSession session = flow.initiateFlow(receiver);

    // Read the enclave attestation from the peer.
    EnclaveInstanceInfo attestation = session.receive(byte[].class).unwrap(EnclaveInstanceInfo::deserialize);

    // The key hash below (the hex string after 'S') is the public key version of sample_private_key.pem
    // In a real app you should remove the SEC:INSECURE part, of course.
    try {
        EnclaveConstraint.parse(constraint).check(attestation);
    } catch (InvalidEnclaveException e) {
        throw new FlowException(e);
    }

    EnclaveFlowInitiator instance = new EnclaveFlowInitiator(flow, session, attestation);

    instance.sendIdentityToEnclave();

    return instance;
}
```

Once the enclave is successfully verified and our identity is authenticated by the enclave, the flow can start
securely exchanging encrypted messages with the enclave through the `EnclaveFlowInitiator` instance returned,
which implements a simple send/receive API that encrypts and decrypts the outgoing and incoming data in the
form of byte arrays.
The send and receive methods use the the PostOffice API and the `EnclaveFlowInitiator` class holds a map
of PostOffice instances per topic, where the default topic is the session's flow id and, so far, one extra 
topic is used for the authentication message that is sent via `sendIdentityToEnclave()`.

```java
@Suspendable
@NotNull
private byte[] sendAndReceive(String topic, byte[] messageBytes) throws FlowException {
    sendToEnclave(topic, messageBytes);
    return receiveFromEnclave();
}

@Suspendable
private void sendToEnclave(String topic, byte[] messageBytes) throws FlowException {
    PostOffice postOffice = getOrCreatePostOffice(topic);
    byte[] encryptedMail = postOffice.encryptMail(messageBytes);
    session.send(encryptedMail);
}

@Suspendable
@NotNull
public byte[] receiveFromEnclave() throws FlowException {
    PostOffice postOffice = postOffices.get(flowTopic);
    EnclaveMail reply = session.receive(byte[].class).unwrap((mail) -> {
        try {
            return postOffice.decryptMail(mail);
        } catch (IOException e) {
            throw new FlowException("Unable to decrypt mail from Enclave", e);
        }
    });
    return reply.getBodyAsBytes();
}
```

# Authenticating sender's identity

The enclave can authenticate a sender's identity that belongs to a network where nodes are identified by a X.509
certificate and the network is controlled by a certificate authority like in a Corda network.

The network CA root certificate's public key must be stored in the enclave's resource folder (in this example the 
path is enclave/src/main/resources/) in a file named trustedroot.cer, so that the enclave can validate the sender
identity.

!!! important
    If you don't need to identify sending parties and plan to use anonymous mode messaging instead, you can ignore or remove the trustedroot.cer certificate file. In this case, do not forget to initiate flows in anonymous mode to avoid a flow exception.

The identity is set up by the `EnclaveFlowInitiator` class during authentication and sent to the enclave who 
verifies it.

```java
@Suspendable
private SenderIdentity buildSenderIdentity() {
    byte[] sharedSecret = encryptionKey.getPublicKey().getEncoded();
    PublicKey signerPublicKey = flow.getOurIdentity().getOwningKey();
    DigitalSignature signature = flow.getServiceHub().getKeyManagementService().sign(
            sharedSecret, signerPublicKey).withoutKey();

    CertPath signerCertPath = flow.getOurIdentityAndCert().getCertPath();
    return  SenderIdentity.create(signerCertPath, signerPublicKey, signature.getBytes());
}

@Suspendable
private void sendIdentityToEnclave() throws FlowException {
    if(!authenticated) {
        SenderIdentity identity = buildSenderIdentity();
        sendAndReceive(authTopic, identity.serialize());
        authenticated = true;        
    }
}
```

When the enclave successfully validates the identity, it stores it in a key based cache. Subsequent messages
from the same sender in the same session are paired with the cached identity which is then available from
within the `receiveCordaMail` method, accessible via the `getAuthenticatedSenderIdentity` method.

The SenderIdentity interface has methods to query the identity verification status, X.509 name, and public
key.

```java
@Override
protected void receiveCordaMail(long id, CordaEnclaveMail mail, String routingHint) {
    String reversedString = reverse(new String(mail.getBodyAsBytes()));
    SenderIdentity identity = mail.getSenderIdentity();

    String responseString = String.format("Reversed string: %s; Sender anonymous: %b; Sender name: %s",
            reversedString,
            identity.isAnonymous(),
            identity.getName());

    // Get the PostOffice instance for responding back to this mail. Our response will use the same topic.
    final EnclavePostOffice postOffice = postOffice(mail);
    // Create the encrypted response and send it back to the sender.
    final byte[] reply = postOffice.encryptMail(responseString.getBytes(StandardCharsets.UTF_8));
    postMail(reply, routingHint);
}
```
## Unit testing

The unit tests are completely normal for Corda. However, as the code above will load a real or simulated Linux enclave,
they won't run on Windows or macOS. You can build your enclave in [mock mode](mockmode.md) to fix this or on macOS, you can use the 
`container-gradle` script to run the tests inside a Linux VM.
