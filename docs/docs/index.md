---
hide:
- navigation
---

# Introduction to Conclave

Welcome to the Conclave documentation. The documentation is best place to start with Conclave. We cover what problems Conclave can solve, talk about concepts and its architecture, and how you can get started using it.

## Why Conclave?

[Conclave](https://www.conclave.net/) solves challenges that organisations of all sizes encounter when they process sensitive data. This ranges from secure data sharing across multiple parties, to solutions that provide transparency into how data is processed. In today’s connected, cloud-first world, data owners don’t have control over their information when shared with third parties. How can they regain control again? We developed Conclave which enables users to build, deploy, and integrate privacy-first services at scale.

## What is Conclave?

Conclave is a software development kit for the rapid development of privacy-first applications using hardware-based trusted execution environments (TEEs), also known as [enclaves](enclaves.md). Conclave makes confidential computing accessible to a broad range of developers because it doesn’t require deep knowledge of the underlying hardware.

**Key features of Conclave:**

-	**Developer-friendly:** You don’t have to be a cryptography expert. A high-level, intuitive API brings the world of trusted execution to regular business developers who can write secure applications on any operating system.
-	**Code in the language of your choice:** Write your host app in any language that can run on a Java Virtual Machine like Java, Kotlin or even JavaScript or Python.
-	**Focus on business logic:** Conclave’s innovative ‘Mail’ feature takes care of the messaging and persistence, allowing you to focus on the business logic. All the complexities of interacting with secure enclaves are abstracted behind an intuitive API.
-	**Everything in one platform:** Conclave Core is one of the few SDKs that tightly integrates with a complementary cloud offering, providing a seamless path from development to deployment, and including features that enable workloads to migrate between cloud servers transparently.
-	**Cloud based Key Derivation Service (KDS):** Get the benefit of stable, persistent keys regardless of which physical machine the enclave is running on, and thus also making it possible to migrate enclave state/data by maintaining SGX security guarantees without affecting the performance.
-	**Enhanced security, performance, and capabilities:** Conclave uses the GraalVM native image technology for incredibly tight memory usage, support for any GraalVM language and instant startup time. 
-	**Easily deploy to Microsoft Azure:** Just upload your Java host app and run it as normal. No setup needed!
-	**Compatible with Corda Enterprise:** Conclave is available as stand-alone but also compatible with R3’s flagship DLT platform Corda Enterprise
-	**Audit your enclaves remotely:** Verify the source code of the remotely running enclave.
-	**Convenient Gradle plugin:** Automat compiling, signing, and calculating the code hash of your enclave using a simple Gradle plugin.
-	**Powerful unit testing framework:** Verify the operation of your enclave and remote attestation functionality, using just JUnit.

## Getting started

If you are interested in trying Conclave, download it [here](https://www.conclave.net/get-conclave/) and check out the [tutorial](https://docs.conclave.net/running-hello-world.html) on how to run your first enclave.

* **[Get Conclave](https://conclave.net/get-conclave/)**
    * Conclave 1.0 SHA2: `c3430d7172b2b0ab15a19930558f8c18c64974bb113dfd2c0722d067cdf3fee5`
    * Conclave 1.1 SHA2: `3d47ae8a9fb944d75fb4ee127cd9874c04343643c830e1fe68898c3c93891ca2`
    * Conclave 1.2 SHA2: `072642ce92e277567794739c4a080414a3313f186208d0cb118945cbcc859682`
    * Conclave 1.2.1 SHA2: `06cb37bb9b8b36275322b7eeda2fec3ab2cd5d0f23192841c6efd5320e9c2026`

## Get help with Conclave

There's a public [mailing list](https://groups.io/g/conclave-discuss) for discussion of using Conclave and we also welcome general SGX talk. You can also 
find the development team during UK office hours (GMT 0900-1700) on [Discord](https://discord.gg/8RhkXc5eFp).

[:fontawesome-solid-paper-plane: Join conclave-discuss@groups.io](https://groups.io/g/conclave-discuss){: .md-button }
[:fontawesome-solid-paper-plane: Email us directly](mailto:conclave@r3.com){: .md-button }
[:fontawesome-brands-discord: Join us on Discord](https://discord.gg/8RhkXc5eFp){: .md-button }
