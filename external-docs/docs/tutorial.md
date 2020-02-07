# First enclave

!!! notice

    This tutorial assumes you've read and understood the [conceptual overview](enclaves.md).

We'll go through these steps to make an enclave:

1. Apply the Conclave Gradle plugin. Conclave projects at this time require the use of the Gradle
   build system.
2. Create a new subclass of [`Enclave`](api/com/r3/conclave/enclave/Enclave.html)
3. Build and load it in simulation mode.
4. Implement the `EnclaveCall` interface for local communication.
5. Prepare your hardware for SGX.
6. Build and load the second version in debug mode.
7. Write a client and host to enable remote attestation.

!!! warning

    TODO: COMPLETE THIS TUTORIAL
