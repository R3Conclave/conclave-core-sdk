# cpp/zlib
This is the `zlib` library with just a minor patch to make the C/C++ build deterministic.

It is used by Graal Native Image when invoked by the
[Conclave plugin](../../plugin-enclave-gradle/src/main/kotlin/com/r3/conclave/plugin/enclave/gradle/GradleEnclavePlugin.kt).
