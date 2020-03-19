In this section we discuss some security issues inherent to the enclave model which may not be immediately apparent.

## Secure time access

The *trusted computing base* is very small in the SGX architecture. Only the CPU and some support software needs to be
trusted to work correctly. That means anything else in the computer *other* than the CPU is assumed to be broken or
malicious, and defended against using encryption and authentication.

This has implications for reading the system clock. The current time is maintained by a battery powered real-time clock chip
outside the CPU, which can be tampered with by the untrusted machine owner at any time. For this reason inside the
enclave the current time isn't available. Trying to read it will yield a default, unchanging timestamp. Additionally, 
the enclave may be paused at any moment for an arbitrary duration without the enclave code being aware of that, so
if you were to read a timestamp it might be very old at the moment you actually used it.

For these reasons timestamping events requires special support and discussion. Future versions of Conclave will be
usable together with [Corda](https://www.corda.net), a decentralised ledger technology that provides trusted 
logical and real-time clocks suitable for ordering database transactions and recording an approximate UTC timestamp
at which they occurred.

If you need a timestamp without using Corda, you can get a signed timestamp from many sources. For instance doing an
HTTP GET request to www.google.com will provide a reading from Google's clocks in the HTTP result headers.     

## Memento attacks

Your enclave has protected RAM which it uses to store normal, in-memory data. Reads are guaranteed to observe prior writes.

But an enclave is just an ordinary piece of code running in an ordinary program. That means it cannot directly access
hardware. Like any other program it has to ask the kernel to handle requests on its behalf, because the kernel is the
only program running in the CPU's supervisor mode where hardware can be controlled ("ring 0" on Intel architecture chips).
In fact, inside an enclave there is no direct access to the operating system at all. The only thing you can do is
exchange messages with the host code. The host may choose to relay your requests through the kernel to the hardware
honestly, or any of those components (host software, kernel, the hardware itself) may have been modified maliciously.
The enclave protections are a feature of the *CPU*, not the entire computer, and the CPU maintains state only until
it is reset or the power is lost.

You can think of this another way. If the enclave stores some data to disk, nothing stops the owner of the computer
stopping the enclave host process and then editing the files on disk.

Because enclaves can generate encryption keys private to themselves, encryption and authentication can be used to stop
the host editing the data. Data encrypted in this way is called **sealed data**. Sealed data can be re-requested from
the operating system and decrypted inside the enclave.


Conclave handles the sealing process for you. Unfortunately there's one class of attack encryption cannot stop. It
must instead be considered in the design of your app. That attack is when the host gives you back older data than
was requested. The system clock is controlled by the owner of the computer and so can't be relied on. Additionally
the owner can kill and restart the host process whenever they like.

Together this means an enclave's sense of time and ordering of events can be tampered with to create confusion. By
snapshotting the stored (sealed, encrypted) data an enclave has provided after each network message from a client is
delivered, the enclave can be "rewound" to any point. Then stored messages from the clients can be replayed back to
the enclave in different orders, or with some messages dropped. We call this a Memento attack, after [the film in which
the protagonist has anterograde amenesia](https://en.wikipedia.org/wiki/Memento_(film)).

!!! warning

    A full discussion of Memento attacks is beyond the scope of this document. The Conclave project strives to provide
    you with high level protocol constructs that take them into account, but when writing your own protocols you should
    consider carefully what happens when messages can be re-ordered and re-tried by the host.

## Side channel attacks

Side channel attacks are a way to break encryption without actually defeating the underlying algorithms, by making very
precise observations from the outside of a machine or program doing a secure computation. Those observations may be
of timings or power draw fluctuations.

Because enclaves run in an environment controlled by an untrusted host, we must assume the operator of the host hardware
is doing these kinds of observations in an attempt to break the security of the enclave.

Side channel attacks introduce a fundamental tradeoff between performance, scalability and security. A part of 
Conclave's intended value proposition is to surface these tradeoffs to you in a way that's easy to understand and 
control, whilst blocking as many attacks as possible automatically.

Side channel attacks on enclaves can be divided into two categories:

1. Architectural
2. Micro-architectural

These types are discussed more below.

Because side channel attacks present tradeoffs between privacy and performance, analysing them requires a different 
mindset to normal security analysis. Formally, we can say a secret has been leaked via a side channel if we can 
learn even a single binary bit of data about an encrypted message. However often the leaked data doesn't matter or 
isn't a secret worth protecting. In these cases it may be better to allow a limited amount of leakage to obtain
better performance. Future versions of this guide and the Conclave API will assist you in studying and making these
tradeoffs.

A standard example is whether a message is of type A or type B, under the assumption that most apps have at least 
two kinds of message that an enclave can process from clients. Careful requirements analysis may reveal that
the number or sequencing of message types doesn't reveal any important information and thus doesn't need to be 
protected: only the specific data within the messages.      

### Architectural attacks

Architectural side channel attacks exploit the nature, structure or specific code of your application architecture to 
reveal secrets. 
  
Here is a non-exhaustive set of examples:

* **Message sizes**. If your enclave is known to process only two kinds of message of around 1 kilobyte or 100 kilobytes
  respectively, then the size of the encrypted message by itself leaks information about what kind of message it is.
* **Message processing time**. The same as message sizes but with processing time, e.g. a message type that takes 1msec 
  to process vs 100 msec can leak what kind of message it is by simply observing how much work the enclave does when
  the host passes it the new data.
* **Storage access patterns**. If your enclave doesn't access the database when processing a message of type A but does
  when processing a message of type B, or accesses the database with a different sequence, number or type of accesses,
  the host can learn the message type by observing those accesses.  

Many of these techniques can be used to reveal fine grained information, not just message types. For instance if an
encrypted piece of data contains a number that's then used to control a loop that does data lookups, counting the
number of external data lookups reveals the number.

Some kinds of architectural side channel attacks can be mitigated by Conclave for you, for instance, messages can be
padded so all encrypted messages look the same size. This sort of approach works if message sizes don't vary too wildly
and you can afford to use the bandwidth and storage to set all messages to their maximum possible size. How valuable it
is in your situation is a topic for you to consider as you design your app.

### Micro-architectural

Enclave memory is encrypted. Micro-architectural side channel attacks exploit the inner workings of the CPU itself to 
reveal information whilst it's being processed by the CPU in its normal unencrypted state.

There are a variety of different attacks with varying details. They work by exploiting the speculative execution
capability of the processor to make an enclave compute on invalid data, which can then be used to bypass normal security
mechanisms and leak data out of the enclave - not via normal means (which the CPU doesn't allow) but by affecting the
timing of subsequent operations which can then be measured.

Micro-architectural side channel attacks can be resolved at a layer lower than the architecture of your own application.
They often require reducing the performance of either the enclave or the entire host system however, so it's worth
always planning for a large buffer of unused per-host performance.

The mitigations suggested for the latest round of micro-architectural side channel attacks (load value injection or LVI 
attacks) work by effectively disabling speculative execution when in enclave mode. Combined with the overhead of memory
encryption, execution inside an enclave can run a lot slower than normal software running outside.

### Impact of side channel attacks

Not all enclaves operate on secret data. Some types of enclave are used for their auditable execution rather than to
work with secret data unaccessible to the host. For those kinds of enclave it's sufficient to protect the signing keys
rather than all data the enclave accesses. Other types of enclave work purely with secret data, but expect that the host
isn't normally malicious: in this scenario enclaves are being used to slow down or stop attackers in the face of a 
hacked host network. It thus makes up one part of a standard suite of security measures.   

Because the performance/privacy tradeoff presented by side channel attacks can vary so widely, and this is an active area
of academic research, the expectation is that every new Conclave version will provide new tools and tunable settings 
to control their impact. This will continue for the lifespan of the product. As a developer it's your responsibility to
both upgrade to new versions as they come out, and to take side channels into account when planning the architecture
and capacity needs of your application.