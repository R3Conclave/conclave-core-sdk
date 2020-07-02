# TLS/SSL vs Mail

Let's look at why Conclave provides Mail, instead of having clients build TLS sockets into a running enclave.

## TCB size

The software industry has decades of experience with building secure and tamperproof systems. A simple heuristic is
that as the more code there is inside an enclave, the easier it will be for an adversary to find a mistake and break 
in. Obviously, this heuristic is only true for code that an attacker can actually reach and influence via external
input, which is why having a JVM inside the enclave is a benefit rather than a cost (the input data to the JVM is 
itself a fixed part of the enclave that attackers can't modify without being detected). But the general principle
remains - small is beautiful.

The software and hardware that must be uncompromised for a system to work correctly is called the 
*trusted computing base*, or TCB. Thus a goal in secure systems design is to minimise the size of the TCB. Intel SGX
is good at this because an enclave is designed to be the smallest piece of application logic that needs to be
protected, rather than earlier approaches of e.g. attempting to protect an entire operating system stack. 

## TLS complexity

Mail is based on the [Noise protocol framework](https://noiseprotocol.org/). Noise is designed by professional 
cryptographers and is used in WhatsApp, WireGuard and other modern network protocols. Noise was created because 
TLS is weighed down with complicated historical baggage and web-specific features. A Noise protocol delivers most of 
the same functionality but in a more modular, cleaner and simpler way. Therefore the primary reason to use TLS is 
compatibility, not technical excellence. Now there is Noise, when designing a new protocol from scratch there isn't 
much reason to use TLS anymore unless you really need its features or support.

TLS 1.3 recognises this problem and simplifies the protocol. But it's still far more complex than a Noise protocol, 
and because the purpose of using TLS is compatibility and TLS 1.3 is very new, realistically you will support TLS 1.2 
as well. So now you have even more complexity in your enclave because you need both 1.2 and 1.3 support 
simultaneously.

A big source of complexity in TLS is certificates. Certificates do not make sense for an enclave but TLS 
absolutely requires them, so alternative platforms all find various ways to jam fake pseudo-certificates into
the system. This can create problems because a lot of organisations have decades of procedures and best practices
around all aspects of certificates and X.509, so pseudo-certificates can end up harder to deploy and work with
than a purely technical analysis would suggest.

Certificates don't make sense because TLS is all about *verifiable names* as a form of identity, but enclaves 
are all about *measurements* and *remote attestations*. Thus any client tool that communicates with an enclave will 
need to extract a remote attestation from a pseudo-certificate and throw out the rest. Given that you need complex 
custom client-side code anyway, TLS buys you nothing beyond brand recognition.

!!! notice
    This logic applies when connecting to an *enclave*. When connecting to a *host* the identity of the hosting
    provider may well matter, and in that case TLS to the host process can be useful.

TLS stacks have a history of being extremely buggy. The security TLS supposedly adds has a long history of being 
undermined by bugs in the implementations e.g. Heartbleed. Putting a TLS stack into an enclave makes it much bigger 
and harder to audit. In Conclave we can use the JSSE Java SSL implementation and thus avoid memory management errors,
but there are other kinds of issues that can be experienced with TLS too, that Java cannot protect us from.

Finally, TLS mandates complex session persistence. This is a subtle point that deserves deeper discussion.

## Sessions and forward secrecy

One of the upgrades Noise gives us is the ability to encrypt in both interactive and one-way modes e.g. for encrypting 
a file. But TLS and HTTPS are fundamentally interactive protocols. 

TLS is interactive because the modern way to use it (and the only way in TLS 1.3) is with *forward secrecy*. 
In a scheme with forward secrecy two sides calculate an ephemeral session symmetric key during the handshake, 
that is used only for a short period of time and then deleted - usually this means the ephemeral key is stored 
only in RAM. 

In theory this is good, because it means if someone hacks the server/enclave they can't decrypt all the old 
messages they might have recorded. The private key is used only to establish identity, not actually encrypt 
any messages.

Forward secrecy is a rather slippery feature. The problem is simple - what does "short period of time" mean? 
This is surprisingly hard to answer.

TLS was designed for the web, so at first this meant a TCP connection that carried one or a few web requests. 
So usually a few seconds or perhaps hours if doing a big download. These days it gets used in many other kinds of 
apps and thus might mean much longer sessions, but fundamentally a TLS session was tied to the lifetime of a 
socket, so it couldn't last very long. 

But handshaking to set up a new session key is slow, so various optimisations were introduced. Connections were 
kept alive longer, and TLS added "session resume" that allowed session keys to be saved to disk and continued 
in later connections. For this to actually add any security it has to be harder to steal the cached session 
keys than the identity keys, but why would this be so 
[when they're both stored in ordinary files](https://www.imperialviolet.org/2013/06/27/botchingpfs.html)?

But there's actually an even deeper conceptual problem. TLS encrypts sockets. It has nothing to say about 
the wider design of your app - that's out of scope for the people designing TLS. So TLS sessions don't bear 
any resemblance to actual user sessions. The intuition behind forward secrecy is a sort of controlled 
forgetting ... when the session ends, the keys that could decrypt it are destroyed, so even if a passive 
observer stops being passive and hacks someone's private key, they can't get the "forgotten" data.

If web apps forgot everything you uploaded for no better reason than you changed from 4G to WiFi people 
would get pretty upset. So HTTP has the idea of cookies but even that is too transient for most users, 
so web apps normally require you to log in to an account. The actual data you care about is tied to the 
lifetime of the account, not a TCP connection.

To make this work, servers must store user session data into a database. This is so obvious and natural 
we hardly think about it, but in fact it largely obliterates the benefits of forward secrecy - the 
private key is held on the server, so if you can steal it, you can probably steal the database credentials 
that are also stored on the server. And then you can just steal the valuable user data out of the database, 
you don't need to decrypt TLS sessions anymore.

## TLS in enclaves

The biggest risk to an enclave is that the host can read its memory and thus steal its keys. Forward secrecy 
doesn't help you with this because users (we think) won't tolerate an app that forgets stuff if a TCP connection 
resets, and operations teams won't tolerate software that can't be safely restarted. 

So in practice an enclave will immediately re-encrypt data received from a client under a static sealing key, 
and if you can steal a TLS private key out of an enclave you can also steal a sealing key. So it buys you nothing. 
Moreover, it's insufficient to simply seal a byte array. You will probably need some sort of minimal database 
infrastructure, to keep data separated by user and so on. So now you have the complexity of a data management 
layer in the enclave as well.

By losing TLS we can:

* Eliminate huge complexity from in-enclave code.
* Avoid pseudo-certificates and other hacks that will run into X.509 bureaucracy.
* Enable enclaves to talk to each other even if they can't be loaded simultaneously (e.g. on same machine)
* Move session management *and expiry* out of the enclave, where it can't be done properly anyway because there's no secure access to the clock.
* Get various other benefits, like being able to reuse MQ brokers, integrate with Corda flows, store messages to databases, support M-to-1 inbound message collection before processing and so on.

## Messages vs streams

There are three main ways for networked programs to communicate: streams, messages and RPCs.

Multi-party computations all involve the transmission of relatively small messages, often without needing any
immediate reply. For instance, over the course of a day parties may submit a small data structure when a human gets
around to it, and get the results back hours later when a deadline or threshold is reached. In other cases
submission of data to an enclave may be asynchronous and should not block a business process in case of outage.
These requirements are not a good fit for stream-oriented connections that (when on top of TCP) require app
developers to do their own framing, can break due to NAT boxes timing them out, IP address changes, passing through
one-way firewalls, require high-availability servers with load balancing, require databases for stored state and so on.
Many businesses have sophisticated infrastructure for message routing and persistence between firms already in place.

Enclaves doing multi-party computations may often wish to process a collection of messages all at once. For instance all messages submitted to
a shared calculation, or a run of messages sent by a client for aggregation. Buffering messages and storing them to
disk is not security sensitive and doesn't need to be remotely attested, which implies the host should be able to
take care of this on its own. Putting a normal serving stack inside the enclave increases its size significantly, which
in turn increases audit costs and the risk of security vulnerabilities (see "[Small is beautiful](enclaves.md#small-is-beautiful)").
The more the host can take care of by itself the better.

With a stream or RPC oriented approach the enclave would be expected to respond to bytes from a client more or less
immediately, meaning it has to be always running and would have to persist uploaded data. But persistence in
enclaves is tricky due to the existence of Memento attacks (see "[Handling time and state](enclaves.md#handling-time-and-state)").

Additionally, RPCs can be hard to integrate with fully reliable systems that can tolerate restarts at any moment.
Because server restarts break connections, in a loosely coupled multi-party system connection-oriented protocols
require expensive HA load balancing to ensure users aren't often exposed to errors caused by planned restarts. Even
then it isn't possible to eliminate such errors entirely, just reduce their impact.

All this suggests the primary paradigm exposed to enclaves should be messages. The host will take care of
delivering messages and providing atomic, transactional semantics over them, so many kinds of enclave won't need an
encrypted database at all. The untrusted host can also take on the significant burden of moving data around,
storing it to disk, sorting it, applying backpressure, exposing to the admin if there are backlogs etc.
