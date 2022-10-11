# Conclave Mail

Conclave uses *Conclave Mail*, a communication mechanism that allows you to send encrypted messages to enclaves that 
can be decrypted only by enclaves that you trust. Conclave Mail ensures secure communication between a client and an 
enclave.

## How Conclave Mail works

Conclave Mail is essentially an authenticated byte array. A Mail item consists of five parts:

1. A protocol ID.
2. A plain-text header.
3. A plain-text envelope.
4. The handshake.
5. The encrypted body.

The protocol ID, the unencrypted header, and the envelope form the *prologue* of a Mail item.

The handshake consists of the following items:

* A random public key.
* An authenticated encryption of the sender's public key.
* An authentication hash.

Conclave uses the contents of the handshake to do an [Elliptic-curve Diffie-Hellman](https://cryptobook.nakov.com/asymmetric-key-ciphers/ecdh-key-exchange)
calculation and derive a unique [AES-GCM](https://www.cryptosys.net/pki/manpki/pki_aesgcmauthencryption.html) key
using the [Noise protocol](https://noiseprotocol.org/noise.html) and the
[SHA-256](https://www.simplilearn.com/tutorials/cyber-security-tutorial/sha-256-algorithm) algorithm.

AES-GCM is an *authenticated* encryption mechanism. Along with encrypting data, the key is also used to calculate a 
unique hash called a *tag*. Any change to the message will also change the *tag*. This hashing mechanism prevents 
attackers from changing the contents of a message without the enclave noticing it.

Like all symmetric ciphers, for AES-GCM to work, both sides must know the same key. In cases where the client 
knows only a public key, Conclave Mail uses the Elliptic-curve Diffie-Hellman algorithm. The elliptic curve used in 
Conclave Mail is [Curve25519](https://en.wikipedia.org/wiki/Curve25519).

The client knows the target's public key either because the enclave puts its public key into the remote attestation, 
represented by an [`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html)
object, or because the enclave obtained the key from an earlier Mail item. Two public keys are all that both 
parties need to compute the same AES key without any man-in-the-middle being able to calculate the same value.

The sender may also have a long-term static public key that the target will recognize. Conclave appends an 
encryption of this public key and repeat the calculation between the enclave's public key and the static key, 
generating a new AES-GCM key as a result.

The handshake process maintains an authentication hash that mixes in the hash of the prologue and the various 
intermediate stages. This authentication hash allows the unencrypted headers to be tamper-proof against the host, as 
the host will not be able to recalculate it.

The initialization vector for each AES encryption is a counter. To avoid revealing any information, Conclave throws 
an exception if the Mail item exceeds the limit of the counter.

The encrypted body consists of secure data that you need to transfer between a client and an enclave. Only the
target enclave can decrypt the encrypted body of a Mail item. The encrypted body consists of a set of 64KB packets. 
Each packet has its authentication tag.

## Features of Conclave Mail

Conclave Mail provides various features that are useful when building secure applications.

### Encryption

The encryption key used by an enclave is private to that enclave. The encryption key is stable across system restarts 
and enclave upgrades. So, enclaves can decrypt any message encrypted and delivered by older clients. The format
uses the respected Noise protocol framework with AES-GCM and SHA-256.

### Authentication

Conclave Mail enables recipients to prove that a message came from the owner of a particular key. If a key can 
identify a user, you can use Conclave Mail's envelope to authenticate that user cryptographically. You can use this 
feature to securely implement your application's login and authentication processes outside the enclave.

### Message headers

Conclave Mail's plain-text headers are authenticated and tamper-proof. Clients can use these headers to connect 
messages to structure a conversation logically. Clients can also hold multiple conversations simultaneously using 
message headers. You can use message headers to implement non-data-processing tasks like usage tracking and 
prioritization at the host's end. The Mail headers contain the following fields:

1. The _topic_. This is a string you can use to distinguish between different streams of Mail items from the same 
   client. It resembles an email's subject line. Topics are scoped per sender and are not global. Clients
   can send multiple streams of related Mail items using a different topic for each stream. To avoid replay attacks, 
   you should never reuse a topic for an unrelated Mail item. Using a random UUID in a topic is good practice to 
   prevent reuse.

2. The _sequence number_. Every Mail item with a specific topic has a sequence number that starts from zero and 
   increments by one with each Mail item. The enclave rejects messages if the sequence number is not in order. This 
   ensures that the enclave 
   receives a stream of related Mail items in the correct order.

3. The _envelope_. This field can hold any plain-text byte array. You can use it to hold app-specific data that 
   should be authenticated but unencrypted.

The header fields should not contain secrets, as these are available to the host. It may seem odd to have unencrypted 
data, but it's useful for the client, the host, and the enclave to collaborate for data storage and routing. Even when 
the host is untrusted, it may still be useful for the client to send data that is readable by the host and the enclave 
simultaneously but which cannot be modified by the host. Inside the enclave, you can be sure that the header 
fields contain the values set by the client because they're checked before
[`receiveMail`](api/-conclave%20-core/com.r3.conclave.enclave/-enclave/receive-mail.html) is invoked.

In addition to the headers, there is also the _authenticated sender public key_. This is the public key of the client
that sent the Mail item. It's encrypted so that the host cannot learn the client's identity.

### Framing

Conclave Mail delimits messages so that you can always tell where they begin and end without imposing your own framing.
This in-built framing prevents the host from tampering with the messages by detecting end-of-message characters. 

## How Conclave Mail prevents attacks on messaging

Conclave Mail is designed to block various attacks the host can mount on the enclave.

### Observation

The body of a Mail item is encrypted with industry-standard AES-GCM. The host can't see what the client is sending 
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

Conclave pads Mail items to a uniform size. This blocks any attempt to infer the contents of messages based on the 
precise length of a message. By default, Conclave Mail uses a moving average size to pad messages. However, you can 
configure the size of your application's messages to a reasonable upper limit.

To avoid the host guessing information from message timing, you can send empty Mail items even when you have nothing 
to say. For example, if an enclave is running an auction between users, and you wish to hide who won, the enclave 
can send a "you won" or "you lost" message to every client.

## Comparison to a classical public key infrastructure

Conclave uses Conclave Mail to connect clients to enclaves because of the following reasons:

1. Compared to HTTPS, Conclave mail has a relatively small [Trusted Computing Base (TCB)](https://en.wikipedia.org/wiki/Trusted_computing_base).
   The small TCB helps to harden enclaves against potential [zero-day](https://en.wikipedia.org/wiki/Zero-day_(computing))
   exploits that may exist in dependency libraries.

2. The certificate-based architecture of SSL/TLS doesn't integrate cleanly with enclave-based computing. Conclave Mail
   uses the better-suited Noise protocol framework. The Noise protocol delivers most of the functionalities of Transport
   Layer Security (TLS) in a cleaner, simpler, and modular manner.

   The TLS protocol is reliant on a certificate-based security architecture, which does not suit enclave-based
   computing. Certificates don't make sense for enclaves because enclaves are all about *measurements* and *remote
   attestations*.

   If you use TLS, a client communicating with an enclave needs to extract a remote attestation from a
   pseudo-certificate. As Conclave Mail uses the Noise protocol, you can provide a byte array instead of a
   certificate as part of the handshake.

   In Conclave, the remote attestation data is represented as an
   [`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html) object. The
   Noise protocol and Conclave doesn't restrict how you get the remote attestation. For example, you can
   send remote attestation to the client by returning it in another API, publishing it on a web server, and putting it
   into a distributed hash table, network drive, or message queue.

   Following are the benefits of the Noise protocol over an HTTPS/REST architecture:

   * Eliminate complexity from enclave code.
   * Avoid hacks like pseudo-certificates.
   * Enable enclaves to talk to each other even if they can't be loaded simultaneously.
   * Move session management *and expiry* out of the enclave.
   * Get other benefits like reusing MQ brokers, integrating with Corda flows, storing messages to databases, and
     supporting M-to-1 inbound message collection.
   
3. The primary reason to use HTTPS/REST is the availability of tools and libraries. To use these preexisting tools
   for remote attestation, you must modify them in unusual ways that they were not designed for, which introduces
   design complexities and reduces their usefulness.



   
    


