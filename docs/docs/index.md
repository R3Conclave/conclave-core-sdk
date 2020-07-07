# Conclave

Conclave is a toolkit for building _enclaves_, small pieces of software that are protected from attack by the owner
of the computer on which they run. It is ideally suited to solving complex business collaboration and privacy problems.

## Why Conclave?

- An embedded Java Virtual Machine, capable of running the full Java 8 platform inside an enclave with encrypted RAM.
  You can eliminate all memory management errors that would undermine the security of your enclave, thanks to the built-in generational garbage collector.
- Write enclaves in Java, Kotlin, Scala, Haskell (via Eta) or any other language that can target bytecode.
- Full support for auditing enclaves over the internet, including remote attestation and fully deterministic,
  reproducible builds. A user can verify what the source code of the remotely running enclave is, to ensure it will
  behave as they expect.
- A Gradle plugin, so compiling and signing your enclave is handled automatically.
- API designs that guide you towards SGX best practices and avoidance of security pitfalls.
- Many tutorials, guides and commercial support from the SGX experts at R3.
- A powerful unit testing framework to verify the operation of your enclave and remote attestation functionality, using just JUnit.

## Documentation

[**Enclaves.**](enclaves.md) If you're totally new to enclave development, start with our introduction to enclave-oriented
design. This will explain the concepts referred to in the rest of the documentation.

[**Architectural overview.**](architecture.md) This explains the core Conclave APIs.

[**Tutorial.**](tutorial.md) Once you understand the concepts go straight to writing your first enclave.

[**Machine setup.**](/machine-setup/) Learn how to obtain SGX capable hardware, set it up, deploy to production
and then keep your machine trusted by applying updates. 

[**Reference guide.**](api/index.html) We provide detailed JavaDocs for the API.

## Get in touch

There's a public mailing list for discussion of using Conclave and SGX. Join [conclave-discuss@groups.io](https://groups.io/g/conclave-discuss).

You can also [email us directly](mailto:conclave@r3.com). In future R3 will offer ticket based commercial support. 

## Known issues

!!! warning
    This is a beta release of Conclave. You should not run enclaves built with it in production.

This release ships with the following known issues:

1. The API may change up until version 1.0. Although we have no current plans to change the API, small changes like 
   package names may still occur and we may adapt the API based on user feedback during the beta period.
2. There's currently no API for sending and receiving encrypted messages to/from enclaves.
3. Conclave doesn't presently implement any side channel attack mitigations.
4. Enclave keys aren't yet stable and change across enclave restarts, forcing re-attestation each time the host
   process starts.
5. Some system level exceptions like divide by zero or using null reference crash the enclave/host process.
6. The type of attestation used currently requires you to sign up with and be whitelisted by Intel. Future versions
   will implement "DCAP attestation" which will allow the owner of the hardware to whiteliste enclaves, not just Intel.

## Release notes

### Beta 3

1. :jigsaw: **New feature!** New mock API for unit testing enclaves and for easy debugging between the host and enclave.
   Add `conclave-testing` as a `testImplementation` dependency to your project.

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
