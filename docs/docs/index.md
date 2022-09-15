---
hide:
- navigation
---

# Introduction to Conclave

Welcome to the Conclave documentation. These pages cover what problems Conclave can solve, concepts and architecture, and how you can get started using Conclave.

## Why Conclave?

Data owners lose absolute control over their information when they share it with third parties.
[Conclave](https://www.conclave.net/) helps data owners to regain control by enabling users to build, deploy, and integrate privacy-first applications at scale.

With Conclave, you can build:

- Applications that safeguard data while in use.
- Applications that provide digital evidence that no one has tampered with the data.
- Solutions for secure multi-party data sharing.

## What is Conclave?

Conclave is a software development kit for the rapid development of privacy-first applications using hardware-based Trusted Execution Environments (TEEs) known as [enclaves](enclaves.md).
Conclave makes [Confidential Computing](enclaves.md) using Intel® Security Guard Extension (SGX) more accessible for 
developers. Take a look at our source code on [this GitHub repository](https://github.com/R3Conclave/conclave-core-sdk).

### Advantages of Conclave

- **Developer-friendly:** You don't have to be a cryptography expert to use Conclave. A high-level, intuitive API helps you write secure applications on any operating system.
- **Code in many languages:** Write your code in Java, Kotlin, or any other JVM (Java Virtual Machine) language. You can also use Javascript and basic Python to write your application.
- **Open Source:**: You can [verify the source code](https://github.com/R3Conclave/conclave-core-sdk) and remove Conclave from your trust model.
- **Focus on business logic:** Interact with secure enclaves easily using an intuitive API. **Conclave Mail** takes care of the end-to-end encrypted messaging.
- **Everything in one platform:** The Core SDK tightly integrates with a
  [complementary cloud offering](https://www.conclave.net/conclave-cloud/).
- **Cloud-based Key Derivation Service (KDS):** Create stable applications that maintain Intel SGX security guarantees even when the enclave runs on multiple physical machines.
- **Enhanced security, performance, and capabilities:** Conclave uses the GraalVM native image technology for incredibly tight memory usage, support for GraalVM languages, and instant startup time.
- **Quickly deploy to Microsoft Azure:** Run Conclave apps on Microsoft Azure VMs with minimal setup.
- **Audit your enclaves remotely:** Verify the source code of the remotely running enclave.
- **Convenient Gradle plugin:** Automate compiling, signing, and calculating the code hash of enclaves using a simple Gradle plugin.
- **Powerful unit testing framework:** Verify remote attestation and the operation of enclaves using JUnit.

## Getting started

Take a look at the [hello world sample](running-hello-world.md) to get started with Conclave.

## Get help with Conclave

If you need help:

- Ask questions on [Discord](https://discord.gg/zpHKkMZ8Sw). Conclave's development team answers questions during UK business hours (GMT 0900-1700).
- Discuss everything about Conclave on [this public mailing list](https://groups.io/g/conclave-discuss). You can also use the mailing list to discuss SGX and other TEE technologies.


[:fontawesome-solid-paper-plane: Join conclave-discuss@groups.io](https://groups.io/g/conclave-discuss){: .md-button }
[:fontawesome-solid-paper-plane: Email us directly](mailto:conclave@r3.com){: .md-button }
[:fontawesome-brands-discord: Join us on Discord](https://discord.gg/zpHKkMZ8Sw){: .md-button }
