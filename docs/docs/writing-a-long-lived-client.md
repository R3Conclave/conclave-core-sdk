# Writing a long-lived client

## Prerequisites

Complete the following tutorials.

* [Running your first enclave](running-hello-world.md).
* [Writing your first enclave](writing-hello-world.md).
* [Writing your own enclave host](writing-your-own-enclave-host.md).

## Introduction

In the [hello-world sample](running-hello-world.md) and [writing your first enclave](writing-hello-world.md) tutorial,
each run is independent. In such cases, the client doesn't need to maintain any state between runs.

However, in most real-world applications, the client and the enclave interact over an extended period. To support such
applications, the client has to maintain its state and resume successfully after a restart.

## Add state to your client

[`EnclaveClient`](api/-conclave%20-core/com.r3.conclave.client/-enclave-client/index.html) provides a
[`save`](api/-conclave%20-core/com.r3.conclave.client/-enclave-client/save.html) method to serialize the state to 
a bytes array. You can call the `EnclaveClient` constructor with this byte array to restore the client.

!!!Warning
    
    If you are using a file to store the bytes, you must store it securely as it contains the client's private key.

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
    
// Code where the client interacts with the enclave.

Files.write(stateFile, enclaveClient.save())
```

Note that you don't need an [enclave constraint](constraints.md) to load the state from the file. This is because the
constraint doesn't usually change for a long-lived client. You can update the constraint, if necessary, using
[`EnclaveClient.setEnclaveConstraint`](api/-conclave%20-core/com.r3.conclave.client/-enclave-client/set-enclave-constraint.html).
