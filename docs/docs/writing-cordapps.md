# Writing the sample CorDapp

[Corda](https://www.corda.net) is R3's open source enterprise blockchain platform. You can think of it as a peer
to peer database in which user-written apps can be installed. Enclaves can be used inside "CorDapps" to
provide multi-party computation over the peer to peer network. Or look at it the other way around: Corda can provide
communication and identity services to your enclave.

The sample CorDapp builds on the [hello-world](writing-hello-world.md) sample allowing nodes to perform the 
reverse string task between two nodes, one of which loads the enclave. Smart contracts aren't used in this 
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
this way, although the attestation may be cached if such logic is required. The flow responder expects to receive
an identity during the initialization. An empty identity message can be sent if the party prefers to remain anonymous.

```kotlin
@Suspendable
@Throws(FlowException::class)
@JvmStatic
@JvmOverloads
fun <T : EnclaveHostService> initiateResponderFlow(flow: FlowLogic<*>, 
                                                   counterPartySession: FlowSession,
                                                   serviceType: Class<T>): EnclaveFlowResponder {
    // Start an instance of the enclave hosting service
    val host = flow.serviceHub.cordaService(serviceType)
    // Send the other party the enclave identity (remote attestation) for verification.
    counterPartySession.send(host.attestationBytes)
    val instance = EnclaveFlowResponder(flow, counterPartySession, host)
    // Relay the initial identity message to the enclave and relay the response back
    instance.relayMessageToFromEnclave()
    return instance
}
```

The `EnclaveFlowResponder` implements a simple request/response type protocol that receives an encrypted byte array
and uses the `deliverAndPickUpMail` method of the `EnclaveHostService` class. This returns an operation that can be passed
to `await`. The flow will suspend (thus freeing up its thread), potentially for a long period. There is no requirement
that the enclave reply immediately. It can return from processing the delivered mail without replying. When it does
choose to reply, the flow will be re-awakened and the encrypted mail returned to the other side.

```kotlin
@Suspendable
@Throws(FlowException::class)
fun relayMessageToFromEnclave() {
    // Other party sends us an encrypted mail.
    val encryptedMail = session.receive(ByteArray::class.java).unwrap { it }
    // Deliver and wait for the enclave to reply. The flow will suspend until the enclave chooses to deliver a mail
    // to this flow, which might not be immediately.
    val encryptedReply: ByteArray = flow.await(host.deliverAndPickUpMail(flow, encryptedMail))
    // Send back to the other party the encrypted enclave's reply
    session.send(encryptedReply)
}
```

!!! important
    Although the Corda flow framework has built in support for it, this sample code does not handle node restarts.

And here's the initiator flow that initiates a session with the responder party, get the enclave attestation, validate it, 
build and send a verifiable identity for the enclave to validate, send the encrypted mail with the string to reverse and 
read and decrypt the enclave's response. Keep in mind that a verifiable identity is only sent to the enclave if the `anonymous`
property is set to false.

```java
@InitiatingFlow
@StartableByRPC
public class ReverseFlow extends FlowLogic<String> {
    private final Party receiver;
    private final String message;
    private final String constraint;
    private final Boolean anonymous;

    public ReverseFlow(Party receiver, String message, String constraint) {
        this(receiver, message, constraint, false);
    }

    public ReverseFlow(Party receiver, String message, String constraint, Boolean anonymous) {
        this.receiver = receiver;
        this.message = message;
        this.constraint = constraint;
        this.anonymous = anonymous;
    }

    @Override
    @Suspendable
    public String call() throws FlowException {
        EnclaveFlowInitiator session = EnclaveClientHelper.initiateFlow(this, receiver, constraint, anonymous);

        byte[] response = session.sendAndReceive(message.getBytes(StandardCharsets.UTF_8));

        return new String(response);
    }
}
```

The flow initiation starts in the usual manner for a Corda flow. Once a session with the hosting node is established, the
flow waits for an `EnclaveInstanceInfo` to verify it against the constraint passed. If the enclave verifies successfully
and doesn't throw an exception, the flow can continue by sending the initiator party identity to the enclave for
[authentication](writing-cordapps.md#authenticating-senders-identity). The last step is only applicable if the party wishes to
share its identity. No party identity is not sent if the parameter `anonymous` is set to `true`.



```kotlin
@Suspendable
@Throws(FlowException::class)
@JvmStatic
@JvmOverloads
fun initiateFlow(flow: FlowLogic<*>, receiver: Party, constraint: String,
                 anonymous: Boolean = false): EnclaveFlowInitiator {
    val session = flow.initiateFlow(receiver)

    // Read the enclave attestation from the peer.
    val attestation = session.receive(ByteArray::class.java).unwrap { from: ByteArray ->
        EnclaveInstanceInfo.deserialize(from)
    }

    // The key hash below (the hex string after 'S') is the public key version of sample_private_key.pem
    // In a real app you should remove the SEC:INSECURE part, of course.
    try {
        EnclaveConstraint.parse(constraint).check(attestation)
    } catch (e: InvalidEnclaveException) {
        throw FlowException(e)
    }
    val instance = EnclaveFlowInitiator(flow, session, attestation)
    instance.sendIdentityToEnclave(anonymous)

    return instance
}
```

Once the enclave is successfully verified, and our identity is authenticated by the enclave (if we decided to share our identity),
the flow can start securely exchanging encrypted messages with the enclave through the `EnclaveFlowInitiator` instance returned,
which implements a simple send/receive API that encrypts and decrypts the outgoing and incoming data in the form of byte arrays.
The send and receive methods use the PostOffice API. The `EnclaveFlowInitiator` class holds one PostOffice instance which
has the topic set to the session's flow id.

```kotlin
@Suspendable
@Throws(FlowException::class)
fun sendAndReceive(messageBytes: ByteArray): ByteArray {
    sendToEnclave(messageBytes)
    return receiveFromEnclave()
}

@Suspendable
@Throws(FlowException::class)
private fun sendToEnclave(messageBytes: ByteArray) {
    val encryptedMail = postOffice.encryptMail(messageBytes)
    session.send(encryptedMail)
}

@Suspendable
@Throws(FlowException::class)
fun receiveFromEnclave(): ByteArray {
    val reply: EnclaveMail = session.receive(ByteArray::class.java).unwrap { mail: ByteArray ->
        try {
            postOffice.decryptMail(mail)
        } catch (e: IOException) {
            throw FlowException("Unable to decrypt mail from Enclave", e)
        }
    }
    return reply.bodyAsBytes
}
```

# Authenticating sender's identity

The enclave can authenticate a sender's identity that belongs to a network where nodes are identified by a X.509
certificate, and the network is controlled by a certificate authority like in a Corda network.

The network CA root certificate's public key must be stored in the enclave's resource folder (in this example the 
path is enclave/src/main/resources/) in a file named trustedroot.cer, so that the enclave can validate the sender
identity.

The identity is set up by the `EnclaveFlowInitiator` class during authentication and sent to the enclave who 
verifies it. Please be aware that the first byte of the identity message indicates whether a party wants to remain
anonymous or not. If a party decides to remain anonymous, the identity message is padded with zeros to prevent
anyone in the middle from using statistical analysis to guess whether a party is anonymous or not. If a party decides to authenticate
itself, the remaining bytes represent the party's identity.

```kotlin
@Suspendable
private fun buildMailerIdentity(): SenderIdentityImpl {
    val sharedSecret = encryptionKey.publicKey.encoded
    val signerPublicKey = flow.ourIdentity.owningKey
    val signature = flow.serviceHub.keyManagementService.sign(sharedSecret, signerPublicKey).withoutKey()
    val signerCertPath = flow.ourIdentityAndCert.certPath
    return SenderIdentityImpl(signerCertPath, signature.bytes)
}

@Suspendable
@Throws(FlowException::class)
fun sendIdentityToEnclave(isAnonymous: Boolean) {

    val serializedIdentity = getSerializedIdentity(isAnonymous)
    sendToEnclave(serializedIdentity)

    val mail: EnclaveMail = session.receive(ByteArray::class.java).unwrap { mail: ByteArray ->
        try {
            postOffice.decryptMail(mail)
        } catch (e: IOException) {
            throw FlowException("Unable to decrypt mail from Enclave", e)
        }
    }

    if (!mail.topic.contentEquals("$flowTopic-ack"))
        throw FlowException("The enclave could not validate the identity sent")
}
```

When the enclave successfully validates the identity, it stores it in a key based cache. Subsequent messages
from the same sender in the same session are paired with the cached identity which is then available from
within the `receiveMail` method through the extra parameter called `identity`. This identity can be used to uniquely 
identify a sender but be aware that the parameter might be null if the user decides to remain anonymous.

```java
@Override
protected void receiveMail(long id, EnclaveMail mail, String routingHint, SenderIdentity identity) {
    String reversedString = reverse(new String(mail.getBodyAsBytes()));

    String responseString;
    if (identity == null) {
        responseString = String.format("Reversed string: %s; Sender name: <Anonymous>", reversedString);
    } else {
        responseString = String.format("Reversed string: %s; Sender name: %s", reversedString, identity.getName());
    }

    // Get the PostOffice instance for responding back to this mail. Our response will use the same topic.
    final EnclavePostOffice postOffice = postOffice(mail);
    // Create the encrypted response and send it back to the sender.
    final byte[] reply = postOffice.encryptMail(responseString.getBytes(StandardCharsets.UTF_8));
    postMail(reply, routingHint);
}
```

The method `receiveMail` contains the enclave logic and therefore can contain any logic necessary to meet the business requirements.
For the example above, the enclave simply reverses a string and returns the result back with the sender name if the party
decided not to be anonymous. The mail parameter contains some metadata, and the data which is going to be processed by the enclave.
The call `mail.getBodyAsBytes()` returns the data that is going to be processed by the enclave. As mention previously, the `identity`
parameter object contains the identity of the sender or is set to null if the sender decided to remain anonymous.

!!! important
    Contrary to the 'hello world' project, the method `receiveMail` contains an extra parameter called identity. Even though
    technically this is an overload, the original is final and can't be used.

## Unit testing

The unit tests are completely normal for Corda. However, as the code above will load a real or simulated Linux enclave,
they won't run on Windows or macOS. It is possible to work around this limitation using virtualization. For instructions
on how to do this, consult the sample CorDapp readme.
