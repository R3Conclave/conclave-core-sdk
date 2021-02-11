---
hide:
- navigation
---

# Conclave

Conclave is a toolkit for building _enclaves_, small pieces of software that are protected from attack by the owner
of the computer on which they run. It is ideally suited to solving multi-party collaboration and privacy problems
but can also be used to secure your infrastructure against attack.

**[Visit the main Conclave website](https://www.conclave.net)**

## Why Conclave?

- High level, simple API that is much easier to use than other enclave APIs.
- Write your host app in any language that can run on a Java Virtual Machine like Java, Kotlin or even 
  [JavaScript](javascript.md). Write your enclave using the GraalVM native image technology for incredibly tight memory 
  usage, support for any GraalVM language and instant startup time. Eliminate all memory management errors that would 
  undermine the security of your enclave, thanks to the built-in compacting generational garbage collector.
- Develop gracefully on all operating systems, not just Linux: Windows and macOS are fully supported as well.
- Full support for auditing enclaves over the internet, including remote attestation and fully deterministic,
  reproducible builds. A user can verify what the source code of the remotely running enclave is, to ensure it will
  behave as they expect.
- A message oriented communication and storage system that eliminates size-based side channel attacks and integrates with
  the Intel SGX secure upgrade mechanisms. Roll forward through security upgrades without clients being aware of it.
- A Gradle plugin to automate compiling, signing and calculating the code hash of your enclave. No need to use the Intel
  SDK - everything needed is included.
- API designs that guide you towards SGX best practices and avoidance of security pitfalls.
- Easily deploy to Microsoft Azure Gen2 VMs by just uploading your Java app and running it as normal. There is no setup!
- A powerful unit testing framework to verify the operation of your enclave and remote attestation functionality, 
  using just JUnit.
- Integrate and benefit from [Corda](https://www.corda.net), an open source peer-to-peer network for business uses with
  enterprise support.
- Tutorials, guides, design assistance and commercial support from the SGX experts at R3. Friendly devs on our Slack 
  channel and mailing list, even if you don't have a proper support contract!
  
Finally, **Conclave is free for non-commercial open source projects**, and there are easy licensing terms for individuals 
and early stage startups.

## Documentation

Click through the tabs above to see all our documentation. If you're not sure where to start, these pages are good:

[**Enclaves.**](enclaves.md) If you're totally new to enclave development start with our introduction to enclave-oriented
design. This will explain the concepts referred to in the rest of the documentation.

[**Architectural overview.**](architecture.md) This explains the core Conclave APIs and how it's structured.

[**Tutorial.**](tutorial.md) Once you understand the concepts go straight to writing your first enclave.

[**Enclave Configuration.**](enclave-configuration.md) Now you've created your first enclave, take a deeper look at the configuration options
available for creating enclaves.

[**Using JavaScript.**](javascript.md) How to use JIT compiled JavaScript inside the enclave.

[**Machine setup.**](machine-setup.md) Learn how to obtain SGX capable hardware, set it up, deploy to production
and then keep your machine trusted by applying updates.

[**Writing CorDapps with Conclave**](writing-cordapps.md) You'll need a way for your users to get data to and from your
service that has integrated identity, workflow, firewall handling, database integration and more. Corda is an enterprise
blockchain platform that offers many useful features when you progress beyond encrypting your business logic.

[**Reference guide.**](api/index.html) We provide detailed JavaDocs for the API.

## Get in touch

R3 offers [full ticket based commercial support](https://conclave.net/get-conclave/).

There's a public mailing list for discussion of using Conclave and we also welcome general SGX talk. A Slack channel
is available where you can find the development team during UK office hours (GMT 0900-1700).

[:fontawesome-solid-paper-plane: Join conclave-discuss@groups.io](https://groups.io/g/conclave-discuss){: .md-button } [:fontawesome-solid-paper-plane: Email us directly](mailto:conclave@r3.com){: .md-button } [:fontawesome-brands-slack: Slack us in #conclave](https://slack.corda.net/){: .md-button } 



## Release notes

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
   on macOS. This helps you execute a simulation mode enclave without direct access to a Linux machine. Learn more about
   the [container-gradle](container-gradle.md) script.
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

### Beta 4

1. :jigsaw: **New feature!** Conclave now supports building [GraalVM Native Image](https://www.graalvm.org/docs/reference-manual/native-image/)
   enclaves on macOS and Windows! [GraalVM Native Image](https://www.graalvm.org/docs/reference-manual/native-image/)
   support was added in Beta 3 but required a Linux build system. Now, by installing Docker on Windows or macOS you
   can configure your enclaves to use the `graalvm_native_image` runtime and let Conclave simply manage the build process
   for you. Creating and managing the container is automated for you.
1. :jigsaw: **New feature!** Conclave now supports a new remote attestation protocol. That means it now works 
   out of the box on [Azure Confidential Compute VMs](https://docs.microsoft.com/en-us/azure/confidential-computing/),
   without any need to get an approved signing key: you can self sign enclaves and go straight to 'release mode' on
   Azure. Follow our tutorial on [how to deploy your app to Azure](azure.md) to learn more.
1. :jigsaw: **New feature!** [Easily enable and use JavaScript](javascript.md). It is JIT compiled inside the enclave, 
   warms up to be as fast as V8 and can interop with JVM bytecode. Full support for the latest ECMAScript standards.
1. :jigsaw: **New feature!** Mail is now integrated with the SGX data sealing and TCB recovery features. If a version of 
   the CPU microcode, SGX architectural enclaves or the enclave itself is revoked, old mail will be readable by the newly
   upgraded system, but downgrade attacks are blocked (old versions cannot be exploited to read new mail). This support
   is fully automatic and especially useful when using the 'mail to self' pattern for storage.
1. :jigsaw: **New feature!** The new `EnclaveHost.capabilitiesDiagnostics` API prints a wealth of detailed technical
   information about the host platform, useful for diagnostics and debugging.
1. `System.currentTimeMillis` now provides high performance, side-channel free access to the host's clock. The host
   copies the current time to a memory location the enclave can read, thus avoiding a call out of the enclave that
   could give away information about where in the program the enclave is. Remember however that as per usual, 
   the host can change the time to whatever it wants, or even make it go backwards.
1. Significantly improved multi-threading support. [Learn more about threads inside the enclave](threads.md). Write
   scalable, thread safe enclaves and use thread-pools of different sizes inside and outside the enclave.
1. Conclave's internal dependencies are better isolated. As a consequence it's now loadable from inside an app designed 
   for [R3's Corda platform](https://www.corda.net). Corda is one of the world's leading blockchain platforms and its 
   privacy needs are what drove development of Conclave. We plan to release a sample app showing Corda/Conclave 
   integration soon.  
1. API improvements! The API for receiving local calls into an enclave has been simplified, the mail API lets the host
   provide a routing hint when delivering, and the API for passing attestation parameters has been simplified due to the
   introduction of support for the new DCAP attestation protocol. [Learn more about the API changes](api-changes.md).
1. Mail has been optimised to reduce the size overhead and do fewer memory copies.
1. Bug fixes, usability and security improvements. Upgrade to ensure your enclave is secure. We've improved error 
   messages for a variety of situations where Conclave isn't being used correctly.

### Beta 3

1. :jigsaw: **New feature!** The Mail API makes it easy to deliver encrypted messages to the enclave that only it can
   read, with sequencing and separation of different mail streams by topic. Mail can also be used by an enclave to
   persist (sealed) data. [Learn more](architecture.md#mail)
1. :jigsaw: **New feature!** You can now compile your entire enclave ahead of time using 
   [GraaalVM Native Image](https://www.graalvm.org/docs/reference-manual/native-image/). This gives you access to a
   much better JVM than in prior releases, with faster enclaves that use less RAM. The performance improvement can be
   between 4x and 12x faster than in prior releases and memory usage can be up to 5x lower.
1. :jigsaw: **New feature!** New mock API for easy debugging between the host and enclave, fast unit testing and easy
   development of enclaves on machines that don't support the technology. [Learn more](writing-hello-world.md#mock). 
1. :jigsaw: **New feature!** You can now produce enclaves on macOS! Just follow the instructions as you would on a Linux
   developer machine, and a JAR with an embedded Linux enclave .so file will be produced automatically. You can then take
   that JAR and upload it to a Linux host for execution, e.g. via a Docker or webapp container (e.g. Tomcat). Combined
   with the equivalent Windows support we added in beta 2 and the easy to use mock enclave API, this completes our 
   developer platform support and allows mixed teams of people working on their preferred OS to build enclave-oriented
   apps together. Please note: at this time only the Avian runtime can be cross-compiled from Windows and macOS.
1. :jigsaw: **New feature!** You may now make concurrent calls into the enclave using multiple threads,.  
1. Remote attestations (serialized `EnclaveInstanceInfo` objects) now remain valid across enclave restarts. They may
   still be invalidated by changes to the SGX TCB, for example, microcode updates applied as part of an operating
   system upgrade.
1. Enclave/host communication now handles exceptions thrown across the boundary properly.
1. In order to prevent accidental leakage of information from inside enclaves, release builds of enclaves no 
   longer propagate console output across the enclave boundary. Calls to `System.out.println()` and related methods 
   will now only print to the console on simulation and debug builds of enclaves.

### Beta 2

1. :jigsaw: **New feature!** Build enclaves on Windows without any special emulators, virtual machines or other setup.
1. :jigsaw: **New feature!** Specify an enclave's product ID and revocation level in the enclave build file. There's a new
   `conclave` block which lets you do this. These values are enforced in any relevant `EnclaveConstraint` object.
1. :jigsaw: **New feature!** A new `EnclaveHost.checkPlatformSupportsEnclaves` API allows you to probe the host
   operating system to check if enclaves are loadable, before you try to actually do so. Additionally, if SGX is disabled
   in the BIOS but can be enabled by software request, Conclave can now do this for you. If the host machine needs
   extra configuration a useful error message is now provided in the exception. 
1. :jigsaw: **New feature!** Better support for enclave signing in the Gradle plugin. New documentation has been added showing how to
   sign with externally managed keys. 
1. You can now use the Conclave host API from Java 11. The version of Java inside the enclave remains at Java 8.
1. We've upgraded to use the version 2.9.1 of the Intel SGX SDK, which brings security improvements and lays the groundwork for new 
   features. Make sure your host system is also running version 2.9.1. We've also upgraded to the latest version of the Intel
   Attestation Service (IAS).
1. The ID for the enclave plugin is now `com.r3.conclave.enclave`. You will need to change this in your enclave's
   build.gradle file.
1. `enclave.xml` files are no longer needed. You can safely delete them, as they're now generated for you by Conclave.
1. The enclave measurement is now stable when built using different versions of the JDK.
1. The format of an `EnclaveInstanceInfo` has been optimised. Old `EnclaveInstanceInfo` objects won't work with the beta 2
   client libraries and vice-versa.
1. Java serialization is now formally blocked inside the enclave using a filter. Unfiltered deserialization has a history
   of leading to exploits in programs written in high level managed languages.
