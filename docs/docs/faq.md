# Frequently asked questions

## What app architecture issues should we consider?

We plan to offer more guidance on this in future. Until then consider:

* Dataset size. Until Ice Lake it must fit in memory in a single CPU machine.
* Lower performance than usual. The extra security checks used by SGX reduce execution performance and the embedded 
  JVM inside the enclave isn't as sophisticated as HotSpot, so peak performance will be lower than normal for Java.
* Client-side tooling. Enclaves are useless unless the end user of the service they provide is checking a remote
  attestation. Because web browsers know nothing about remote attestation you will have to provide an independent tool
  that verifies this as part of interacting with the service (note: trying to implement this in JavaScript to run in
  a browser won't work given the enclave security model, as the JavaScript would come from the same host you're trying
  to audit). 

## What cryptographic algorithm(s) will Conclave support?

The elliptic curve used is X25519. The symmetric cipher is AES256/GCM. The hash function is SHA256. The Noise protocol
is used to perform Diffie-Hellman key agreement and set up the ciphering keys. No other options are provided at this
time, but you can always use JCA and your own cryptographic code.

## What Kotlin/Java versions are usable?

Inside the enclave you can use Java 8 and Kotlin 1.3.61.

## Which communication channels exist to/from the enclave?
 
The host can send and receive messages with the enclave, and an X25519 key is included in the remote attestation.
Clients may choose to use this key with Conclave Mail (not yet available in beta 2) to communicate with the enclave in 
an encrypted manner. It's up to the host to route messages to and from the network. 

## Why are you using Intel SGX versus mathematical techniques?
 
Mathematically based secure multi-party computation and zero knowledge proofs are closely related and have similar
tradeoffs/problems such as high complexity, low performance, difficult to use without intense dedicated training and 
not stable (i.e. the best known algorithms change frequently).

SGX is relatively simple, high performance, much easier to use, and stable over time.

However, we acknowledge the limits of relying on hardware based solutions and are interested in bringing easy to use
circuit-based cryptographic algorithms to market one day.

## How are upgrades performed when patches are released?

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

## Who and what will we be trusting exactly?

To ensure an enclave is behaving as expected one needs to verify its source and verify that the Intel attested reported 
measurement matches the expected measurement at runtime, which requires a reproducible build system. We provide this 
in Conclave.

Regarding the who and what needs to be trusted, the above assumes trust in Intel and, when using Conclave, R3. Trust 
is needed on Intel’s software, hardware and remote attestation services and R3’s Conclave software stack implementation.
However you don't need to trust the owner of the hardware on which you run.

We plan in future to allow audits of the source code of Conclave, to allow users to establish trust in it directly.

You must verify the source code of the enclave itself to ensure it’s "behaving properly", e.g. it’s not sending 
secrets to the host.

Verifying the enclave measurement through Intel’s remote attestation service is needed to ensure the source code 
matches the actual code running in the enclave. This also lets you check the remote system is fully up to date,
and that the enclave is not running in debug mode (i.e. that the memory is really encrypted).

## How long should we trust an attestation verification report?

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

## What about side-channel attacks?
 
Please see the discussion of [side channel attacks](security.md#side-channel-attacks) in the security documentation.

## At which points in time is a connection with Intel needed?

A connection to Intel's servers will be made by the `aesmd` daemon and the host program when an enclave is loaded, as 
part of the startup sequence. See the information about [machine setup](machine-setup.md) to learn about firewall and 
proxy configuration.

## Will using Conclave require any agreements with Intel?

To develop enclaves no agreements are required. To run enclaves in production you currently need:

1. A "service provider ID" that grants access to the attestation servers. This is free.
2. To sign a (very simple, one page) agreement that you won't sign malware. Intel will whitelist your signing key and
   Release Mode then becomes available. This is also free.

So whilst enclave developers must interact with Intel to be able to use SGX it's not a difficult task, and step (2) is becoming
optional in newer versions of SGX. Technically, the whitelisting authority becomes configurable via the BIOS/UEFI so
you can set it to yourself, or for cloud/datacenter providers, enforce your own restrictions. This means that for
example if you run a datacenter you can't find yourself unexpectedly serving un-analysable malware command and control 
servers, etc. 

Note that due to how Conclave is designed, enclave clients don't need to interact with Intel at any point. Instead the
host does it for them, and then sends them a serialised `EnclaveInstanceInfo` object. Because the data in this object
is signed by Intel the clients can check it despite it being relayed through the untrusted host. 

## Do you have plans to support AMD SEV?
 
Not at this time. We will re-evaluate future versions of SEV. The main problems are:
 
1. AMD SEV is very strongly oriented around the protection of VMs in the cloud. Every protected domain has an owner that
   has full access to it. This makes it useless for multi-party computations where nobody should have access to the full
   state of the calculation.
2. It has no equivalent of SGX TCB recovery, meaning flaws permanently break the system. Prior versions of SEV have been
   rendered useless by the discovery of numerous fatal bugs in AMD's firmware. Although patches were made available 
   there's no way to remotely detect if they are actually applied.  

Additionally in SEV remote attestation is randomized, which means you can’t ask a remote host "what are you running".
You are expected to know this already (because you set up the remote VM to begin with).

## Do you have plans to support ARM TrustZone?

Not at this time. ARM TrustZone doesn't have any form of remote attestation support. It's meant for hardening
mobile phone operating systems and supported use cases don't really go beyond that.