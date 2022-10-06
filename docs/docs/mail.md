# Mail

Conclave uses *Conclave Mail* to securely communicate between the client and the enclave. It is an authenticated byte 
array with an encrypted body and a cleartext envelope. The mail can be up to two
gigabytes in size, however, it must at this time fit into memory when loaded all at once. In practice mails will
usually be much smaller than that. 

## Mail features

Conclave Mail provides a variety of features that are useful when building secure applications.

### Encryption

The encryption key used by the enclave is private to that enclave but stable across restarts and
enclave upgrades. This means messages encrypted and delivered by older clients can still be decrypted. The format
uses the respected [Noise protocol framework](https://noiseprotocol.org/) (with AES/GCM and SHA-256), which is
the same cryptographic framework used by WhatsApp, the Linux kernel WireGuard protocol and I2P.

### Authentication

Mail allows a message to prove it came from the owner of a particular key, as well as being
encrypted to a destination public key. Because of this it's easy to encrypt a reply to the sender,
although how a public key is mapped to a physical computer is up to the app developer. This capability is useful
for another reason: if an enclave has a notion of a user identifiable by public key, mail can be cryptographically
authenticated as having come from that user. This avoids the need for complex user login processes: the login
process can be handled outside of the enclave, without compromising security. The envelope is protected this way
along with the encrypted body.

### Message headers

Mail has unencrypted but authenticated (tamperproof) headers that can be used to link messages together.
This allows clients to structure a conversation as if they were using a socket, but also hold multiple conversations
simultaneously. This can be used to implement usage tracking, prioritisation, or other tasks not directly 
relevant to data processing that the host can assist with (despite its untrusted nature). The mail headers contain the
following fields:

1. A _topic_. This can be used to distinguish between different streams of mail from the same client. It's a string and
   can be thought of as equivalent to an email subject. Topics are scoped per-sender and are not global. The client can
   send multiple streams of related mail by using a different topic for each stream, and it can do this concurrently.
   The topic is not parsed by Conclave and, to avoid replay attacks, should never be reused for an unrelated set of
   mail items in the future. A good value might thus contain a random UUID. Topics may be logged and used by your
   software to route or split mail streams in useful ways.
1. The _sequence number_. Starting from zero, this is incremented by one for every mail delivered on a topic. Conclave will
   automatically reject messages if this doesn't hold true, thus ensuring to the client that the stream of related
   mail is received by the enclave in the order they were sent, and that the host is unable to re-order or drop them.
1. The _envelope_. This is a slot that can hold any arbitrary byte array the sender likes. It's a holding zone for
   app specific data that should be authenticated but unencrypted.

**These header fields are available to the host and therefore should not contain secrets**. It may seem odd to have
data that's unencrypted, but it's often useful for the client, host and enclave to collaborate in various ways related
to storage and routing of data. Even when the host is untrusted it may still be useful for the client to send data
that is readable by the host and enclave simultaneously, but which the host cannot tamper with. Inside the enclave
you can be assured that the header fields contain the values set by the client, because they're checked before
[`receiveMail`](api/-conclave%20-core/com.r3.conclave.enclave/-enclave/receive-mail.html) is invoked.

In addition to the headers, there is also the _authenticated sender public key_. This is the public key of the client
that sent the mail. Like the body, it's encrypted so that the host cannot learn the client identities. It's called
"authenticated" because the encryption used by Conclave means you can trust that the mail was encrypted by an entity
holding the private key matching this public key. If your enclave recognises the public key this feature can be used as
a form of user authentication.

### Framing

A typical need with any socket or stream-based transport is to add framing on top, because the application
really needs to work with messages. In textual protocols, framing can be remarkably tricky to get right, as user 
or attacker controlled data may be placed inside a message, meaning any characters that mark the end of a message need
to be escaped. This can go wrong in [a variety of interesting ways](https://www.blackhat.com/docs/us-17/thursday/us-17-Tsai-A-New-Era-Of-SSRF-Exploiting-URL-Parser-In-Trending-Programming-Languages.pdf). Mail by its nature delimits
messages such that you can always tell where they begin and end, without needing to impose your own framing on top.

## Attacks on messaging

Mail is designed to block a variety of attacks the host can mount on the enclave.

### Observation

The body of the mail is encrypted with industry standard AES/GCM. The host can't see what the client
is sending to the enclave.

### Tampering

All the encrypted body, the plaintext headers and the user-specifiable envelope header are
authenticated such that the enclave can detect if they were tampered with. See below for more information on this.  

### Reordering

The enclave can't directly access any hardware other than the CPU and RAM. That means it can't know
that messages came from the network card or hard disk in the right order, or that no messages were dropped. 
Mails include a sequence number and topic in the headers, meaning it is *visible* to the host but can't be tampered with. This
enlists the client as an ally in the enclave's war against the host: the client wants its messages to be delivered in
the right order and without being dropped. The Conclave runtime checks the sequence numbers in the headers always
increment, on a per-topic basis, before passing the mail to your code.

!!! note
    The host may arbitrarily delay or even refuse to deliver mail, but it can only end the stream of mails early, it can't
    re-order messages relative to each other. This is a feature not a bug, as if the enclave could force the host to
    deliver mail that would imply it had actually taken over the computer somehow and was forcing it to provide services
    to the enclave. SGX isn't a form of remote control and nobody can force a host to run enclaves against its will.

### Size side channels

Simply knowing how big a message is can be [surprisingly powerful](https://www.schneier.com/blog/archives/2010/03/side-channel_at.html).
Mail is automatically padded by Conclave to give messages a uniform size. This blocks attempts to infer the contents of
the message based on the precise size. By default, Mail uses a moving average but the policy is configurable and so if
you know a reasonable upper limit on the size of your messages, you can pad every message to be that size. The host will
be blinded and have to try and infer what's going on just from message timing. You can fix that by sending empty
mails even when you have nothing to say. For example, if an enclave is running some sort of auction or competition 
between users and you wish to hide who won, the enclave can simply send a "you won" or "you lost" message to every
client. Even if the winner needs additional data the padding will ensure the host can't tell which client won.

## How does Mail work

When viewed as a structured array of bytes, Mail consists of five parts:

1. The protocol ID.
1. The unencrypted headers, which have fields simply laid out in order.
1. The unencrypted envelope, which is empty by default and exists for you to add whatever data you like (i.e. additional
   headers).
1. The handshake.
1. The encrypted body.

Let's call the protocol ID, unencrypted headers and envelope the *prologue*. 

At the core of Mail's security is the handshake. It consists of the data needed to do an elliptic curve Diffie-Hellman
calculation and derive a unique (per mail) AES/GCM key, using the 
[Noise protocol specification](https://noiseprotocol.org/noise.html). 

AES/GCM is an *authenticated* encryption mechanism. That means the key is used not only to encrypt data, but also 
calculate a special hash called a "tag" that allows detection of modifications to the encrypted data. This matters 
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

## Comparison to a classical architecture using REST and SSL

Let's look at why Conclave provides Mail, instead of having clients connect into an enclave using HTTPS.

!!! note
    The following explanation applies when connecting to the *enclave*. When connecting to the *host* you may use
    whatever transport mechanism you like to pass mail around. Conclave doesn't currently privilege any particular
    transport over any other.

We provide the mail paradigm for these reasons:

1. HTTPS requires a relatively large and complex infrastructure, increasing the size of the TCB.
   It can go wrong in ways that aren't intuitive. Mail attempts to solve some common security problems before they start.
1. SSL/TLS is not well adapted to enclaves, for example certificates don't make sense, but the protocol requires them.
1. Web servers require the backing of a database to ensure session persistence and atomicity, but this interacts 
   badly with the way enclaves handle time and state. Mail allows for a very simple approach to restarting and upgrading
   enclaves.
1. The primary reason to use HTTPS+REST is familiarity and installed base of tools. However, none of these tools or
   libraries understand SGX remote attestation so cannot be used in their regular modes and must be modified or 
   adjusted in complex ways, thus invalidating most of the benefits.

We'll now explore these issues in more depth.

### TCB size

The code that handles encrypted messaging is directly exposed to attackers. It is important to minimise
the amount of code exposed in this way. For more discussion of how enclaves are designed to minimise code size, 
please see ["Small is beautiful"](enclaves.md#small-is-beautiful).  

### TLS complexity

Mail is based on the [Noise protocol framework](https://noiseprotocol.org/). Noise is designed by professional
cryptographers and is used in WhatsApp, WireGuard and other modern network protocols. Noise was created because TLS
is weighed down with complicated historical baggage and web-specific features. A Noise protocol delivers most of
the same functionality but in a more modular, cleaner and simpler way. Therefore, outside of the web the primary
reason to use TLS is compatibility, not technical excellence. Now there is Noise there isn't much reason to use TLS
anymore when designing a new protocol from scratch, unless you really need its features or support.

TLS 1.3 recognises this problem and simplifies the protocol, but it's still far more complex than a Noise protocol, 
and because the purpose of using TLS is compatibility and TLS 1.3 is very new, realistically you will support TLS 1.2 
as well. So now you have even more complexity in your enclave because you need both 1.2 and 1.3 support 
simultaneously.

A big source of complexity in TLS is X.509 certificates. Certificates do not make sense for an enclave but TLS 
absolutely requires them, so some systems try to jam fake pseudo-certificates into TLS, which aren't real but
contain the remote attestation in an extension field. Fake certificates can create problems because a lot of 
organisations have decades of procedures and best practices around all aspects of certificates and X.509, 
so fake pseudo-certificates can end up harder to deploy and work with than a purely technical analysis would suggest.
Noise is more modular - you can provide any arbitrary byte array as part of the handshake, which can thus 
include a certificate (of any format) if you want that, but it isn't necessary.

In Conclave the "certificate" is the remote attestation data represented as an [`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html) object. 
It will usually make sense to expose this to the client via some app-specific mechanism, for example, returning it
in some other API, publishing it on a web server, a network drive, a message queue, even putting it into a 
distributed hash table. Noise and by extension Conclave doesn't care how you get this data, only that you have it.
 
Certificates don't make sense for enclaves because TLS is all about *verifiable names* as a form of identity, but
enclaves are all about *measurements* and *remote attestations*. Thus, any client tool that communicates with an
enclave will need to extract a remote attestation from a pseudo-certificate and throw out the rest. Given that you
would need complex custom client-side code anyway, TLS buys you little beyond brand recognition.  

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
persistence in enclaves is tricky due to the existence of [Memento attacks](security.md#memento-attacks).

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
