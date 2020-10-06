# Conclave

Conclave is a toolkit for building _enclaves_, small pieces of software that are protected from attack by the owner
of the computer on which they run. It is ideally suited to solving multi-party collaboration and privacy problems.

## Why Conclave?

- Write your host app in any language that can run on a Java Virtual Machine. Write your enclave using the GraalVM
  native image technology for incredibly tight memory usage, support for any GraalVM language and instant startup time.
  Eliminate all memory management errors that would undermine the security of your enclave, thanks to the built-in 
  generational garbage collector.
- High level, simple API that is much easier to use than other enclave APIs.
- Full support for auditing enclaves over the internet, including remote attestation and fully deterministic,
  reproducible builds. A user can verify what the source code of the remotely running enclave is, to ensure it will
  behave as they expect.
- A Gradle plugin to automate compiling, signing and calculating the code hash of your enclave. No need to use the Intel
  SDK - everything needed is included.
- API designs that guide you towards SGX best practices and avoidance of security pitfalls.
- A powerful unit testing framework to verify the operation of your enclave and remote attestation functionality, using just JUnit.
- Tutorials, guides and commercial support from the SGX experts at R3.

## Documentation

[**Enclaves.**](enclaves.md) If you're totally new to enclave development, start with our introduction to enclave-oriented
design. This will explain the concepts referred to in the rest of the documentation.

[**Architectural overview.**](architecture.md) This explains the core Conclave APIs.

[**Tutorial.**](tutorial.md) Once you understand the concepts go straight to writing your first enclave.

[**Machine setup.**](machine-setup.md) Learn how to obtain SGX capable hardware, set it up, deploy to production
and then keep your machine trusted by applying updates. 

[**Reference guide.**](api/index.html) We provide detailed JavaDocs for the API.

## Get in touch

There's a public mailing list for discussion of using Conclave and SGX. Join [conclave-discuss@groups.io](https://groups.io/g/conclave-discuss).

You can also [email us directly](mailto:conclave@r3.com). In future R3 will offer ticket based commercial support. 

!!! warning
    This is a **developer preview** release of Conclave. You **may not** run enclaves built with it in production. 
    Please read the list of [known issues](known-issues.md).

## Release notes

### Beta 4

1. :jigsaw: **New feature!** Conclave now supports building [GraaalVM Native Image](https://www.graalvm.org/docs/reference-manual/native-image/)
   enclaves on macOS and Windows! [GraaalVM Native Image](https://www.graalvm.org/docs/reference-manual/native-image/)
   support was added in Beta 3 but required a Linux build system. Now, by installing Docker on Windows or macOS you
   can configure your enclaves to use the `graalvm_native_image` runtime and let Conclave simply manage the build process
   for you.
2. :jigsaw: **New feature!** Conclave now supports DCAP along with EPID attestation. NOTE: This breaks compatibility with previous versions.

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
