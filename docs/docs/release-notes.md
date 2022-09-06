# Release notes

## 1.3

1. :tada: **The Conclave Core SDK is now open source!** :tada: Read our
   [blog post](https://www.conclave.net/blog/announcing-conclave-sdk-1-3-and-open-source) on why we did this and what it
   means for you. You can find the source code for the SDK [here](https://github.com/R3Conclave/conclave-core-sdk).
2. The SDK artifacts are now available on [Maven Central](https://search.maven.org/search?q=conclave). There's no
   longer any need to have a local repo directory in your Conclave project. See the
   [API changes page](api-changes.md#maven-central) for more details.
3. The Core SDK powers our new Conclave Cloud platform. Head over to [conclave.cloud](https://conclave.cloud) to learn 
   more.
4. :jigsaw: **New feature!** The Conclave Key Derivation Service (KDS) is out of beta and now supports production
   workloads. The REST API docs can be found [here](kds-rest-api.md).
5. :jigsaw: **New feature!** Support for stable enclave encryption keys with Mail by using the KDS. This enables use 
   cases where the enclave can restart or move to a different physical machine without affecting the client. It also 
   enables horizontally-scaled enclave solutions. See the API docs for the new
   [KDS post office](api/-conclave%20-core/com.r3.conclave.client/-post-office-builder/using-k-d-s.html) for more details.
6. :jigsaw: **Java 17** is now supported inside the enclave. There's no need to configure anything. Just ensure 
   you're using JDK 17 when building your enclave to benefit from the new language features.
7. Exceptions thrown during enclave startup in release mode now propagate to the host. This provides better feedback if
   the enclave is unable to start.
8. Gradle 7 is now supported.
9. GraalVM has been updated to version 22.0.
10. Intel SGX SDK has been updated to 2.17.1. This provides bug fixes, security updates, and other improvements. See the
    [SGX SDK release notes](https://github.com/intel/linux-sgx/releases) for more details.
11. Conclave now supports Ubuntu 20.04 LTS and 18.04 LTS. 16.04 LTS is no longer supported.
12. We've introduced the concept of beta APIs to facilitate quick iterative feedback on APIs before they're finalized. 
    Anything annotated with [`@Beta`](api/-conclave%20-core/com.r3.conclave.common/-beta/index.html) is subject to change 
    and may even be removed in a later release.
13. :jigsaw: **Beta feature** New API method which creates an attestation quote with custom report data, for use with
    external SGX-enabled applications which require a signed quote with specific content. See
    [`Enclave.createAttestationQuote`](api/-conclave%20-core/com.r3.conclave.enclave/-enclave/create-attestation-quote.html)
    for more information.
14. We have added Intel advisory IDs to DCAP-based attestation reports. These provide more information on any 
    platform vilnerabilites that may be present on the system.

Please read the list of [known issues](known-issues.md).

## 1.2.1

This is a small release with some minor improvements:

1. Compatibility with some libraries (such as [Tribuo](https://github.com/oracle/tribuo)) has been improved.
2. The CorDapp sample has been updated to use Corda v4.8.5, which is patched against the "Log4Shell" vulnerability.
3. Better error message by the plugin if no enclave class is found.

## 1.2

!!! important
    There have been some breaking changes in 1.2. Be sure to read the [API changes page](api-changes.md) on how to
    migrate your existing project.

!!! important
    In our previous release we had deprecated Avian support. This has now been removed completely in 1.2. Enclaves built
    with GraalVM native image had many benefits over Avian enclaves, including enhanced security, performance and
    capabilities.

1. :jigsaw: **New feature!** The Conclave Key Derivation Service (KDS) eliminates the restriction of the enclave
   sealing key being tied to a single physical CPU and thus unlocking cloud deployments. You can now easily migrate
   data from one VM to another, unlock clusters and high-availability architectures, and enable seamless
   redeployment of VMs by cloud service providers. [Learn more about the KDS and how to start using the
   public preview](kds-configuration.md).

1. :jigsaw: **New feature!** We've vastly improved how data is persisted inside the enclave. Previously we
   recommended the "mail-to-self" pattern for storing data across enclave restarts. This is cumbersome to write, not
   easy to understand and does not provide rollback protection against the host. To address all these issues, the
   enclave has a simple [key-value store](api/-conclave%20-core/com.r3.conclave.enclave/-enclave/get-persistent-map.html)
   represented as a `java.util.Map` object. Conclave will securely persist this map such that it survives
   restarts and is resilient to attempts by the host to roll it back to previous states. Find out more
   [here](persistence.md#persistent-map).

1. :jigsaw: **New feature!** We've actually introduced two forms of enclave persistence in 1.2! The rollback protection
   provided by the persistent map above may not be needed and comes at a cost of increased overheads. As an alternative
   the in-memory file system inside the enclave can be persisted directly to disk as an encrypted file on the host
   for faster performance. [Find out more here](persistence.md#conclave-filesystems).

1. :jigsaw: **New feature!** To eliminate the need to write the same boilerplate code for the host, we've introduced a
   simple new host web server which exposes a REST API for sending and receiving mail and which implements the
   necessary behavior of an enclave host. Your host module only needs to reference `conclave-web-host` as a
   runtime dependency and then all of the boilerplate host code can be done away with. Have a look at the updated hello
   world sample to see how it's used.

1. :jigsaw: **New feature!** To complement the host web server, we've also introduced a client library to make it
   super easy to write a web-based enclave client. Add `conclave-web-client` as a dependency to your client module
   and make use of the new [`WebEnclaveTransport`](api/-conclave%20-core/com.r3.conclave.client.web/-web-enclave-transport/index.html) class in
   conjunction with the new [`EnclaveClient`](api/-conclave%20-core/com.r3.conclave.client/-enclave-client/index.html).

1. :jigsaw: **New feature!** [`EnclaveClient`](api/-conclave%20-core/com.r3.conclave.client/-enclave-client/index.html) is a
   new API in `conclave-client` which greatly simplifies your client code and handles all the complexities when
   communicating with an enclave. It is agnositic to the transport layer between it and the host and support for
   other network protocols beside HTTP can be added.

1. :jigsaw: **Java 11** is now the default JDK version inside the enclave. You can make use of the new APIs and
   features introduced since Java 8 when writing your enclave code. For compatibility, the Conclave libraries are still
   compiled using Java 8. So you can continue to use Java 8 (or above) outside the enclave.

1. :jigsaw: **New feature!** We have made it easier than ever to start a Conclave project using our new tool,
   [Conclave Init](conclave-init.md).

1. :jigsaw: **New feature!** We've added enclave lifecycle methods so that you can do any necessary enclave startup
   initialization and shutdown cleanup. Override [`onStartup`](api/-conclave%20-core/com.r3.conclave.enclave/-enclave/on-startup.html) and
   [`onShutdown`](api/-conclave%20-core/com.r3.conclave.enclave/-enclave/on-shutdown.html) respectively.

1. :jigsaw: **New feature!** The host can now update the enclave's attestation without having to restart it.
   Previously restarting was the only way to force an update on the [`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html)
   object. Now you can call [`EnclaveHost.updateAttestation`](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/update-attestation.html)
   whilst the enclave is still running and the [`enclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/get-enclave-instance-info.html)
   property will be updated.

1. :jigsaw: **New feature!** We've further improved the Conclave plugin and added more automation so that
   you have to write less boilerplate. It's no longer necessary to add the `conclave-enclave` library as a
   dependency to your enclave module. Also, the plugin will automatically add `conclave-host` as a
   `testImplementation` dependency to enable mock testing. And finally the plugin will make sure any enclave
   resource files in `src/main/resources` are automatically added. Previously resource files had to be specified
   manually.

1. :jigsaw: **New feature!** We've added a new overload of [`EnclaveHost.load`](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/load.html)
   which no longer requires having to specify the enclave class name as a parameter. Instead,
   [`EnclaveHost`](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/index.html) will scan for the single matching
   enclave on the classpath.

1. :jigsaw: New experimental feature! Easily enable and use Python. It is JIT compiled inside the enclave and can
   interop with JVM bytecode. Use this feature with care. Python support is still in an experimental state. While it
   is possible to run simple Python functions, importing modules will likely lead to build issues.

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
