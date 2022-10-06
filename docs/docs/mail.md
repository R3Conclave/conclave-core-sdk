# Mail

Conclave uses *Conclave Mail*, a communication mechanism that allows you to send encrypted messages to enclaves which 
can be decrypted only by enclaves that you trust. Conclave Mail ensures secure communication between a client and an 
enclave.

## How does Mail work

Conclave Mail is structured as an authenticated byte array. A Mail item consist of five parts:

1. A protocol ID.
1. A plain-text header.
1. A plain-text envelope.
1. The handshake.
1. The encrypted body.

The protocol ID, the unencrypted header, and the envelope forms the *prologue* of a Mail item.

The handshake consist of the data needed to do an [Elliptic-curve Diffie-Hellman](https://en.wikipedia.org/wiki/Elliptic-curve_Diffie%E2%80%93Hellman#:~:text=Elliptic%2Dcurve%20Diffie%E2%80%93Hellman%20(,or%20to%20derive%20another%20key.)
calculation and derive a unique [AES-GCM](https://www.cryptosys.net/pki/manpki/pki_aesgcmauthencryption.html) key
using [Noise protocol](https://noiseprotocol.org/noise.html) and
[SHA-256](https://www.simplilearn.com/tutorials/cyber-security-tutorial/sha-256-algorithm) algorithm.

The encrypted body consists of secure data that you need to transfer between a client and an enclave. Only the 
target enclave can decrypt the encrypted body of a Mail item.

AES-GCM is an *authenticated* encryption mechanism. That means the key is used not only to encrypt data, but also 
to calculate a special hash called a *tag*. Any change to the message will also change the *tag*. This prevents 
attackers from changing the contents of a message without the enclave noticing it.

Like all symmetric ciphers, for AES-GCM to work, both sides must know the same key. In cases where the client 
knows only a public key, Conclave Mail uses the Elliptic-curve Diffie-Hellman algorithm. The elliptic curve used in 
Conclave Mail is [Curve25519](https://en.wikipedia.org/wiki/Curve25519).

The client know the target's public key either because the enclave puts its public key into the remote attestation, 
represented by an [`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html)
object, or because the enclave is replying and obtained the key to reply to from an earlier Mail item. Two public 
keys is all that's needed for both parties to compute the same AES key, without any man-in-the-middle being able to 
calculate the same value.

The sender may also have a long term "static" public key that the target will recognise. We append an encryption of this
public key and repeat the calculation between the enclave's public key and the static key, generating a new AES-GCM key
as a result.

As each step in the handshake progresses, a running hash is maintained that mixes in the hash of the prologue and the
various intermediate stages. This allows the unencrypted headers to be tamper-proof against the host as well, because
the host will not be able to recalculate the authentication tag emitted to the end of the handshake.

Thus, the handshake consists of a random public key, an authenticated encryption of the sender's public key, and an 
authentication hash. Once this is done the Mail item's AES key has been computed. The initialization vector for each 
AES encryption is a counter (an exception will be thrown if the Mail item is so large the counter would wrap around, as 
that would potentially reveal information for cryptanalysis,
however this cannot happen given the maximum Mail size).

The encrypted body consists of a set of 64KB packets. Each packet has its own authentication tag. So, data is read
from a Mail item in 64KB chunks.

## Mail features

Conclave Mail provides a variety of features that are useful when building secure applications.

### Encryption

The encryption key used by an enclave is private to that enclave. The encryption key is stable across system restarts 
and enclave upgrades. So, any message encrypted and delivered by older clients can still be decrypted. The format
uses the respected Noise protocol framework with AES-GCM and
[SHA-256 algorithm](https://www.simplilearn.com/tutorials/cyber-security-tutorial/sha-256-algorithm).

### Authentication

Conclave Mail enables a message to prove that it came from the owner of a particular key. If a user can be 
identified by a key, you can use Conclave Mail's envelope to cryptographically authenticate that user. You can use 
this feature to securely implement the login and authentication processes of your application outside the enclave.

### Message headers

Conclave Mail's plain-text headers are authenticated and tamper-proof. Clients can use these headers to logically 
connect messages together to structure a conversation. Clients can also hold multiple conversations simultaneously 
using message headers. You can use message headers to implement usage tracking, prioritisation, or other 
non-data-processing tasks that the host can assist with. The mail headers contain the following fields:

1. A _topic_. This can be used to distinguish between different streams of Mail items from the same client. It's a 
   string similar to an email subject. Topics are scoped per-sender and are not global. Clients can send multiple 
   streams of related Mail items by using a different topic for each stream. Conclave does not parse the topic. To 
   avoid replay attacks, you should never reuse a topic for an unrelated Mail item. It's a good practice to use a 
   random UUID in a topic to avoid reuse.
1. The _sequence number_. Every Mail item under a topic has a sequence number that starts from zero and incremented 
   by one. Conclave reject messages if the sequence number is not in order. This ensures that the enclave 
   receives a stream of related Mail items in the correct order.
1. The _envelope_. This is a slot that can hold any plain-text byte array. You can use it to hold app-specific data 
   that should be authenticated but unencrypted.

These header fields are available to the host and therefore should not contain secrets. It may seem odd to have
data that's unencrypted, but it's often useful for the client, host, and enclave to collaborate in various ways related
to storage and routing of data. Even when the host is untrusted, it may still be useful for the client to send data
that is readable by the host and the enclave simultaneously, but which the host cannot tamper with. Inside the enclave
you can be assured that the header fields contain the values set by the client, because they're checked before
[`receiveMail`](api/-conclave%20-core/com.r3.conclave.enclave/-enclave/receive-mail.html) is invoked.

In addition to the headers, there is also the _authenticated sender public key_. This is the public key of the client
that sent the Mail item. It's encrypted so that the host cannot learn the client identities.

### Framing

Conclave Mail delimits messages such that you can always tell where they begin and end without needing to impose 
your own framing. This in-built framing prevents the host from tampering with the messages by detecting 
end-of-message characters. 

## Attacks on messaging

Conclave Mail is designed to block a variety of attacks the host can mount on the enclave.

### Observation

The body of a Mail item is encrypted with industry standard AES-GCM. The host can't see what the client is sending 
to the enclave.

### Tampering

The encrypted body, the plain-text header, and the envelope are authenticated such that the enclave can detect if 
the host modifies it.

### Reordering

The Conclave runtime verifies that the sequence numbers under a topic always increment by one before passing a Mail 
item to the enclave. This feature ensures that the host can't reorder messages or drop Mail items.
 
### Side-channel attacks

A malicious host can infer crucial information if it knows the size or timing of a message.
Conclave guards against these types of [side-channel attacks](security.md#side-channel-attacks) as follows.

Conclave pads Mail items to a uniform size. This blocks attempts to infer the contents of messages based on the 
precise size of a message. By default, Conclave Mail uses a moving average size to pad messages. However, you can 
configure the size of your application's messages to a reasonable upper limit.

To avoid the host guessing information from message timing, you can send empty Mail items even when you have nothing 
to say. For example, if an enclave is running an auction between users, and you wish to hide who won, the enclave 
can send a "you won" or "you lost" message to every client.

## Comparison to a classical public key infrastructure

Conclave uses Conclave Mail to connect clients to enclaves because of the following reasons:

1. Conclave Mail can work with a small [Trusted Computing Base (TCB)](https://en.wikipedia.org/wiki/Trusted_computing_base) 
   when compared to HTTPS. A small TCB removes several common security problems at the origin.

2. The certificate-based architecture of SSL/TLS doesn't gel with enclave-based computing. Conclave Mail uses the 
   better-suited Noise protocol framework. The Noise protocol delivers most of the functionalities of Transport 
   Layer Security (TLS) in a cleaner, simpler, and modular manner.

   The TLS protocol is reliant on a certificate-based security architecture, which does not suit enclave-based 
   computing. Certificates don't make sense for enclaves because enclaves are all about *measurements* and *remote 
   attestations*.
   
   If you use TLS, a client that communicates with an enclave needs to extract a remote attestation from a 
   pseudo-certificate. If you use Noise protocol via Conclave Mail, you can provide a byte array instead of a 
   certificate as part of the handshake.

   In Conclave, the remote attestation data is represented as an
   [`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html) object. The
   Noise protocol and Conclave doesn't have any restrictions on how you get the remote attestation. For example, you can
   send remote attestation to the client by returning it in another API, publishing it on a web server, putting it into
   a distributed hash table, a network drive, or a message queue.

   Following are the benefits of the Noise protocol over an HTTPS/REST architecture:

   * Eliminate complexity from enclave code.
   * Avoid hacks like pseudo-certificates.
   * Enable enclaves to talk to each other even if they can't be loaded simultaneously.
   * Move session management *and expiry* out of the enclave.
   * Get various other benefits, like being able to reuse MQ brokers, integrate with Corda flows, store messages to 
     databases, support M-to-1 inbound message collection before processing and so on.


3. Conclave Mail's approach to enclave restarts and enclave upgrades are more suited than a classical architecture 
   that requires a database to ensure session persistence.
4. The primary reason to use HTTPS/REST is the availability of tools and familiarity. However, none of these 
   tools or libraries understand SGX remote attestation. You need to modify or adjust these tools in complex ways to 
   use, which invalidates most of the benefits.
