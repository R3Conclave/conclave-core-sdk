# Architecture overview

There are three entities in an application that uses Conclave:

1. Enclaves
2. Hosts
3. Clients

**Clients** send and receive encrypted messages to/from enclaves by interacting with the host over the network.
Conclave doesn't mandate any particular network protocol for client<->host communication. It's up to you. However
the content of the messages *is* defined, using the [Mail](#mail) API. Mail is described below.

**Host programs** load enclaves. From a security perspective they are fully untrusted and assumed to be malicious at all
times. Hosts are relied on to provide the enclave with resources but beyond that work only with encrypted data. In some
kinds of app their function is primarily proxying communications from clients to enclaves, but sometimes they also assist
with application logic. Hosts use a standard JVM like HotSpot.

**Enclaves** are classes that are loaded into a dedicated sub-JVM with a protected memory space, running inside the same
operating system process as the host JVM. Because they don't share heaps the host may exchange only byte buffers with
the enclave. Direct method calls also don't work out of the box: that would require you to add some sort of RPC system
on top. In this way it's similar to interacting with a server over a network, except the enclave is fully local.
Enclaves run on a specialised JVM suited for the small enclave environment. In current Conclave versions that JVM is
called Avian. In future versions we plan to upgrade to SubstrateVM, which will bring various advantages.

![Architecture Diagram](arch-diagram.png)

In the above diagram orange shaded boxes are untrusted and could attack the enclave: the host and operating system
(which includes the BIOS, drivers and peripherals). Blue shaded boxes are part of the _trusted computing base_ -
the set of components that must be correct and non-malicious for the system to work. That includes the enclave and
of course the CPU.  The client communicates with the enclave via the host. Both client and host interact with
Intel servers to obtain relevant pieces of data proving the CPU is genuine and considered secure (see below for more
information on this process). The enclave has a complex interaction with both operating system and host, in which the
OS schedules the enclave onto the CPU and provides resources but is otherwise locked out of the enclave's operation.
For its part, the enclave cannot interact with the OS directly and runs in what is effectively a "bare metal" embedded
style environment. It cannot load DLLs/.so files or do system calls, so there's no way for it to do things like load
files directly. It must ask the host to do it and use cryptography to prevent the malicious host from tampering with
the data as it's loaded or saved.

!!! notice

    Because the enclave JVM isn't the same as a normal HotSpot JVM, you will need to test your enclave code
    carefully and avoid advanced features that the embedded JVM doesn't support, such as Java Flight Recorder.

## Remote attestation

To use an enclave clients need to know the network address where the host process for an enclave instance is
running so they can send it messages, and they also need to obtain an `EnclaveInstanceInfo` object from the host.
This could be downloaded on demand from the host, or it could be published somewhere. This object encapsulates a
_remote attestation_ from the host. They test the `EnclaveInstanceInfo` against a set of constraints, depending on
how flexible they want to be about software upgrades to the enclave. When they're happy they can create
`EnclaveMail` which encapsulates an encrypted message. By serializing and delivering the `EnclaveMail` object to
the host (and from there to the enclave), communication is established. See the [Mail](#mail) section below.

Whilst the high level setup has just those three entities, real deployments have more:

1. Intel
2. Optionally, a cloud provider
3. Optionally, an auditor

**Intel.** Intel's involvement in a deployed architecture is limited to providing the CPU hardware and running some servers.
These servers provision the host with certificates guaranteeing authenticity of the chip (once, on initial setup)
and provide the client with an assessment of the security of the host given a remote attestation (whenever the
client requests it). If Intel's servers become unreachable by the clients that's not a problem, it just means the
security of the host machine may degrade without the clients realising.

For instance if host's SGX software stack is out of date and has known vulnerabilities, or if the BIOS configuration is
not correct, this will be reported to the client by Intel's servers as part of verifying and processing the data inside
the remote attestation provided by the client.

**Cloud provider.** A cloud provider needs to support SGX for Conclave to be usable. They may operate their own
provisioning servers that take over from Intel's.

!!! notice

    Cloud providers running their own provisioning servers is a new feature of SGX and not yet supported by Conclave.

**Auditor.** In the pure enclave-oriented model, the user is responsible for understanding what the enclave does before
using it by reading the enclave's source code. If the user doesn't do this then enclaves have no point. In practice the
user may wish to outsource this auditing to someone else.

## Protocol sequence diagram

This is what a typical interaction looks like:

<!---

https://mermaid-js.github.io/mermaid-live-editor/#/edit/eyJjb2RlIjoic2VxdWVuY2VEaWFncmFtXG4gICAgXG4gICAgcGFydGljaXBhbnQgRW5jbGF2ZVxuICAgIHBhcnRpY2lwYW50IENsb3VkL0ludGVsXG4gICAgcGFydGljaXBhbnQgSG9zdFxuICAgIHBhcnRpY2lwYW50IENsaWVudFxuICAgIHBhcnRpY2lwYW50IEludGVsXG4gICAgXG4gICAgcmVjdCByZ2JhKDI1NSwgMCwgMjU1LCAwLjEpXG4gICAgICAgIE5vdGUgbGVmdCBvZiBIb3N0OiBPbiBpbml0aWFsIGRlcGxveVxuICAgICAgICBDbG91ZC9JbnRlbC0-Pkhvc3Q6IFByb3Zpc2lvbmluZyBjZXJ0c1xuICAgIGVuZFxuICAgIEhvc3QtPj5DbGllbnQ6IFNlbmQgcmVtb3RlIGF0dGVzdGF0aW9uXG4gICAgTm90ZSBvdmVyIENsaWVudDogVmVyaWZ5IG1lYXN1cmVtZW50XG4gICAgb3B0IE9jY2FzaW9uYWxseVxuICAgICAgICBDbGllbnQtLT4-SW50ZWw6IFJlcXVlc3QgYXNzZXNzbWVudFxuICAgICAgICBJbnRlbC0tPj5DbGllbnQ6IEhvc3Qgc3RpbGwgc2VjdXJlIVxuICAgIGVuZFxuICAgIENsaWVudC0-Pkhvc3Q6IEVuY3J5cHRlZCBtZXNzYWdlIG92ZXIgSVBcbiAgICBIb3N0LT4-RW5jbGF2ZTogRW5jcnlwdGVkIG1lc3NhZ2VcbiAgICBFbmNsYXZlLT4-SG9zdDogRW5jcnlwdGVkIHJlc3BvbnNlXG4gICAgSG9zdC0-PkNsaWVudDogRW5jcnlwdGVkIHJlc3BvbnNlIG92ZXIgSVAiLCJtZXJtYWlkIjp7InRoZW1lIjoiZGVmYXVsdCJ9LCJ1cGRhdGVFZGl0b3IiOmZhbHNlfQ
sequenceDiagram

    participant Enclave
    participant Cloud/Intel
    participant Host
    participant Client
    participant Intel

    rect rgba(255, 0, 255, 0.1)
        Note left of Host: On initial deploy
        Cloud/Intel->>Host: Provisioning certs
    end
    Host->>Client: Send remote attestation
    Note over Client: Verify measurement
    opt Occasionally
        Client->>Intel: Request assessment
        Intel->>Client: Host still secure!
    end
    Client->>Host: Encrypted message over IP
    Host->>Enclave: Encrypted message
    Enclave->>Host: Encrypted response
    Host->>Client: Encrypted response over IP-->

![sequence diagram](sequence.png)

The first time SGX is used on a machine there are interactions with either the cloud provider or Intel to retrieve
machine certificates proving authenticity. The host then gets a remote attestation (`EnclaveInstanceInfo`) to the
client somehow, the client verifies it and optionally asks Intel if the hardware setup of the machine is still
considered to be secure, or if there are known vulnerabilities (see [TCB recovery](renewability.md)). This can be
repeated as often as the client wants, e.g. every day. Once this is done the client can send messages to the enclave
through the host.

## Mail

Communicating with an enclave requires sending and receiving encrypted and authenticated messages. One possible approach
is to embed a TLS stack into the enclave and use something like HTTPS + REST. But this technique has some problems
and limitations that are resolved via Conclave's Mail API.

### Messages vs streams

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

## Encrypted, authenticated and transactional messages

!!! notice

    Mail is not implemented in the current release of Conclave.

A mail is an authenticated message with an encrypted body and a cleartext envelope. The mail can be up to two
gigabytes in size.

**Authentication.** Mail allows a message to prove it came from the owner of a particular key, as well as being
encrypted to a destination public key. Because of this it's always possible to encrypt a reply to the sender,
although how a public key is mapped to a physical computer is up to the app developer. This capability is useful
for another reason: if an enclave has a notion of a user identifiable by public key, mail can be cryptographically
authenticated as having come from that user. This avoids the need for complex user login processes: the login
process can be handled outside of the enclave, without compromising security.

**Encryption.** The encryption key used by the enclave is private to that enclave but stable across restarts and
enclave upgrades. This means messages encrypted and delivered by older clients can still be decrypted. The format
uses the respected [Noise protocol framework](https://noiseprotocol.org/) (with the one-way X handshake), which is
the same cryptography used by WhatsApp, WireGuard and I2P.

**Padding.** The body of a mail is automatically padded to reduce or eliminate size-based side channel attacks. The size of a message
won't give away information about the contents. See [side channel attacks](security.md#side-channel-attacks).

**Atomicity.** Mail will be delivered to an enclave on each startup, until the mail is acknowledged. Acknowledgement
is transactional and can be performed atomically with other acknowledgements and sending other replies. In this way
enclaves can ensure that restarting an enclave doesn't produce duplicate messages, perform the same actions twice or
cause service interruptions to users.

**Headers.** Mail has unencrypted but authenticated (tamperproof) headers that can be used to link messages together.
This allows clients to structure a conversation as if they were using a socket, but also hold multiple conversations
simultaneously. Applications can put their own data in the headers, thus enabling hosts to see part of the messages.
This can be used to e.g. implement usage tracking, prioritisation, or other tasks not directly relevant to data
processing.

**Storage.** Mail is stored by the host process. By sending mail to themselves enclaves can store chunks of data that
will be fed back to them at startup, and delete them by acknowledging the mails. This isn't a replacement for a full
database, but accessing a database from an enclave can leak a lot of data via access patterns, and of course the
database itself may need access to the unencrypted data to index or search it. The mail-to-self pattern avoids this
by storing the dataset in memory and always reading all stored data at startup.

!!! notice

    More sophisticated database solutions may be added in future releases.

## Testing and debugging

Conclave provides full unit testing support for enclaves. Enclaves themselves can be compiled for one of three modes:

* **Production/release**: fully encrypted and protected memory.
* **Debug**: the same as production, but special instructions are provided that allow enclave memory to be read and modified.
  This mode provides no protection but is otherwise a faithful recreation of the standard environment.
* **Simulation**: SGX hardware isn't actually used at all. This is helpful during development when SGX capable hardware
  may not be available.

The modes must match between how the enclave was compiled and how it's loaded. This is handled for you automatically.

Inside the enclave `System.out` and `System.err` are wired up to the host console, but logging to files doesn't work.
This is to avoid accidentally leaking private data to the host via logging.

!!! notice

    Future versions of the platform may offer encrypted logging of various forms.