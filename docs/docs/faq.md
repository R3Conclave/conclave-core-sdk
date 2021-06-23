---
hide:
    - navigation
---

# Frequently asked questions

## Design/architecture questions

### What app architecture issues should we consider?

We plan to offer more guidance on this in future. Until then consider:

* Dataset size. It must fit in memory in a single CPU machine.
* Slightly lower performance than usual. The extra security checks used by SGX reduce execution performance and the embedded 
  JVM inside the enclave isn't as sophisticated as HotSpot, so peak performance will be lower than normal for Java.
* Client-side tooling. Enclaves are useless unless the end user of the service they provide is checking a remote
  attestation. Because web browsers know nothing about remote attestation you will have to provide an independent tool
  that verifies this as part of interacting with the service (note: trying to implement this in JavaScript to run in
  a browser won't work given the enclave security model, as the JavaScript would come from the same host you're trying
  to audit). You can read more about this below.

### What cryptographic algorithm(s) does Conclave support?

The elliptic curve used is Curve25519 for encryption and Ed25519 for signing. The symmetric cipher is AES256/GCM. 
The hash function is SHA256. The [Noise protocol](https://noiseprotocol.org/) is used to perform Diffie-Hellman key agreement and set up the 
ciphering keys. As these algorithms represent the state of the art and are widely deployed on the web, there are
no plans to support other algorithms or curves at this time.

### Which communication channels exist to/from the enclave?
 
The host can exchange local messages with the enclave, and an encryption key is included in the remote attestation.
Clients can use this key transparently via Conclave Mail to communicate with the enclave in an encrypted manner. 

It's up to the host to route messages to and from the network. Conclave doesn't define any particular network protocol to 
use. Mails and serialized `EnclaveInstanceInfo` objects are just byte arrays, so you can send them using REST, gRPC, 
Corda flows, raw sockets, embed them inside some other protocol, use files etc.

### What is a confidential service?

A confidential service is a service built using the principles of enclave-oriented design. Such services can keep their 
data secure and give end users hard guarantees about how their data will be used, guarantees that do *not* require the
service provider to be trusted.

In any confidential service there must be at least three parties: the service provider, the service consumer/end user, 
and an auditor. The auditor provides the link between a natural language specification of what the service is meant to 
do in a form understandable by the users and the actually executing code.

Service providers and auditors are just roles and can be allocated amongst different people or groups in different ways.
For example, in the simplest case the service's users are their own auditors. This requires the users to be comfortable
reading source code, and to have the time and energy to audit new versions as they're released. Because that will rarely 
be the case it's more conventional to have third party auditors who perform this service for a fee. The output of the
auditor can be one of:

1. An enclave constraint (see the [tutorial](writing-hello-world.md)) that specifies which enclaves are acceptable for
   use, and a description of what they do. Those constraints are then incorporated into clients or integrations by the
   users directly.
2. A full download of a client app that has the constraint hard-coded into it.

The advantage of the latter is that the client app is also audited, providing extra guarantees of security. If the client
isn't checked or written by the service users themselves, then they have no real guarantee an enclave is even being used at all. 
It's also got better usability because users can simply download the client directly from the website of the auditors, 
thus establishing them as the root of trust.

Obviously having the service provider be their own auditor doesn't make sense, although unfortunately we have seen a
few enclave deployments in the wild that try to work this way! Such setups give the appearance of security without 
actually providing any.

### How should my confidential service UI be implemented and distributed?

The primary constraint is that the provider of the client (the UI) and the provider of the hosting should be different.
It must be so because the purpose of the enclave is protect secrets, and if the host also provides you
with the user interface or client app then you don't even know the client is talking to the enclave in the first place.
It may take the data when it's unencrypted (e.g. on your screen or disk) and simply send it elsewhere, encrypt it
with a back door or break the protection of the system in many other ways.

Fixing this depends on how you're using enclaves. If you're using it to defend against malicious datacenter 
operators/clouds then the client app must be distributed *outside* of those clouds. For example, providing the client
app for download from the same server that hosts the enclave wouldn't make sense: a malicious host could just tamper
with the client instead of the enclave.

This constraint poses special difficulties when trying to implement a classical web or mobile-app based service with
enclaves. On the web the client UI logic always comes from the same server it interacts with, and the user has no way
to control what version of the HTML and JavaScript is downloaded. As web browsers don't understand enclave
attestations you can't host a web server inside an enclave either (and it would be highly inefficient to do so).

One way to solve this is to take the approach outlined in the previous question: make the user interface and client
logic a totally separate artifact that can be downloaded directly from the auditors. The client then communicates with
the host and through it the enclave using whatever protocol you like.

Another way is to have the users download the client from the service provider but require it to be signed by the auditor. 

In practical terms this means you should be designing your client UI as a downloadable desktop or mobile app, not a
web app. You should also consider who will play the role of auditor in your design early on.

### Can I write clients in languages other than Java?

Yes, in three ways:

1. You can use JNI to load a JVM and invoke the client library that way.
1. You can run your client program on top of the JVM directly. Modern JVMs are capable of running many languages these 
   days and giving them transparent Java interop, including languages you might not think of like C++, Rust, Ruby,
   Python, JavaScript, and so on.
1. You can compile the client library to a standalone C library that exports a regular C API by using GraalVM native-image.
   This library can then be used directly (if writing in C/C++) or via your languages foreign function interface.
   
The latter approach may be provided out of the box by Conclave in future. If your project needs such support please
[email us directly](mailto:conclave@r3.com) to ask about it. The amount of work involved isn't large.

### Will using Conclave require any agreements with Intel?

Not if you use the latest generation of hardware. Modern chips support a feature called flexible launch control that
allows the owner of the hardware to decide which enclaves can be loaded. For example, if you deploy to Microsoft Azure 
no agreement or interaction with Intel is needed: Azure allows any self-signed enclave. 

On older hardware that uses the EPID attestation protocol, to go live you'll need:

1. A "service provider ID" that grants access to the [Intel attestation servers](ias.md). This is free.
2. To sign a (very simple, one page) agreement that you won't sign malware. Intel will whitelist your signing key and
   Release Mode then becomes available. This is also free.

!!! note
    Due to Conclave's design enclave clients don't need to interact with Intel at any point. The
    host does it and then publishes to clients a serialised `EnclaveInstanceInfo` object. The Conclave client libraries
    embed the necessary certificates to verify Intel's signature over this data, and the integrity of the object 
    is checked automatically when it's deserialized. The developer doesn't have to do anything: it's all fully automated. 

### How are upgrades performed when patches are released?

There are several components which might need an upgrade when a vulnerability is found. It might involve updates to 
the Intel platform services software (a daemon and some libraries on the host Linux machine), the BIOS or UEFI 
versions and configuration, Conclave, or even the application code itself.

Some of these updates might involve rebooting the system, which can cause downtime depending on the security 
requirements of each application. Conclave Mail is designed to be an asynchronous messaging system, so restarts to
upgrade to new versions would be seen by clients as a latency spike rather than downtime.

It might be possible to perform a rolling update, permitting applications to continue working on a potentially 
compromised system while the rollout is being performed. Stateless enclaves are easier to interchange, since 
no state needs to be transferred to other enclave instances. In the case of stateful enclaves, the possibility 
for doing state transfers depend on the application requirements, since data sealed on a system cannot be 
unsealed on a different system.

### Who and what will we be trusting exactly?

You must trust the CPU vendor. However, you have to trust your CPU vendor to use computers in the first place, regardless 
of whether you're using SGX or not, thus this requirement doesn't actually change anything. It's well within the 
capabilities of CPU microcode to detect code patterns and create back doors or rewrite programs on the fly. A few minutes reflection will demonstrate that [you would usually have 
no way to detect this](https://users.ece.cmu.edu/~ganger/712.fall02/papers/p761-thompson.pdf), unless you can recruit 
some other CPU that isn't back doored to assist. 

You don't need to trust the hardware manufacturers *other* than the CPU vendor. RAM, disk, firmware, the operating
system, PCI devices etc are all untrusted in the SGX threat model. Only the CPU itself needs to be correct for secrets to
be protected. 

You don't need to trust the owner of the hardware on which your enclave is running. Verifying the remote attestation
lets you check the remote system is fully up to date, and that the enclave is not running in debug mode i.e. that the 
memory really is encrypted.

You don't have to trust R3 if you have a commercial relationship with us. We will allow audits of Conclave's source code 
to paying customers, so the full code inside the enclave can be verified directly. 

You must 'trust but verify' the source code of the enclave. Users of an enclave-backed service must either audit the 
source code of the enclave themselves to ensure it’s "behaving properly" (i.e. not sending secrets to the host or other
users), or they must outsource this task to a third party they trust to accurately summarise the enclave's behaviour for 
them. See the discussion of this in the questions above. Verifying the remote attestation ensures the source code 
matches the actual code running in the enclave. 

Your enclave may need a trusted authentication system if the business logic requires a notion of identified users. 
See below for more discussion.

### How should I authenticate users of my enclave?

The service provider that runs the enclave cannot also be the account provider, as if they were they could just force
a password reset operation - which your service presumably has to support if it's used by humans - and then the host 
could impersonate any user including all users. The protections offered by enclaves are of no use if the host can simply
walk in through the front door. 

This can be fixed in several ways:

1. Identify users by long term public keys and hard code lists of them. This is very simple but may have poor usability. 
1. Use cryptographic identity tied to an independent ID provider, like a certificate authority. This works particularly 
   well if your users are institutions, as then you can use the [Corda Network identity system](https://corda.network/).
   Talk to R3 for more details if you're interested in this.
1. Use OAuth/OpenID and allow users to sign in via their Google, Office 365, LinkedIn, or corporate SAML accounts. This
   is the most sophisticated approach but also the hardest to implement. Conclave may offer pre-canned APIs for this in 
   future.

### How long should we trust an attestation verification report?

When a flaw in the system is found Intel performs a "trusted computing base recovery" operation. This involves releasing
security updates and adjusting their servers to start reporting that old versions are known to be compromised. Thus you
shouldn't accept a remote attestation for too long.
 
Intel provides [official guidance](https://software.intel.com/sites/default/files/managed/01/7b/Intel-SGX-Trusted-Computing-Base-Recovery.pdf) 
with some recommendations:

* For organizations with low risk tolerance (for example, banking), the attestation frequency policy might be once per 
  day or once per week. 
* Organizations with higher risk tolerance may define a frequency policy of once every 180 days. 
* A typical frequency policy is once every 30 days. 

At the moment the developer is responsible for writing code that restarts the enclave from time to time. Restarting
the enclave will force a re-attestation and thus make the `EnclaveInstanceInfo` fresh again. How often this happens
should be synchronised between the host and client side tool (but remember to make the host server refresh more
frequently than the client requires, to avoid synchronisation errors). 

### Isn't putting a whole JVM into an enclave a bit fat?

The so-called native images produced by Conclave don't include a traditional full JVM like HotSpot. All code is compiled
ahead of time and dynamic classloading isn't possible, which gives a model more similar to that of Rust. The runtime
aspects of the enclave are the garbage collector (which is certainly worth its weight in gold when lack of memory safety
bugs is considered), and some small pieces of code for miscellaneous runtime tasks like the generation of stack traces.

Keeping an enclave small is a good idea to reduce the chance of security critical bugs, but this logic mostly applies
when writing code in unsafe languages like C, C++ or when using unsafe blocks in Rust. The runtime in Conclave enclaves
exists mostly to prevent security bugs in the rest of the code, additionally, the runtime is itself written in Java
meaning the same bounds checking and memory safety technologies that protect your code also protect the runtime. The 
lack of dynamic code loading (when using normal JVM bytecode) means even the runtime functions aren't exposed to
attackers, so on balance we feel this is a clear security win.

### What about side-channel attacks?
 
Please see the discussion of [side channel attacks](security.md#side-channel-attacks) in the security documentation.

## Development and deployment

### What Kotlin/Java versions are usable?

Please see [System Requirements](system-requirements.md#jdks) for documentation on supported JDKs.

### At which points in time is a connection with Intel needed?

When using DCAP attestation (which requires the latest hardware), e.g. on Microsoft Azure, no direct connection to Intel is
required. The cloud provider runs caching proxies in-cloud and Conclave uses them automatically.

When using the older EPID attestation protocol a connection to Intel's servers will be made by the `aesmd` daemon and 
the host program when an enclave is loaded, as part of the startup sequence. See the information about 
[machine setup](machine-setup.md) to learn about firewall and proxy configuration.

### Can I print debug output to the console from my enclave?

Conclave enclaves built for debug and simulation support output to the console from inside the enclave 
through the use of `System.out.println()`. Release builds of enclaves do not support printing to the console.
Calling `System.out.println()` in release builds of enclaves is allowed but the output is discarded 
inside the enclave. This is to prevent accidental leakage of enclave state through the use of debug logging. 

### Can I load more than one enclave at once?

Yes, but each enclave must be a separate (Gradle) module. One module can only have one enclave entrypoint, but the same
host can load multiple enclaves.

On current hardware you may need to keep the enclaves small in order to avoid running out of fast EPC RAM, in which case the
enclave memory will be 'swapped' in and out of EPC, at a considerable performance penalty.

You can consider an alternative approach. Because of how Conclave Mail is designed, enclaves can message each other
when offline. Therefore, it's possible to load one enclave, have it send a message to another enclave, unload it,
load the second enclave and have it process the message from the first. Splitting up app logic in this way can be
useful for allowing modules to be upgraded and audited independently.

### Why does my enclave build fail?

We use the GraalVM native image technology. Builds can fail if you are trying to dynamically load bytecode (this doesn't
work yet), if you use a feature not supported by our version of Native Image, or if you're on Windows/macOS and you 
don't allocate enough RAM to Docker. The build is memory intensive, so please allocate at least 6GB of RAM to Docker
and possibly more. However you don't need to do builds all the time when working on an enclave. You always have the
option of using [mock mode](mockmode.md) to directly load the enclave code into the host JVM, which gives a regular Java development
experience.

### Can you use Java object serialisation with Conclave?

Yes, check our documentation on [serialization configuration files](enclave-configuration.md#serializationconfigurationfiles) on how to do so.

### How do I control the Native Image parameters?

You can add flags to the `native-image` command line when building to optimise your enclave binary and control the
details of how it's compiled. Create a file called `src/main/resources/META-INF/native-image/native-image.properties`
in your enclave module and set it to contain:

```
Args = -H:Whatever
```

!!! note
    This doesn't let you alter or override the flags we already specify in order to create a working enclave.

## Competing technologies

### Do you have plans to support AMD SEV?
 
Not at this time. We will re-evaluate future versions of SEV. The main problems are:
 
1. AMD SEV is very strongly oriented around the protection of VMs in the cloud. Every protected domain has an owner that
   has full access to it. This makes it useless for multi-party computations where nobody should have access to the full
   state of the calculation.
2. It has no equivalent of SGX TCB recovery, meaning flaws permanently break the system. Prior versions of SEV have been
   rendered useless by the discovery of numerous fatal bugs in AMD's firmware. Although patches were made available 
   there was no way to remotely detect if they are actually applied, which made patching meaningless.

Additionally, in SEV remote attestation is randomized which means you can’t ask a remote host "what are you running".
You are expected to know this already (because you set up the remote VM to begin with). This doesn't fit well with
most obvious enclave APIs.

AMD and Intel are in increasingly strong competition, and so we expect AMD to catch up with the SGX feature set in 
future. Intel are also working on an SEV-equivalent feature for protecting entire (Linux) virtual machines from 
the host hardware. Conclave may add support for this at some point in the future as well.

### Do you have plans to support ARM TrustZone?

Not at this time. ARM TrustZone doesn't have any form of remote attestation support. It's meant for hardening
mobile phone operating systems and supported use cases don't really go beyond that. ARM will likely enhance their
CPUs to support remote attestation in future, and we will re-evaluate when such support ships.

### Do you have plans to support AWS/Nitro?

Nitro is the name Amazon uses to refer to in-house security technology on their custom servers that restricts
access by AWS employees. It's not an enclave and Amazon must still be assumed to have access to all your data,
institutionally, because they design and implement Nitro, thus you have only their assurance that there are no
back doors or internally known weaknesses and of course you must assume sufficiently privileged administrators can
override Nitro if they need to.

As it's not an enclave we currently have no plans to support Nitro.

### Do you have plans to support cloud providers as the root of trust?

Although Nitro is not an enclave, some cloud providers do allow you to generate an attestation-like structure that
asserts what disk image was used to boot a virtual machine. For cases where you're willing to trust a cloud provider
but still want an auditable code module as part of your app, we may consider adding support for this in future.

### Why are you using Intel SGX versus mathematical techniques?
 
Mathematically based secure multi-party computation and zero knowledge proofs are closely related and have similar
tradeoffs/problems such as high complexity, low performance, difficult to use without intense dedicated training and 
not stable (i.e. the best known algorithms change frequently).

SGX is relatively simple, high performance, much easier to use, and stable over time.

However, we acknowledge the limits of relying on hardware based solutions and are interested in bringing easy to use
circuit-based cryptographic algorithms to market one day.