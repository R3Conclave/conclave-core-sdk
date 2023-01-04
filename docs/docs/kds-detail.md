# Persisting data with the Key Derivation Service (KDS)

R3 offers the Key Derivation Service (KDS) for any Conclave enclave to obtain stable keys for persisting data. These 
stable keys enable an enclave to encrypt data on an untrusted data store and retrieve data when needed. With the KDS,
an enclave can restore its state after a restart, even from a different physical system.

## Properties of an encryption key for persisting data 

For an enclave to persist data on an untrusted data store and retrieve it irrespective of the CPU in which the 
enclave resides, it needs an encryption key with the following properties:

1. The key must be unique to a particular enclave configuration.
2. The developer should be able to configure the key to be tied to any combination of the enclave code, version, 
   signer, or other attributes of the enclave/platform.
3. The key must be only available inside enclaves that match the required configuration.
4. It must be possible to deterministically regenerate the key, even if the enclave or the server restarts.
5. The key must _not_ be unique to one CPU.


## CPU keys for 'sealing' data

Intel SGX provides a root _sealing key_, fused into the CPU silicon. This sealing key is known only to the CPU. It 
can be used to derive other keys based on the configuration of the enclave. The sealing key fulfills the first four 
requirements listed above.

However, the sealing key doesn't fulfill the last requirement that the key must not be unique to one CPU. If you use 
the sealing key, data persisted by an enclave running on one CPU cannot be decrypted by another CPU, _even if it is 
running the same version of the enclave_. This restriction can cause the following problems:

* If you use bare-metal SGX servers, you might lose data if the hardware fails.
* Cloud Service Providers might transfer your enclave from one physical server to another. In such cases, you might 
  lose your data.

The KDS resolves these issues. When configuring your enclave, you can specify whether to use the KDS to obtain a key 
instead of using the sealing key.

## How the KDS works

The KDS has access to the [master key](#what-is-the-master-key), the root seed for all the keys requested from the 
KDS. To protect the master key and the key derivation process, Conclave has implemented the KDS inside an enclave. 
As the KDS resides in an enclave, application enclaves can verify the integrity of the KDS using remote attestation, 
using the KDS [`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html).
The KDS can also verify the integrity of application enclave instances using their `EnclaveInstanceInfo`.

When an enclave requests a key from the KDS, the KDS generates the key for the enclave by deriving it from the 
master key using an HMAC Key Derivation Function (HKDF) passing a set of parameters to generate a key unique for 
each distinct set of parameters. This set of parameters is called the _key specification_.

A key specification consists of the following fields:

| Field                 | Description                                                                           |
|-----------------------|---------------------------------------------------------------------------------------|
| Master Key Type       | The type of [master key](#what-is-the-master-key) to derive the key from.             |
| Key Policy Constraint | The conditions under which the KDS will allow an enclave to access the requested key. |

The developer provides a key specification within the code of the enclave. When the enclave restarts, it requests a 
key from the KDS using its key specification. This specification tells the KDS which type of master key to use and 
which enclaves should have access to the derived key.

The KDS uses the _key policy constraint_ in the key specification to provide access control for the keys. The 
constraint itself is used within the key derivation process. 

If a bad actor requests a key, it receives a different key, as it will have different key specifications.

The key policy constraint allows the enclave author to specify the conditions under which an enclave can access the key.
In effect, it allows the enclave to say to the KDS:

* _"Please give access to this key only to this exact version of this enclave"_. 
* Or alternatively, _"Please give access to this key only if this enclave is signed by this particular key, and  
  it's running on a secure platform"_. 

The key specification is defined when the enclave is built. This means that the key specification forms part of the 
hash measurement of the enclave. So, it can't be tampered with after the enclave has been built. Any tampering would be
detected, as the change in hash measurement would be apparent in a remote attestation of the enclave.

This design enables enclaves to mandate which other enclaves could access their keys. So, there is no need 
for a central administrator to provide access. The [KDS cluster](kds-cluster.md) uses this design to hold the master 
key in a decentralized fashion and derive the keys needed for production enclaves.

## Deriving keys from the key specification

The KDS generates the key for the enclave by deriving it from the master key using an HMAC Key Derivation Function 
(HKDF), passing the key specification as parameters to the function.

![](images/kds_key_derivation.png)

In the diagram above, an enclave built using Conclave requests a key from the KDS. This request is based on the 
configuration provided inside the enclave code. It includes the key specification, consisting of the type of master 
key to use and the key policy constraint.

The KDS checks the key specification to see if the request from the enclave is valid. If the enclave is not 
authorized, the KDS returns an error response to the enclave.

Suppose the enclave is authorized to have access to the requested key. The KDS derives the key from the master key 
using a standard HMAC Key Derivation Function (HKDF) using the parameters in the key specification. The KDS then 
securely sends the resulting unique key back to the Conclave enclave.

## Validation of key requests

When a developer configures their enclave, they specify a key policy constraint for the key that the enclave will 
request from the KDS. This constraint is very similar to the [`EnclaveConstraint`](api/-conclave%20-core/com.r3.conclave.common/-enclave-constraint/index.html)
that a client application uses to determine whether to trust an enclave.

![](images/kds_validate_enclave.png)

The KDS sends a key to an enclave only if the enclave passes certain validation checks. The KDS verifies the
[`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html) report from 
the enclave to determine whether the enclave meets the constraints defined in the key specification or not.

The enclave passes its [`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html)
to the KDS, along with the key specification for the key it is requesting. The KDS then validates the 
`EnclaveInstanceInfo` and then builds an `EnclaveConstraint` object using the key policy constraint in the key
specification and checks the `EnclaveInstanceInfo` against that constraint. If the enclave matches the constraints, 
the KDS provides the key to the enclave.

## Secure transfer of the key

The KDS securely transfers the key to the enclave by encrypting it using its public key in its 
[`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html). This process
guarantees that only the recipient enclave can decrypt the key received from the KDS.

But how does the enclave know that a valid instance of the KDS generated the key?

The KDS runs inside a Conclave enclave. So, the KDS has the same protections and assurances as any other enclave. It 
can use remote attestation to prove the following:

* The exact code running inside the KDS.
* The signing key used to sign the enclave.
* The KDS is running on a secure Intel SGX platform.

The application enclave uses remote attestation via its [`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html)
to prove its status to the KDS. Similarly, the KDS uses remote attestation via its own `EnclaveInstanceInfo` to
prove it's a valid, uncompromised KDS instance.

![](images/kds_key_exchange.png)

This process happens behind the scenes to developers using the Conclave Core SDK. The SDK and the KDS together perform
all required steps to ensure a secure key exchange.

# What is the 'Master Key'?

For the KDS to provide stable keys to enclaves developed with Conclave, the KDS needs access to a stable master key. 
The KDS has three types of master keys. Developers can choose which type of master key they need to use for their 
projects.

The following table describes the different types of master keys.

| Master key type              | Description                                                                                                                                                                                           |
|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `MasterKeyType.DEVELOPMENT`  | Use it to develop enclaves in mock, simulation, and debug mode. Please don't use it to derive keys for production enclaves.                                                                           |
| `MasterKeyType.CLUSTER`      | Use it to derive enclave keys in production enclaves. This master key exists across a [cluster of KDS nodes](kds-cluster.md) to increase availability and reduce the chance of losing the master key. |
| `MasterKeyType.AZURE_HSM`    | Use it to derive enclave keys in production enclaves if your application can trust Azure. This master key is backed by a [FIPS compliant](https://en.wikipedia.org/wiki/FIPS_140-2) Azure HSM.        |


## Migrating data from a previous version of an enclave

Developers can set the key specification such that only the exact version of an enclave can access the key. However, 
such a stringent key specification makes it challenging to migrate data from an enclave to a new version, as the KDS 
will not give the key to any new versions.

To enable enclave updates, developers need to relax the constraints. The simplest way is to allow any enclave for a 
particular product signed using the same key to access the key. In this case, all new enclaves for the particular 
product can access data encrypted with previous versions without any data migration.

A more secure solution is to introduce a minimum revocation level in the key specification constraint. A new version
of the enclave requests a key from the KDS specifying a minimum revocation level that matches the configuration
of the enclave. In this case, one version of the enclave and all higher versions will get the key.

As the key policy constraint is part of the key specification, changing it generates a different key. So, the new 
version of the enclave needs to request the previous key specification from the KDS to read the current persisted 
data and migrate it to the new key by re-encrypting it. This migration process does not happen automatically using 
the Conclave SDK but is likely to be introduced in a future version. [Please talk to us](https://discord.com/invite/zpHKkMZ8Sw)
if you would like more information about this.
