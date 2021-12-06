---
hide:
- navigation
---

# Conclave

Conclave is a toolkit for building _enclaves_, small pieces of software that are protected from attack by the owner
of the computer on which they run. It is ideally suited to solving multi-party collaboration and privacy problems
but can also be used to secure your infrastructure against attack.

* **[Visit the Conclave website](https://www.conclave.net)**

* **[Get Conclave](https://conclave.net/get-conclave/)**
    * Conclave 1.0 SHA2: `c3430d7172b2b0ab15a19930558f8c18c64974bb113dfd2c0722d067cdf3fee5`
    * Conclave 1.1 SHA2: `3d47ae8a9fb944d75fb4ee127cd9874c04343643c830e1fe68898c3c93891ca2`

## Why Conclave?

- High level, simple API that is much easier to use than other enclave APIs.
- Write your host app in any language that can run on a Java Virtual Machine like Java, Kotlin or even 
  [JavaScript](javascript-python.md) or [Python](javascript-python.md). 
- Write your enclave using the [GraalVM](https://www.graalvm.org/) native image technology for incredibly tight memory 
  usage, support for any GraalVM language and instant startup time. Eliminate all memory management errors that would 
  undermine the security of your enclave, thanks to the built-in compacting generational garbage collector.
- Develop gracefully on all operating systems, not just Linux: Windows and macOS are fully supported as well.
- Full support for auditing enclaves over the internet, including remote attestation. A user can verify what the 
  source code of the remotely running enclave is, to ensure it will behave as they expect.
- An [end-to-end encrypted communication](mail.md) system that eliminates size-based side channel attacks and makes 
  communicating with an enclave super easy.
- Simple to use persistence API for securely storing data inside an enclave which is resistant to rollback attacks.
- A cloud based [Key Derivation Service](kds-detail.md), which enables applications not be tied down to a single 
  machine. This enables seemless migration of enclave data from one VM to another, high-availability architectures, 
  and seamless redeployment of VMs by cloud service providers.
- A Gradle plugin to automate compiling, signing and calculating the code hash of your enclave. No need to use the Intel
  SDK - everything needed is included.
- API designs that guide you towards SGX best practices and avoidance of security pitfalls.
- Easily deploy to [Microsoft Azure](azure.md) by just uploading your Java host app and running it as normal. 
  There is no setup!
- A powerful unit testing framework to verify the operation of your enclave and remote attestation functionality, 
  using just JUnit.
- Integrate and benefit from [Corda](https://www.corda.net), an open source peer-to-peer network for business uses with
  enterprise support.
- [Tutorials](running-hello-world.md), guides, design assistance and commercial support from the SGX experts at R3. Friendly devs on our
  [discord server](https://discord.com/invite/dDaBGqGPre) and mailing list, even if you don't have a proper support contract!
  
**Get started for free today!**

## Documentation

Click through the tabs above to see all our documentation. If you're not sure where to start, these pages are good:

* **Concepts**

    * [**Enclaves.**](enclaves.md) If you're totally new to enclave development start with our introduction to enclave-oriented
design. This will explain the concepts referred to in the rest of the documentation.

    * [**Architectural overview.**](architecture.md) This explains the core Conclave APIs and how it's structured.

* **Writing and Running your First Conclave Application**
  
    * [**Tutorial.**](running-hello-world.md) Once you understand the concepts go straight to writing your first enclave.

    * [**Enclave Configuration.**](enclave-configuration.md) Now you've created your first enclave, take a deeper look at the configuration options
available for creating enclaves.

* **Deploying and Operating Conclave Applications**

    * [**Machine setup.**](machine-setup.md) Learn how to obtain SGX capable hardware, set it up, deploy to production
and then keep your machine trusted by applying updates.

* **Exploring more of Conclave's capabilities** 

    * [**Using JavaScript or Python.**](javascript-python.md) How to use JIT compiled JavaScript or Python inside the enclave.

    * [**Integrating Conclave with Blockchain Applications**](writing-cordapps.md) You'll need a way for your users to get data to and from your
service that has integrated identity, workflow, firewall handling, database integration and more. Corda is an enterprise
blockchain platform that offers many useful features when you progress beyond encrypting your business logic.

    * [**Reference guide.**](api/index.html) We provide detailed JavaDocs for the API.
   
    * [**Samples.**](https://github.com/R3Conclave/conclave-samples) The Conclave Developer Relations team maintains a library 
   of samples here. 
      Samples include machine learning within a Conclave enclave and an example of a lightweight host application.

## Get in touch

R3 offers [full ticket based commercial support](https://conclave.net/get-conclave/).

There's a public mailing list for discussion of using Conclave and we also welcome general SGX talk. You can also 
find the development team during UK office hours (GMT 0900-1700) on Discord.

[:fontawesome-solid-paper-plane: Join conclave-discuss@groups.io](https://groups.io/g/conclave-discuss){: .md-button }
[:fontawesome-solid-paper-plane: Email us directly](mailto:conclave@r3.com){: .md-button }
[:fontawesome-brands-discord: Join us on Discord](https://discord.com/invite/dDaBGqGPre){: .md-button }


## Release notes

### 1.2

!!! important
    There have been some breaking changes in 1.2. Be sure to read the [API changes page](api-changes.md) on how to 
    migrate your existing project.

!!! important
    In our previous release we had deprecated Avian support. This has now been removed completely in 1.2. Enclaves built
    with GraalVM native image had many benefits over Avian enclaves, including enhanced security, performance and 
    capabilities.

1. :jigsaw: **New feature!** The Conclave Key Derivation Service (KDS) elimates the restriction of the enclave 
   sealing key being tied to a single physical CPU and thus unlocking cloud deployments. You can now easily migrate 
   data from one VM to another, unlock clusters and high-availability architectures, and enable seamless 
   redeployment of VMs by cloud service providers. [Learn more about the KDS and how to start using the 
   public preview](kds-configuration.md).

1. :jigsaw: **New feature!** We've vastly improved how data is persisted inside the enclave. Previously we 
   recommended the "mail-to-self" pattern for storing data across enclave restarts. This is cumbersome to write, not 
   easy to understand and does not provide rollback protection against the host. To address all these issues the 
   the enclave has a simple [key-value store](api/-conclave/com.r3.conclave.enclave/-enclave/get-persistent-map.html)
   represented as a `java.util.Map` object. Conclave will securely persist this map such that it survives 
   restarts and is resilient to attempts by the host to roll it back to previous states. Find out more 
   [here](persistence.md#persistent-map).

1. :jigsaw: **New feature!** We've actually introduced two forms of enclave persistence in 1.2! The rollback protection 
   provided by the persistent map above may not be needed and comes at a cost of increased overheads. As an alternative 
   the in-memory file system inside the enclave can be persisted directly to disk as an encrypted file on the host 
   for faster performance. [Find out more here](persistence.md#conclave-filesystems).

1. :jigsaw: **New feature!** To elimate the need to write the same boilerplate code for the host we've introduced a 
   simple new host web server which exposes a REST API for sending and receiving mail and which implements the 
   necessary behaviour of an enclave host. Your host module only needs to reference `conclave-web-host` as a 
   runtime dependency and then all of the boilerplate host code can be done away with! Have a look at the updated hello 
   world sample to see how it's used.

1. :jigsaw: **New feature!** To complement the host web server, we've also introduced a client library to make it
   super easy to write a web-based enclave client. Add `conclave-web-client` as a dependency to your client module 
   and make use of the new [`WebEnclaveTransport`](api/-conclave/com.r3.conclave.client.web/-web-enclave-transport/index.html) class in 
   conjunction with the new [`EnclaveClient`](api/-conclave/com.r3.conclave.client/-enclave-client/index.html).

1. :jigsaw: **New feature!** [`EnclaveClient`](api/-conclave/com.r3.conclave.client/-enclave-client/index.html) is a 
   new API in `conclave-client` which greatly simplies your client code and handles all of the complexities when 
   communicating with an enclave. It is agnositic to the transport layer between it and the host and support for 
   other network protocols beside HTTP can be added. 

1. :jigsaw: **Java 11** is now the default JDK version inside the enclave. You can make use of the new APIs and 
   features introduced since Java 8 when writing your enclave code! For compatibility the Conclave libraries are still 
   compiled using Java 8 so you can continue to use Java 8 (or above) outside the enclave.

1. :jigsaw: **New feature!** We have made it easier than ever to start a Conclave project using our new tool, 
   [Conclave Init](conclave-init.md).

1. :jigsaw: **New feature!** We've added enclave lifecycle methods so that you can do any necessary enclave startup 
   initialisation and shutdown cleanup. Override [`onStartup`](api/-conclave/com.r3.conclave.enclave/-enclave/on-startup.html) and 
   [`onShutdown`](api/-conclave/com.r3.conclave.enclave/-enclave/on-shutdown.html) respectively.

1. :jigsaw: **New feature!** The host can now update the enclave's attestation without having to restart it. 
   Previously restarting was the only way to force an update on the [`EnclaveInstanceInfo`](api/-conclave/com.r3.conclave.common/-enclave-instance-info/index.html)
   object. Now you can call [`EnclaveHost.updateAttestation`](api/-conclave/com.r3.conclave.host/-enclave-host/update-attestation.html) 
   whilst the enclave is still running and the [`enclaveInstanceInfo`](api/-conclave/com.r3.conclave.host/-enclave-host/get-enclave-instance-info.html)
   property will be updated.
   
1. :jigsaw: **New feature!** We've further improved the Conclave plugin and added more automation so that 
   you have to write less boilerplate. It's no longer necessary to add the `conclave-enclave` library as a 
   dependency to your enclave module. Also, the plugin will automatically add `conclave-host` as a 
   `testImplementation` dependency to enable mock testing. And finally the plugin will make sure any enclave
   resource files in `src/main/resources` are automatically added. Previously resource files had to be specified 
   manually.

1. :jigsaw: **New feature!** We've added a new overload of [`EnclaveHost.load`](api/-conclave/com.r3.conclave.host/-enclave-host/load.html)
   which no longer requires having to specify the enclave class name as a parameter. Instead,
   [`EnclaveHost`](api/-conclave/com.r3.conclave.host/-enclave-host/index.html) will scan for the single matching
   enclave on the classpath.

1. :jigsaw: New experimental feature! Easily enable and use Python. It is JIT compiled inside the enclave and can 
   interop with JVM bytecode. Use this feature with care. Python support is still in an experimental state. While it 
   is possible to run simple Python function, importing modules will likely lead to build issues.

1. The gradle plugin will now check your build configuration for 
   [productID](enclave-configuration.md#conclave-configuration-options) and
   [revocationLevel](enclave-configuration.md#conclave-configuration-options) properties, and print a helpful error
   message if they are missing.

1. The API for checking platform support on the host been improved. `EnclaveHost.checkPlatformSupportsEnclaves` was 
   found to be too complex and did too many things. It's been replaced by easier to understand methods. See the  
   [API changes page](api-changes.md) for more information.

1. Conclave now uses version 2.14 of the Intel SGX SDK. This provides bug fixes and other improvements. See the
   [SGX SDK release notes](https://01.org/intel-softwareguard-extensions/downloads/intel-sgx-linux-2.14-release)
   for more details.

1. The container gradle script has been removed due to stability issues and will no longer be supported. If you are 
   using container-gradle to develop on Mac, we strongly suggest you stop doing so and follow
   [these instructions](running-hello-world.md) for running your conclave projects instead.

### 1.1

!!! important
    There have been some breaking changes in this version of Conclave. Be sure to check out the [API changes](api-changes.md)
    you might need to make to get your current project building with Conclave 1.1.

!!! Deprecation
    The Avian runtime is deprecated as of Conclave 1.1. Previously Conclave gave you the choice of whether to use Avian 
    or GraalVM native image as the runtime environment inside your enclave. Enclaves built with GraalVM native image
    have many benefits over Avian enclaves, including enhanced security, performance and capabilities. Therefore 
    new projects should not use the Avian runtime. References to using Avian have been removed from the documentation
    for Conclave 1.1, and the next release of SDK will not include the capability to build enclaves that use the Avian runtime.
    Conclave 1.1 does still allow you to build Avian enclaves on Linux and macOS but you cannot build Avian enclaves
    on Windows systems.

1. **Conclave 1.1 has been tested on the latest 3rd Gen Intel Xeon Scalable processors, also known as Ice Lake Xeon CPUs.**
   These CPUs bring a number of enhancements for Conclave applications, especially in the amount of memory available
   for use inside enclaves where the limit has been increased from typically around 95MB up to 512GB per CPU depending
   on the platform. You do not need to make any changes to your application to support these new CPUs except
   to ensure you are using DCAP attestation as Xeon Scalable processors do not support EPID.
1. :jigsaw: **New feature!** Mock mode has been extended so you can now specify 'mock' as an enclave mode and use 
   your regular host rather than having to modify your code to use a special build of your host. A new `mockEnclave` 
   property has been added to `EnclaveHost` that can be used in mock mode to allow access to the enclave instance
   for probing internal state during development and testing.
   [Learn more about enclave configurations](architecture.md#testing-and-debugging).
   [See more information about how the API has changed](api-changes.md#1.0-to-1.1)
1. :jigsaw: **New feature!** When using mock mode you can now specify the configuration of the mock environment,
   allowing parameters such as the `codeHash`, `codeSigningKeyHash` and `tcbLevel` to be modified programatically
   in your unit tests. See [Mock mode configuration](mockmode.md#mock-mode-configuration) for more details.
1. :jigsaw: **New feature!** We've updated the bundled [CorDapp sample](writing-cordapps.md) to show how to integrate
   Corda network identities with Conclave. The node can now log in to the enclave and identify itself by presenting its
   verified X.509 certificate. The enclave can use this to map the mail sender key to a meaningful X.500 name.
1. :jigsaw: **New feature!** To better showcase Conclave we've created a [separate repository](https://github.com/R3Conclave/conclave-samples)
   of enclave samples for you to look and try out. We plan to update this on a more regular basis. In particular we have
   a [sample](https://github.com/R3Conclave/conclave-samples/tree/master/tribuo-tutorials) running the [Tribuo](https://tribuo.org/)
   machine learning library inside an enclave.
1. The Conclave documentation has been improved, fixing a number of errors and updating the format of the Javadocs
   section of the documentation site. The Conclave SDK documentation is packaged along with the SDK so it is automatically 
   displayed in IDEs that support this, including Eclipse and Visual Studio Code. See 
   [Writing hello world](writing-hello-world.md#ide-documentation-in-the-root-buildgradle-file) for details of
   how to configure your Gradle project to display documentation in the IDE.
1. We've updated to version 21.0.0 of GraalVM which along with some performance improvements to the garbage collector,
   also adds Java serialisation support. We've updated Conclave to take advantage of this. Find out more about how
   to configure [serialization within the enclave](enclave-configuration.md#serializationconfigurationfiles).
1. The SGX SDK that Conclave is built upon has been updated to version 2.13.3. This provides bug fixes and an update
   to the Intel IPP cryptographic library. See the [SGX SDK release notes](https://01.org/intel-softwareguard-extensions/downloads/intel-sgx-linux-2.13.3-release)
   for more details.
1. We've improved the error messages in a number of places, including when there are problems signing the enclave
   and when there are issues in sending and receiving Mail messages.
1. The container-gradle script has been updated to correctly handle configuration files that live outside the source tree.
1. The output of the enclave gradle build has been tidied up, hiding the information that would only normally be
   present on verbose builds. If you want to see the verbose output in your build then just add `--info` to your
   gradle build command line.
1. Security improvements and bug fixes: improved DCAP certificate validation, added additional bounds checks on some
   internal methods, fixes to allow validation of enclave-to-enclave attestations inside an enclave.

### 1.0

1. :jigsaw: **New feature!** A new `PostOffice` API makes using mail easier and also automatically applies a reasonable 
   minimum size to each mail to help defend against the host guessing message contents by looking at how big it is (a size
   side channel attack). The default size policy is a moving average. See `MinSizePolicy` for more information. Mail 
   topic semantics have been improved by making them scoped to the sender public key rather than being global. This 
   allows the enclave to enforce correct mail ordering with respect to the sequence numbers on a per-sender basis. This means 
   `EnclaveMail.authenticatedSender` is no longer nullable and will always return an authenticated sender, i.e. if
   a sender private key is not specified then one is automatically created.
1. :jigsaw: **New feature!** An embedded, in-memory file system is provided that emulates POSIX semantics. This is 
   intended to provide compatibility with libraries and programs that expect to load data or config files from disk.
   [Learn more about the in-memory filesystem](filesystem.md).
1. :jigsaw: **New feature!** A new script is provided to make it easier to run your application inside a Docker container
   on macOS. This helps you execute a simulation mode enclave without direct access to a Linux machine.
1. :jigsaw: **New feature!** The enclave signing key hash is now printed during the build, ready for you to copy into a constraint.
1. :jigsaw: **New feature!** A tutorial for how to write [CorDapps](https://www.corda.net) has been added. Corda can
   provide your enclave with a business oriented peer-to-peer network that has integrated identity. [Learn more about
   writing CorDapps with Conclave](writing-cordapps.md).
1. Multi-threaded enclaves are now opt-in. By default, the enclave object will be locked before data from the host is
   delivered. This ensures that a malicious host cannot multi-thread an enclave that's not expecting it.
1. The Gradle tasks list has been cleaned up to hide internal tasks that aren't useful to invoke from the command line.
1. GraalVM has been updated to version 20.3. An upgrade to 21.0 will come soon. 
1. Usability improvements: better error messages, more FAQs. 
1. Bug fixes: improve CPU compatibility checks, enclaves with non-public constructors are now loadable.
1. Security improvements and fixes.

Please read the list of [known issues](known-issues.md).
