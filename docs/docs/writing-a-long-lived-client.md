# Writing a long-lived client

## Prerequisites

This tutorial assumes you have read and understood [Writing your first enclave](writing-hello-world.md).

## Introduction

The introductory hello-world sample (see [Running your first enclave](running-hello-world.md)
and [Writing your first enclave](writing-hello-world.md)) implements a scenario where each run is independent, so the
client does not need to maintain any state between runs: it can generate a new one-time key each time and there is no
need to track a multi-step protocol.

Most real-world scenarios are more complex than this. In particular, the interaction between a client and
enclave may unfold over an extended period of time, meaning the client needs to be able to resume successfully after a
restart.

Conclave Mail uses the client's key as a form of identity and the enclave uses this to track the clients that
communicate with it. For example, if the enclave were hosting an auction, a client may want to use the same identity
when submitting multiple bids on the same lot.

## Add state to your client

[`EnclaveClient`](api/-conclave/com.r3.conclave.client/-enclave-client/index.html) provides a helpful
[`save`](api/-conclave/com.r3.conclave.client/-enclave-client/save.html) method which will serialize the necessary 
state to bytes and which can then
be used at a later point to restore the client using the `EnclaveClient` constructor that takes in a byte array.

For example, the Client's state could be written to the file before the client terminates.

!!! warning
    The bytes contain the client's private key, so if using a file, it **must** be stored securely or encrypted.

```java 
enclaveClient = new EnclaveClient(Files.readAllBytes(stateFile));

// If a constraint was provided to the client, ensure that it matches the
// constraint loaded from the file.
EnclaveConstraint loadedConstraint = enclaveClient.getEnclaveConstraint();
if (providedConstraint != null && loadedConstraint != providedConstraint) {
    throw new IllegalArgumentException(
            "Constraint provided to client and constraint loaded from "
                    + stateFile.getFileName() + " are not the same."
    );
}
    
// TODO client interacts with enclave.

Files.write(stateFile, enclaveClient.save())
```

Notice how an [enclave constraint](constraints.md) is not required when loading the state from the file.
In general, we assume that the constraint will not change for a long-lived client. The constraint can always be
updated using [`EnclaveClient.setEnclaveConstraint`](api/-conclave/com.r3.conclave.client/-enclave-client/set-enclave-constraint.html).
