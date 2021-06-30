# Confidential Computing

When you send data to somebody else's computer they can ordinarily do whatever they like with it. Confidential
Computing describes a set of hardware techniques that fix this problem. Confidential Computing makes it possible 
to know what algorithm will process your information before you send it to a third party, and to be assured that
the third party cannot subvert the integrity of the algorithm or observe it while it works.

Conclave makes it easy to write applications that utilise these capabilities. Conclave makes it possible to 
isolate a small piece of code from the rest of the computer on which it runs (an _enclave_). Remote users can
be shown what code is running in the isolated world and then upload their secret data to it, where it can be processed
without the owner of the computer in question getting access. Enclaves can be used to protect private data
from the cloud, do multi-party computations on shared datasets and make networks more secure.

Intel SGX is an implementation of enclave-oriented computing. Conclave builds on SGX by making it easier to develop
enclaves in high level languages like Java, Kotlin or JavaScript.

In this document we'll introduce the key concepts of Confidential Computing and map them to the equivalent
concepts in Conclave. The [architecture](architecture.md) document then pulls these concepts together to describe
the end-to-end architecture of Conclave itself.

## What is an enclave?

An **enclave** is a region of memory which the CPU blocks access to, running inside an ordinary process which we call
the **host**. The region contains both code and data, neither of which can be read or tampered with by anything else.
Code executing inside this region has access to special CPU instructions that give it a variety of useful
abilities. Enclave memory is protected both from the rest of the code in that process, privileged software like the
kernel or BIOS, and because the RAM itself is encrypted even physical attackers. This means a remote computer can
trust that an enclave will operate correctly even if the owner of the computer is malicious.

In Conclave, an enclave is a subclass of the [`Enclave`](api/com/r3/conclave/enclave/Enclave.html) class, which is 
automatically combined by Conclave with an embedded JVM and compiled into a native shared library (ending in .so), and
bundled into a JAR. The host program then uses the [`EnclaveHost`](api/com/r3/conclave/host/EnclaveHost.html) class to
load the enclave class from this JAR and automatically instantiate the enclave.

!!! note
    Conclave compiles the enclave into a .so file because that's how the Intel SGX SDK expects the enclave to be built.
    Conclave hides this detail during the build phase by wrapping this file inside a Jar artifact, and during execution phase
    automatically loads the .so file from the classpath.

### Enclaves and Hosts

As described above, an enclave is loaded into an ordinary operating system process, which is known as the 'host'. All of 
the enclave's communicates with the outside world is via the host. To facilitate this, once an enclave is loaded, the host and 
enclave can exchange byte buffers back and forth. This is an efficient operation: the buffers are copied from the host 
into the enclave using a direct memory copy. Conclave provides convenient APIs to facilitate this data passing between 
enclave and host. 

However, it is important to note that, unlike the enclave, neither this 'host' process nor the underlying
operating system are trusted by the users of the enclave. Yet the enclave depends on them. The following sections 
explain how message encryption, remote attestation, and the Conclave-specific 'Mail' API combine to solve this problem.

!!! note
    In many ways, enclaves turn pre-existing security assumptions on their heads. In traditional programming,
    it is generally assumed that the operating system is trusted and that the primary threat is applications
    attacking the operating system, or each other. However, when using enclaves, this presumption is reversed: 
    the user of the application, who is remote, does _not_ trust the operator or operating system of the computer. 
    Instead, they only trust the code running inside the enclave. Therefore, it is useful to imagine the operating system
    and the process that hosts the enclave as potential adversaries. Unfortunately, [most existing software is 
    built on the assumption that the operating system is not a threat](https://hovav.net/ucsd/dist/iago.pdf). For
    this reason, it is usually best to avoid relying inside the enclave on code that was not explicitly written with 
    this deployment mode in mind.

### Encrypted messages

Enclaves are only useful if some other computer is interacting with them over the network. An enclave _by itself_
doesn't gain you anything, because enclaves are defending against the owner of a computer. If you just run an enclave
locally then the owner of the computer is you, and it doesn't make sense for software to try and protect your
own data from yourself. 

So these remote users need to be able to encrypt messages with a key that is known only to the enclave.

Enclaves can generate random numbers and thus encryption keys available only to themselves, which nothing else on
the host machine can access. Using these keys remote computers can encrypt messages to the enclave. These messages can
be passed into the enclave via the host where they are decrypted and worked on. The owner of the host computer can't 
read them, despite that the data is being processed on their hardware.

Conclave calls these encrypted messages [**mails**](architecture.md#mail), by way of analogy to encrypted e-mail, but 
enclaves can work with encryption in whatever way they want. The Mail API is just a utility and you don't have to use it.

### Remote attestation

Encrypting a message requires a public key, which raises the question of where that key comes from. The clients of
the enclave have to be convinced that the key really belongs to an enclave they want to work with and not, say, an
unencrypted non-enclave program that's impersonating the intended destination.

Enclave host programs can generate a small data structure called a **remote attestation** (RA). This structure,
which is signed by a key controlled by the underlying hardware, states the following facts:

1. A genuine, un-revoked Intel CPU is running ...
2. ... an enclave into which a code module with a specific hash (measurement) was loaded ...
3. ... and which has generated public key P ...
4. ... and the computer is up to date with security patches, and configured in the recommended way.

The host generates a remote attestation and sends it to clients in some way. Those clients can then send encrypted
messages to the enclave using the public key P after checking what kind of enclave it really is. By recognising a
list of known code hashes, or verifying that the enclave was signed by a party whom they trust, 
clients can effectively whitelist particular enclaves and treat them as trustworthy servers.

In Conclave, a remote attestation is an instance of the [`EnclaveInstanceInfo`](api/com/r3/conclave/common/EnclaveInstanceInfo.html) class.

## Applications of Enclaves

Enclaves are a very general capability and can be used in a variety of ways. You can:

* Process private data without being able to see it yourself, to enhance your user's privacy.
* Create a hardware-backed form of zero knowledge proofs: make trustworthy 'statements' that a particular
  computation was done on some data, without the data itself needing to be revealed.
* Outsource some kinds of computations to an untrusted cloud.
* Improve the security of a server by restricting access to a whitelist of client enclaves, blocking attempts to send
  malformed packets and messages that might come from hackers.
* Make your service auditable only by users who want high assurance - those who don't care can simply ignore the
  infrastructure entirely.

## Limitations of Enclaves

It's important to understand the limitations of enclaves. They aren't meant to be a general protection for
arbitrary programs. Although technically possible to just relay operating system calls in and out of an enclave, this
approach [suffers from various security pitfalls](https://hovav.net/ucsd/dist/iago.pdf) and more importantly is a
mis-understanding of the benefits enclaves give you.

Confidential computing is based on two key insights:

1. The more code that processes attacker-supplied data the more likely the program is to be hackable.
2. A large chunk of most programs is actually just moving data around and managing it in various ways, not processing it.

The software and hardware that must be uncompromised for a system to work correctly is called the
*trusted computing base*, or TCB. In Conclave the TCB includes the CPU itself, the patchable microcode of the CPU,
and all software running inside the enclave - both your code and the Conclave runtime.

A major goal in secure systems design is to minimise the size of the TCB. This is for two reasons.

### Security

The software industry has decades of experience with building secure and tamperproof systems. A
simple heuristic is that the more code there is inside the TCB that handles attacker-controlled data, the easier 
it will be for an adversary to find a mistake and break in.

It's always been good design to minimise the amount of code that handles potentially malicious data and enclaves give
you an even greater incentive to do so. That's because enclaves don't magically make the software inside them
un-hackable. They protect the code and its memory from outside interference by the owner of the computer (and any
hackers that gained access to the operating system) but if the code running inside the enclave has a bug that can
be used to get in, those protections are of no use. Thus it's important for an enclave to be written securely.

Conclave helps with this dramatically because it lets you write enclave logic in memory-safe, type-safe languages like
Java or Kotlin. These languages eliminate by construction huge swathes of bugs. All buffer bounds are checked,
no memory is freed before use and all casts are valid. This makes it much easier to process data
from outside the enclave because you don't have to worry that you might accidentally let an attacker in.

Sometimes people find this confusing: a JVM is quite large, so if the TCB needs to be small isn't that a problem? 
It's not for two reasons. One reason is because this heuristic only applies for code that an attacker can actually 
reach and influence via external input, but the input data to the JVM (bytecode) is itself a fixed part of the enclave.
Attackers can't modify it without changing the measurement reported in the remote attestation and thus being detected by
the client - the client's enclave constraint will be violated and an exception  will be thrown. The second reason is
that Conclave uses the GraalVM Native Image JVM, in which all bytecode is compiled to native code ahead of time. The
actual JVM in use is therefore quite tiny; it consists mostly of the garbage collector.

!!! warning

    Whilst garbage collected/type safe programs are much safer than programs written in C, they aren't immune to
    vulnerabilities. Watch out for very generic reflective code like object serialization frameworks, that could be
    tricked into deserializing malicious streams.

### Auditability 

Enclaves are meaningless unless the user verifies the enclave does what they think it does before sending it private
data. As the enclave size goes up it gets harder to read and understand its code.

In practice audit work will often be outsourced: we don't expect that every end user of an enclave reads all the code
themselves. Ultimately though, someone the user trusts must read all the code inside the enclave. The less there is to
read the better.

A lot of code works with data without parsing or understanding it. For instance, network frameworks read packets from
the wire and databases store data to disk in generic ways. Other parts of a program truly understand the data and work
with it in app-specific ways; usually this is called business logic.

In enclave-oriented design you draw a bright line between host code and business logic. Business logic runs inside
an enclave on normal Java objects. Host code does everything else:

* Network handling
* Storage
* Routing
* Monitoring
* Providing administration interfaces

and so on. Keeping as much code in the host as possible has two key advantages:

1. The host code is untrusted and assumed to be compromised. Thus any bugs in it are much less serious. You can worry
   less about security vulnerabilities in the host code because it can't access your user's private data.
2. Because the enclave is small it will change less frequently, meaning clients don't need to re-audit or whitelist
   new versions.

The second point is intuitive to understand. Think of your enclave as a concrete version of your privacy policy. If you
change your web server, core database engine or even entire operating system then this is of no concern to your users.
You've only changed *how* data is processed. But if you change *what* you do with their data, they may have an opinion
on this and want to know about it. Remote attestation lets them see that the core business logic has changed, and in
what way.

## Summary

In summary, an enclave is a small program which can run on an untrusted computer, where the operator of that computer
cannot affect the integrity of the code nor observe the data that passes in or out. Through a process of 'remote attestation',
remote clients can gain confidence that a specific program - or a program signed by a particular entity - is running, and that
it is running in this secure mode on a fully patched machine. This makes it possible to deliver services that operate on third
parties' data, where those third parties can be assured that their data cannot be used for any other purpose.

Enclaves run inside untrusted host processes, and the combination of encryption, remote attestation and Conclave's
purpose-designed APIs work together to make it as simple as possible for developers to write applications that work in this way.

## Additional Comments

### A note on measurements vs signers

The code hash included in a remote attestation is called a **measurement**. It's not the hash of any particular file
but rather a more complex hash that must be calculated with special tools. It covers the entire module and all of its
dependencies loaded into an enclave (a fat JAR).

A measurement hash is pretty unhelpful by itself. It's just a large number. To be meaningful you must _reproduce_ the
measurement from the source code of the enclave. When you compile your enclave the measurement
hash is calculated and printed. By comparing it to what you find inside a remote attestation, you can know the source
code of the remote enclave matches what you have locally.

Whitelisting measurements is strong but brittle. Any upgrade to the enclave will cause your app to reject the new
remote attestation and stop working until you re-read the enclave's source code, reproduce the build again and whitelist
the new measurement. An alternative is to whitelist a _signing key_ instead.

Enclave files must be signed, and the SGX infrastructure understands and verifies these signatures. This is useful
for two reasons:

1. You can choose to accept any enclave signed by a particular organisation, rather than reviewing the source code
   and reproducing the enclave yourself.
2. Some SGX capable computers have a root key that must whitelist enclaves to be executed. This can be used by the
   owners of SGX-capable machines to retain visibility and control into what programs are actually running on their 
   hardware. Whilst this capability could therefore be leveraged by cloud vendors to restrict which SGX workloads
   their servers will allow to run, we are not aware of any that operate in this way in practice.

The hash of the public part of the key that signed an enclave is included in remote attestations, so you can choose to
communicate with any enclave signed by a given key.

### Enclaves vs alternative approaches

The enclave architecture is the result of many years of evolution. Enclaves are good at minimising TCB size because that
was their entire design goal: an enclave is intended to be the smallest piece of application logic that needs to be 
protected.  

Some other systems work by attempting to protect and attest an entire operating system stack, but [this was tried in the 
past](http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.570.7943&rep=rep1&type=pdf) and found to work poorly. Let
us now briefly trace the historical evolution that led to the SGX enclave design today.

The first attempts at implementing trusted computing relied on a so-called *trusted platform module* chip (TPM). The
TPM contained special registers called *platform configuration registers* (PCR). A PCR was sized to contain a hash, but
could not be written directly. Instead writing to a PCR concatenated the new hash value with the prior, and stored the
result of hashing that concatenation. PCRs therefore contained the final hash in a chain of hashes. The only way to get
a PCR to a particular value is to feed it the right sequence of hashes, called the *chain of trust*.
 
The firmware and electronics on the motherboard implemented a *static root of trust*. In this design, the initial boot 
ROM would load the BIOS and other firmware, hash it into a PCR and then pass control to it. The firmware would then in 
turn hash and load the next stage into the same PCR and so on. Thus by the time the system booted, all the software 
and firmware on the system had been hashed together. The contents of the PCR could then be remotely attested and the 
user could verify what software booted. This then let them reason about how the remote computer would behave.

Given the explanations above the problem with this approach is hopefully now obvious - this approach defined the TCB
as everything in the entire boot sequence, including the whole operating system kernel. Any bug at any point in this
code would allow someone to create a forged chain of trust and undermine the system. But system firmware is already
very large and likely to contain bugs, let alone a kernel. And even if the entire boot was 
secure the operating system or application server would likely contain bugs allowing in remote attackers anyway.

On the open PC platform this system turned out to be too easy to defeat, and became abandoned outside of a few use
cases involving disk encryption (with no remote attestation). Despite its failure in PCs, static roots of trust 
worked well enough for games consoles where the entire boot sequence could be encrypted, locked down, attested to
multi-player game networks and very carefully audited by a vertically integrated systems manufacturer. 

The next attempt introduced a *dynamic chain of trust* via [Intel TXT](https://en.wikipedia.org/wiki/Trusted_Execution_Technology) and AMD SVM.
TXT/SVM provided so-called "late launch" in which a hypervisor can be started up and measured whilst the system is
running. The hypervisor can then measure and boot an operating system, which may be a small and special purpose
operating system. This approach also failed to gain much traction, as at the time building a small special purpose
OS was too difficult (whereas nowadays open source unikernel operating systems are more easily available), and booting
a regular Linux faced many of the same problems as with the static root of trust: too many places to hide back doors,
too many bugs for the system to be truly trustworthy.

This is how we arrived at SGX. An enclave runs in user-space and does not require a large piece of code like a 
hypervisor, kernel and system firmware to become a part of the remote attestation. Instead, only a small statically
linked binary is loaded and protected. The code in the enclave can communicate with the host process by reading and
writing from host memory. The TCB size is finally minimised to only what is strictly necessary - as long as your app
is designed correctly.
