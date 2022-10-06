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
using the [Noise protocol specification](https://noiseprotocol.org/noise.html). and
[SHA-256 algorithm].

The encrypted body consists of secure data that you need to transfer between a client and an enclave. Only the 
target enclave can decrypt the encrupted body of a Mail item.

As AES-GCM is an *authenticated* encryption mechanism, you can detect any modifications to encrypted data when you 
use Conclave Mail. 

the key is used not only to encrypt data, but also to
calculate a special hash called a *tag* that allows detection of modifications to the encrypted data. This matters
because 'raw' encryption merely stops someone reading a message: if they already know what some parts of the message
say (e.g. because it's a well known data structure) then by flipping bits in the encrypted data, corruption can be
created in the decrypted message. In some situations these corruptions could change the way the message is parsed leading
to an exploit, for example, [by changing a zero to a one and gaining administrator access](https://paragonie.com/blog/2015/05/using-encryption-and-authentication-correctly).

Like all symmetric ciphers, for AES/GCM to work both sides must know the same key. But we want to be able to send
messages to a target for which we only know a public key, and have never had a chance to securely generate a joint AES
key with. This is the problem solved by the [Elliptic Curve Diffie-Hellman algorithm](https://en.wikipedia.org/wiki/Elliptic-curve_Diffie%E2%80%93Hellman).
To use it, we first select a large random number (256 bits) and then convert that to a coordinate on an elliptic
curve - in our case, we use Curve25519, which is a modern and highly robust elliptic curve. The point on the elliptic curve is our public
key and can be encoded in another 256 bits (32 bytes). We write this public key after the unencrypted envelope. This
key is an *ephemeral* key - we picked it just for the purposes of creating this mail.

We know the target's public key either because the enclave puts it public key into the remote attestation, represented
by an [`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html) object, or because the enclave is replying and obtained the key to reply to from an earlier
mail. Two public keys is all that's needed for both parties to compute the same AES key, without any man-in-the-middle
being able to calculate the same value.

The sender may also have a long term "static" public key that the target will recognise. We append an encryption of this
public key and repeat the calculation between the enclave's public key and the static key, generating a new AES/GCM key
as a result.

!!! note
Even if the sender doesn't have a static key, a random one is generated and used. This is so that every mail
has a valid sender value which allows the enclave to ensure mail is ordered per sender (and topic), as discussed
[previously](#attacks-on-messaging).

As each step in the handshake progresses, a running hash is maintained that mixes in the hash of the prologue and the
various intermediate stages. This allows the unencrypted headers to be tamper-proofed against the host as well, because
the host will not be able to recalculate the authentication tag emitted to the end of the handshake.

Thus, the handshake consists of a random public key, an
authenticated encryption of the sender's public key, and an authentication hash. Once this is done the mail's AES
key has been computed. The initialization vector for each AES encryption is a counter (an exception will be thrown
if the mail is so large the counter would wrap around, as that would potentially reveal information for cryptanalysis,
however this cannot happen given the maximum mail size).

The encrypted body consists of a set of 64kb packets. Each packet has its own authentication tag, thus data is read
from a mail in 64kb chunks. Packets may also contain a true length as distinct from the length of the plaintext. This
allows the sender to artificially inflate the size of the encrypted message by simply padding it with zeros, which
enables hiding of the true message size. Message sizes can be a giveaway to what exact content it contains.

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

**These header fields are available to the host and therefore should not contain secrets**. It may seem odd to have
data that's unencrypted, but it's often useful for the client, host and enclave to collaborate in various ways related
to storage and routing of data. Even when the host is untrusted, it may still be useful for the client to send data
that is readable by the host and the enclave simultaneously, but which the host cannot tamper with. Inside the enclave
you can be assured that the header fields contain the values set by the client, because they're checked before
[`receiveMail`](api/-conclave%20-core/com.r3.conclave.enclave/-enclave/receive-mail.html) is invoked.

In addition to the headers, there is also the _authenticated sender public key_. This is the public key of the client
that sent the Mail item. It's encrypted so that the host cannot learn the client identities. It's called
*authenticated* because you can trust that the Mail item was encrypted by an entity holding the private key matching 
this public key. If your enclave recognises the public key, this feature can be used as a form of user authentication.

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

The encrypted body, the plain-text header, and the envelope header are authenticated such that the enclave can 
detect if they were tampered with.  

### Reordering

The Conclave runtime verifies that the sequence numbers under a topic always increment by one before passing a Mail 
item to the enclave. This feature ensures that the host can't reorder messages or drop Mail items.
 
### Side-channel attacks

A malicious host can infer crucial information if it knows the size or timing of a message.
Conclave guards against these types of [side-channel attacks](security.md#side-channel-attacks) as follows.

Conclave pads Mail items to a uniform size. This blocks attempts to infer the contents of the message based on the 
precise size. By default, Conclave Mail uses a moving average size to pad messages. However, you can configure 
the size of your application's messages to a reasonable upper limit.

To avoid the host guessing information from message timing, you can send empty Mail items even when you have nothing 
to say. For example, if an enclave is running an auction between users, and you wish to hide who won, the enclave 
can send a "you won" or "you lost" message to every client.

## Comparison to a classical architecture using REST and SSL

Conclave uses Conclave Mail to connect clients to enclaves because of the following reasons:

has the following 
advantages when compared to REST and SSL:an HTTP, instead of having clients 
connect into an 
enclave using HTTPS.

!!! note
    The following explanation applies when connecting to the *enclave*. When connecting to the *host* you may use
    whatever transport mechanism you like to pass mail around. Conclave doesn't currently privilege any particular
    transport over any other.


1. Conclave Mail can work with a small [Trusted Computing Base (TCB)](https://en.wikipedia.org/wiki/Trusted_computing_base) 
   when compared to HTTPS. A small TCB removes several common security problems at the origin.
2. The certificate-based architecture of SSL/TLS doesn't gel with enclave-based computing. Conclave Mail uses the 
   better-suited Noise protocol framework.
3. Conclave Mail's approach to restarting and upgrading are more suited for enclave-based computing than a classical 
   architecture that requires a database to ensure session persistence. 
4. The primary reason to use HTTPS+REST is the availability of tools and familiarity. However, none of these 
   tools or libraries understand SGX remote attestation. You need to modify or adjust these tools in complex ways to 
   use, which invalidates most of the benefits.

You can find more information below:

### TCB size

The code that handles encrypted messaging is directly exposed to attackers. It is important to minimize
the amount of code exposed in this way. For more discussion of how enclaves are designed to minimize code size, 
please see ["Small is beautiful"](enclaves.md#small-is-beautiful).  

### Noise Protocol

Conclave Mail is based on the [Noise protocol framework](https://noiseprotocol.org/). The Noise protocol 
delivers most of the same functionalities of Transport Layer Security (TLS) in a cleaner, simpler, and modular manner.

The TLS protocol is reliant on a certificate-based security architecture, which does not suit enclave-based computing. 
Certificates don't make sense for enclaves because enclaves are all about *measurements* and *remote attestations*. 
If you use TLS, a client that communicates with an enclave will need to extract a remote attestation from a 
pseudo-certificate and throw out the rest. If you use Noise protocol via Conclave Mail, you can provide a byte 
array instead of a certificate as part of the handshake.

In Conclave, the remote attestation data is represented as an
[`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html) object. 
It will usually make sense to expose this to the client via some app-specific mechanism, for example, returning it
in some other API, publishing it on a web server, a network drive, a message queue, even putting it into a 
distributed hash table. Noise and by extension Conclave doesn't care how you get this data, only that you have it.
 
  

!!! notice
    This logic applies when connecting to an *enclave*. When connecting to a *host* the domain name identity of the 
    server owner may well matter, and in that case TLS can be useful.

TLS stacks have a history of being extremely buggy. The security TLS supposedly adds has a track record of being 
undermined by bugs in the implementations e.g. Heartbleed. Putting a TLS stack into an enclave makes it much bigger 
and harder to audit. In Conclave, we could use the JSSE Java SSL implementation and thus avoid memory management errors,
but there are other kinds of issues that can be experienced with TLS too, that Java cannot protect us from. We use a
pure Java implementation of Noise which ensures there are no buffer overflows in our crypto stack, and it's small enough
that we have read every line. By licensing Conclave you can get the code, and then you can read every line too. This
is impractical for a TLS stack.

Finally, TLS mandates complex session persistence. This is a subtle point that deserves deeper discussion.

### Sessions and forward secrecy

One of the upgrades Noise gives us is the ability to encrypt in both interactive and one-way modes e.g. for encrypting 
a file. TLS and HTTPS are fundamentally interactive protocols and cannot do this. 

TLS is interactive because the modern way to use it (and the only way in TLS 1.3) is with *forward secrecy*. 
In a scheme with forward secrecy two sides calculate an ephemeral session symmetric key during the handshake, 
that is used only for a short period and then deleted - usually this means the ephemeral key is stored 
only in RAM. 

In theory this is good, because it means if someone hacks the server/enclave they can't decrypt all the old 
messages they might have recorded. The private key is used only to establish identity, not actually encrypt 
any messages. With a purely one-way handshake you don't get this.

Forward secrecy is a rather slippery feature. The problem is simple - what does "short period" mean? 
This is surprisingly hard to answer.

TLS was designed for the web so at first this meant a TCP connection that carried a few web requests. 
So, usually a few seconds or perhaps hours if doing a big download. These days it gets used in many other kinds of 
apps and thus might mean much longer sessions, but fundamentally a TLS session was tied to the lifetime of a 
socket and therefore it couldn't last very long. 

Handshaking to set up a new session key is slow, so various optimisations were introduced. Connections were 
kept alive longer, and TLS added "session resume" that allowed session keys to be saved to disk and continued 
in later connections. For this to actually add any security it has to be harder to steal the cached session 
keys than the identity keys, but why would this be so 
[when they're both stored in ordinary files](https://www.imperialviolet.org/2013/06/27/botchingpfs.html)?

But there's actually an even deeper conceptual problem. TLS encrypts sockets. It has nothing to say about the wider
design of your app - that's out of scope for the people designing TLS. As a consequence TLS sessions don't bear any
resemblance to actual user sessions. The intuition behind forward secrecy is a sort of controlled forgetting ...
when the session ends the keys that could decrypt it are destroyed, so even if a passive observer stops being
passive and hacks someone's private key they can't get the "forgotten" data.

If web apps forgot everything you uploaded for no better reason than you changed from 4G to Wi-Fi people 
would get pretty upset. So HTTP has the idea of cookies, but even that is too transient for most users, 
which is why web apps normally require you to log in to an account. The actual data an attacker cares about 
is tied to the lifetime of an account, not a TCP connection.

To make this work servers must store user session data into a database. This is so obvious and natural 
we hardly think about it, but it largely obliterates the supposed benefits of forward secrecy - the 
private key is held on the server, so if you can steal it you can probably steal the database credentials 
that are also stored on the server. And then you can just steal the valuable user data out of the database, 
you don't need to decrypt TLS sessions anymore.

The biggest risk to an enclave is that the host can read its memory and thus steal its keys. Forward secrecy 
doesn't help you with this because users (we think) won't tolerate an app that forgets stuff if a TCP connection 
resets, and operations teams won't tolerate software that can't be safely restarted. 

So in practice an enclave will immediately re-encrypt data received from a client under a static sealing key, 
and if you can steal a TLS private key out of an enclave you can also steal a sealing key. So it buys you nothing. 
Moreover, it's insufficient to simply seal a byte array. You will probably need some sort of minimal database 
infrastructure, to keep data separated by user and so on. So now you have the complexity of a data management 
layer in the enclave as well.

The result of all this is we don't lose much by using a more asynchronous design in which there isn't an
interactive handshake.

## Benefits of Noise 

By losing TLS we can:

* Eliminate huge complexity from in-enclave code.
* Avoid pseudo-certificates and other hacks that will open up exploits and hit corporate X.509 bureaucracy.
* Enable enclaves to talk to each other even if they can't be loaded simultaneously (e.g. on same machine)
* Move session management *and expiry* out of the enclave, where it can't be done properly anyway because there's no secure access to the clock.
* Get various other benefits, like being able to reuse MQ brokers, integrate with Corda flows, store messages to databases, support M-to-1 inbound message collection before processing and so on.

## Messages vs streams

There are three main ways for networked programs to communicate: streams, messages and RPCs. The popular REST paradigm
is a form of RPC for the purposes of this discussion.

Multi-party computations all involve the transmission of relatively small messages, often without needing any
immediate reply. For instance, over the course of a day parties may submit a small data structure when a human gets
around to it, and get the results back hours later when a deadline or threshold is reached. In other cases
submission of data to an enclave may be asynchronous and should not block a business process in case of outage.

These requirements are not a good fit for stream-oriented connections that when on top of TCP require app
developers to do their own framing (a frequent source of security vulnerabilities), can break due to NAT boxes 
timing them out, IP address changes, passing through one-way firewalls, require high-availability servers with 
load balancing, require databases for stored state and so on.

With a stream or RPC oriented approach the enclave would be expected to respond to bytes from a client more or less
immediately, meaning it has to be always running and would have to persist uploaded data itself. Unfortunately
persistence in enclaves is tricky due to the existence of [Memento attacks](security.md#mementorewindreplay-attacks).

RPCs (including REST) can be hard to integrate with fully reliable systems that can tolerate restarts at any moment.
Because server restarts break connections, in a loosely coupled multi-party system connection-oriented protocols
require expensive HA load balancing to ensure users aren't often exposed to errors caused by planned restarts. Even
then it isn't possible to eliminate such errors entirely, just reduce their impact. Load balancing in an enclave
environment requires special support because the client is intimately aware of the exact *CPU* the enclave is
running on - thus switching a client between servers isn't transparent. 

!!! note
    Future versions of Conclave are planned to add support for enclave clustering. 

All this suggests the primary paradigm exposed to enclaves should be messages. The host will take care of
delivering messages and providing atomic, transactional semantics over them so many kinds of enclaves won't need an
encrypted database at all. The untrusted host can also take on the significant burden of moving data around,
storing it to disk, sorting it, applying backpressure, exposing to the admin if there are backlogs etc.
