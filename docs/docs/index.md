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
- A set of Gradle build plugins, so compiling and signing your enclave is handled automatically.
- A powerful unit testing framework to verify the operation of your enclave and remote attestation functionality, using just JUnit.
- API designs that guide you towards SGX best practices and avoidance of security pitfalls.
- Many tutorials, guides and commercial support from the SGX experts at R3.

## Documentation

[**Enclaves.**](enclaves.md) If you're totally new to enclave development, start with our introduction to enclave-oriented
design. This will explain the concepts referred to in the rest of the documentation.

[**Architectural overview.**](architecture.md) This explains the core Conclave APIs.

[**Tutorial.**](tutorial.md) Once you understand the concepts go straight to writing your first enclave.

[**Deployment.**](deployment.md) Learn how to obtain SGX capable hardware, set it up, deploy to production
and then keep your machine trusted by applying updates.

[**Reference guide.**](api/index.html) We provide detailed JavaDocs for the API.

