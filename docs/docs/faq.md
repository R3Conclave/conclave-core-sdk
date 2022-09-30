---
hide:
    - navigation
---

# Frequently asked questions

## Design/architecture questions

### What is a confidential service?

A confidential service is a service built using the principles of enclave-oriented design. Such services can
securely process data and give end-users hard guarantees about how these services will use their data.
These hard guarantees do *not* require the service provider to be trusted.

In any confidential service, there must be at least three parties:

1. A service provider.
1. A service consumer or end-user. In Conclave, the service consumer is called the client.
1. An auditor.

Consider an example in which entity A uses Conclave to develop a secure application for B. Here, A is the service
provider, and B is the service consumer or end-user. To verify that the application runs as securely as promised,
B can entrust a third-party C as an independent auditor.

Auditors ensure that the confidential service works as expected. Auditors are typically third-party agencies that
perform this service for a fee.

The output of the auditor can be one of:

1. An enclave constraint (see the [tutorial](writing-hello-world.md)) that specifies which enclaves are acceptable for
   use, and a description of what they do. Application developers can incorporate these enclave constraints into the
   clients.
2. A full download of a client app with the constraint hard-coded.

The advantage of the second approach is that the client app is also audited. This provides an extra level of
security as the service users can confirm that the client uses the enclave as expected.
The second approach also has better usability as users can download the client directly from the website of the
auditors, establishing them as the root of trust.

!!! Important

    To ensure security, you should never assign the service provider as the auditor.
    See more details [below](#how-should-my-confidential-service-ui-be-implemented-and-distributed).

### Which cryptographic algorithms does Conclave use?

Conclave supports the following cryptographic algorithms and protocols:


| Algorithm/Protocol                          | Purpose/Type                                            |
|---------------------------------------------|:--------------------------------------------------------|
| Curve25519                                  | Elliptic curve for encryption                           |
| Ed25519                                     | Elliptic curve for signing                              |
| AES256/GCM                                  | Symmetric cipher                                        |
| SHA256                                      | Hash function                                           |
| [Noise protocol](https://noiseprotocol.org/)| Diffie-Hellman key agreement and setting up cipher keys |

To avoid side-channel attacks and for a robust implementation experience, Conclave supports only these algorithms.

If you want to use other algorithms, you must implement a messaging system on top of host-local calls. Alternatively,
you can use another algorithm to encrypt/decrypt a Curve25519 private key.

### Will there be any performance impact for applications made using Conclave?

Expect *slightly lower* performance. The extra security checks used by SGX reduce execution performance.

### Should the data size of the enclave be small?

The newer SGX-capable CPUs [can support large enclave sizes](https://www.intel.com/content/www/us/en/support/articles/000059614/software/intel-security-products.html).
So, this is no longer an issue.

### Which communication channels exist to/from the enclave?

The communication channels to/from the enclave are:

1. The host can exchange local messages with the enclave. These local messages also include the remote attestation.

2. The client can use Conclave Mail to communicate with the enclave using the encryption key included in the
remote attestation.

Conclave provides a [web host](conclave-web-host.md) out of the box which uses an HTTP RESTful API for communication 
between the enclave and the client.

However, Conclave doesn't define any particular network protocol for the host to route messages to and from the network.
Conclave Mail and serialized
[`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html) objects are
byte arrays. You can send these byte arrays using REST, gRPC,
[Corda](https://github.com/R3Conclave/conclave-samples/tree/master/cordapp) flows, raw sockets, or files.
You can also embed the byte arrays inside any other protocol.


### How should my confidential service UI be implemented and distributed?

Ensure that the service provider and the provider of the client (the UI) are different.

The purpose of the enclave is to protect secrets. If the host also provides the user interface or client 
app, you don't even know that the client is even talking to the enclave. It might access the data 
when it's unencrypted (e.g., on your screen or disk), send it elsewhere, encrypt it with a back door,
or break the system's protection in many other ways.

Fixing this depends on how you're using enclaves. If you're using it to defend against malicious datacenter
operators/clouds, you must deploy the client app outside those clouds. For example, you should not provide
the client app for download from the same server that hosts the enclave. This avoids a malicious host tampering
with the client instead of the enclave.


This constraint causes a unique difficulty when trying to implement a classical web or mobile-app service with
enclaves. On the web, the client UI logic always comes from the same server with which it interacts.
The user cannot control what version of the HTML and JavaScript is downloaded. As web browsers don't understand
enclave attestations, you can't host a web server inside an enclave either (and it would be highly inefficient
to do so).

One way to solve this is to make the user interface and client
logic separate artifacts that service users can download from the auditors. The client then communicates with
the host and, through it, the enclave using the protocol you decide.

This means you should design your client UI as a downloadable desktop or mobile app
[like this example](https://medium.com/@fytraki/confidential-computing-promises-that-your-information-is-kept-private-how-do-you-know-96c86b9396cf).
In your design, you must also decide who will play the role of the auditor.

### Can I write clients in languages other than Java or Kotlin?

Yes, in three ways:

1. You can use JNI to load a JVM and invoke the client library.
2. You can run your client program on top of the JVM directly. JVMs such as [GraalVM](https://www.graalvm.org/) can
run many languages and give them transparent Java interop. These include languages like C++, Rust, Ruby, Python,
and JavaScript that are widely different from Java.
3. You can compile the client library to a standalone C library that exports a standard C API by using GraalVM
native-image. This library can then be used directly (if writing in C/C++).

If you need support to write clients in languages other than Java or Kotlin,
please [talk to us on discord](https://discord.gg/zpHKkMZ8Sw).

### Will using Conclave require any agreements with Intel?

Not if you use the latest generation of hardware. Modern chips support a feature called flexible launch control that
allows the owner of the hardware to decide which enclaves can be loaded. For example, if you deploy to Microsoft
Azure, no agreement or interaction with Intel is needed, as Azure allows any self-signed enclave.

To go live on older hardware that uses the EPID attestation protocol, you'll need:

1. A "service provider ID" that grants access to the [Intel attestation servers](ias.md). This is free.
2. To sign a (very simple, one page) agreement that you won't sign malware.
Intel will whitelist your signing key and the release mode then becomes available. This is also free.

!!! Note 

    Conclave's design ensures that enclave clients don't need to interact with Intel. The host interacts with Intel and
    publishes a serialized [`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html)
    object to the clients. The Conclave client libraries embed the necessary certificates to verify Intel's signature over
    this data, and the integrity of the object is checked automatically when it's deserialized.

### How are upgrades performed when patches are released?

When a vulnerability is found, several components can need an upgrade:

* The Intel platform services software (a daemon and some libraries on the host Linux machine).
* The BIOS or UEFI version and configuration.
* Conclave source code.
* The application code.

Some of these updates can involve rebooting the system, which can cause downtime depending on the security
requirements of each application. Conclave Mail is an asynchronous messaging system. So, clients see such restarts as a
latency spike rather than downtime.

Rolling updates are possible with container orchestration tools
like [Kubernetes](https://docs.microsoft.com/en-us/azure/confidential-computing/confidential-nodes-aks-overview), which
allows applications to continue to work while it is being updated.
Stateless enclaves are easier to interchange as no state needs to be transferred to other enclave instances.
With [KDS](kds-detail.md), you can execute state transfers on stateful enclaves as well.

### Who and what will we be trusting exactly?

You must trust the CPU vendor. However, you have to trust your CPU vendor to use computers in the first place,
regardless of whether you're using SGX or not.

Your enclave may need a [trusted authentication system](#how-should-i-authenticate-users-of-my-enclave) if the business
logic requires user authentication.

You must *trust but verify* the source code of the enclave. Service users of an enclave-backed application must either
audit the source code of the enclave themselves or entrust a third-party auditor.

You *don't* need to trust:

* The hardware manufacturers *other* than the CPU vendor. RAM, disk, firmware, the operating system, PCI devices, and
other hardware components are all untrusted in the SGX threat model.
* The owner of the hardware on which your enclave is running. You can verify the remote attestation to ensure that the
remote system is up to date and that the enclave is running securely.
* R3, as Conclave is [open source](https://github.com/R3Conclave/conclave-core-sdk).

### How should I authenticate users of my enclave?

1. Identify users by long-term public keys. Use hard-coded lists of public keys. This method is easy to implement but
may have poor usability.
2. Use cryptographic identity tied to an independent ID provider, like a certificate authority.
3. Use OAuth/OpenID and allow users to sign in via their Google, Office 365, LinkedIn, or corporate SAML accounts. This
method is the most sophisticated one. However, it is also the hardest to implement. Conclave may offer pre-canned APIs
for this in the future.

The service provider that runs the enclave should never be the account provider. This is to avoid the service provider
misusing features like the password reset operation.

### How long should we trust an attestation verification report?

When a flaw in the system is found, Intel performs a *trusted computing base recovery* operation. This involves
releasing security updates and adjusting their servers to report that old versions are known to be compromised. So, you
shouldn't accept a remote attestation that is too old.

Intel provides official guidance on the
[attestation frequency policy](https://community.intel.com/legacyfs/online/drupal_files/managed/01/7b/Intel-SGX-Trusted-Computing-Base-Recovery.pdf) with some recommendations:

* A typical frequency policy is once every 30 days.
* Organizations with low risk tolerance can define a frequency policy of once a day or once a week.
* Organizations with higher risk tolerance may define a frequency policy of once every 180 days.

The developer is responsible for writing code that restarts the enclave from time to time. Restarting the enclave will
force a re-attestation which refreshes the
[`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html). Enclave restarts
should be synchronized between the host and the client-side tool. To avoid synchronization errors, remember to make the
host server refresh more frequently than the client requires.

### Isn't putting a whole JVM into an enclave a bit fat?

The native images produced by Conclave don't include a traditional full JVM like HotSpot. Conclave compiles all code 
ahead of time and dynamic class loading isn't possible (a model similar to languages like Rust and C++). The runtime
components of the enclave are the garbage collector (which is necessary to avoid memory-safety bugs), and some small
pieces of code for miscellaneous tasks like the generation of stack traces.

It's a good idea to keep the enclave small to reduce the chance of critical security bugs. But this logic mostly 
applies when writing code in unsafe languages like C, C++, or when using unsafe blocks in Rust.
The runtime in Conclave enclaves exists mostly to prevent security bugs in the rest of the code. Additionally, the
runtime is itself written in Java. So, the same components that protect your code also protect the runtime.
As there is no dynamic code loading when using normal JVM bytecode, the runtime functions aren't exposed to attackers.
On balance, this is a clear security win.

### What about side-channel attacks?

Please see [side channel attacks](security.md#side-channel-attacks).

## Development and deployment

### What Java versions are supported?

Conclave requires at least Java 8 to build and run an enclave application. Java 17 (LTS) is recommended.

### At which points in time is a connection with Intel needed?

You don't need a connection with Intel if you use the latest hardware with DCAP attestation. For example, no direct
connection to Intel is required on Microsoft Azure. The cloud provider runs caching proxies in-cloud, and Conclave 
uses them automatically.

If you use older hardware with the EPID attestation protocol, the `aesmd` daemon and the host program will connect to
Intel's servers when an enclave is loaded. This connection happens as part of the startup sequence.
See [machine setup](machine-setup.md) to learn about proxy configuration.

### Can I print debug output to the console from my enclave?

Conclave enclaves built for debug and simulation support output to the console from inside the enclave using
`System.out.println()`.

You cannot print to the console on release builds. You can call `System.out.println()` in release builds of enclaves,
but Conclave discards the output inside the enclave. This restriction prevents accidental leakage of enclave states
through debug logs.

### Can I load more than one enclave at once?

Yes, but each enclave must be a separate Gradle module. One module can only have one enclave entry point. However, the
same host can load multiple enclaves.

### Why does my enclave build fail?

Builds can fail if:

* You are trying to load bytecode dynamically. This is not possible as the GraalVM native image does not allow to link
shared libraries on the enclave.
* You use a feature not supported by the GraalVM native Image.
* You don't allocate enough RAM to Docker while working on Windows/macOS. Please allocate at least 6GB of RAM or more to
Docker to avoid this issue.

You don't need to do builds all the time when working on an enclave. Instead, you can use [mock mode](mockmode.md) to
directly load the enclave code into the host JVM, which gives a regular Java development experience.

If you need help, please [talk to us on discord](https://discord.gg/zpHKkMZ8Sw).

### Can you use Java object serialization with Conclave?

Yes, check our documentation on
[serialization configuration files](enclave-configuration.md#serializationconfigurationfiles).

### How do I control the native image parameters?

You can add flags to the `native-image` command line when building to optimize your enclave binary and control the
details of how it's compiled. Create a file called `src/main/resources/META-INF/native-image/native-image.properties`
in your enclave module and set it to contain:

```
Args = -H:Whatever
```

### How do I add resource files to native image enclaves?

Add the resources to the enclave module's resources directory (`src/main/resources`). Conclave scans this directory and
all its subdirectories automatically and passes all the files to the native image.

You can inspect the file `enclave/build/conclave/app-resources-config.json` to see which resources have been detected.

### Will my application lose data if the server hardware fails while the enclave is running?

You can use Conclave's [Key Derivation Service (KDS)](#kds-detail.md) to avoid this. You can also use KDS to ensure that
your application runs fine when a cloud service provider transfers your enclave from one physical machine to another.

## Alternative technologies

### Do you support AMD SEV?

We don't support AMD SEV now. We may consider it for a future release. If you would like Conclave to support AMD SEV,
please [let us know on discord](https://discord.gg/zpHKkMZ8Sw). 

Intel is also working on an SEV-equivalent feature for protecting entire (Linux) virtual machines from
the host hardware. We will consider to add support for this also in the future.

### Do you plan to support ARM TrustZone?

Not at this time. ARM TrustZone doesn't support remote attestation. We will re-evaluate when TrustZone supports remote 
attestation.

### Do you plan to support AWS/Nitro?

Currently, we don't have plans to support Nitro as it's not an enclave. Nitro is Amazon's in-house security technology 
that restricts AWS employees from accessing custom servers.

### Do you have plans to support cloud providers as the root of trust?

Some cloud providers allow you to generate an attestation-like structure that asserts what disk image was used to boot a
virtual machine. For cases where you're willing to trust a cloud provider but still want an auditable code module as
part of your app, we may consider adding support for this in the future.

### Why are you using Intel SGX versus mathematical techniques?

Mathematically-based secure multi-party computation and zero-knowledge proof are closely related and have similar
problems. These problems include high complexity, low performance, lack of stability, and difficulty to use without
intensive training.

SGX is relatively simple, high performance, easy-to-use, and stable over time.

However, we acknowledge the limits of relying on hardware-based solutions and are interested in bringing circuit-based
cryptographic algorithms to market one day.