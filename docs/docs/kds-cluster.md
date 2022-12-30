# KDS cluster

The KDS cluster holds the master key, which it uses to _derive_ the keys needed for production enclaves. You can use 
the KDS cluster to get stable keys for persisting data independent of the physical system on which the enclave is 
running.

R3 centrally manages the KDS cluster. It consists of a group of enclaves known as _nodes_.

## KDS nodes

Each KDS node contains the fat JAR of the KDS host module. This fat JAR includes the enclave and the Spring Boot web 
server, which exposes the REST interface. 

A KDS node can perform the following functions:

* Give keys to external enclaves after validating them.
* Send and receive the master key among other nodes in the cluster. 

If an individual node loses the master key, it can recover the master key from the other nodes in the KDS cluster.

There are three types of nodes.

1. The Seeder Node.
2. Service Nodes.
3. Backup Nodes.

### The seeder node

The seeder node is the first node in a KDS cluster. It provides the root seed for the master key.

When the seeder node is created, it _randomly_ generates the _master key_, seals it in its local machine, and exits. 
If a previously sealed master key is present in the node's file system, the process exits with an error. This check 
prevents accidental duplication of the master key.

The seeder node uses the `--generate-cluster-master-key` command to generate the master key. After generating the 
master key, the seeder node is changed into a service node using the `--service` command.

When in production, the KDS cluster does not have a seeder node. Instead, all the service nodes have a copy of the 
master key sealed in their file system.

### Service nodes

The service nodes are the nodes that handle key requests from external enclaves.

When a new node joins the KDS cluster, it runs the `--fetch-cluster-master-key` command to get the master key from 
the cluster. Then it runs the `--service` command to become a service node. The number of service nodes in the KDS 
cluster can be increased or decreased as needed.

When the KDS cluster receives a key request, the service nodes evaluate the authenticity of the request. The KDS API 
ensures that the service nodes return the key only if the request is from the correct enclave by using the following:

* Conclave's attestation.
* Enclave Constraints.
* Conclave Mail's end-to-end encryption.

If the request is from the correct enclave, a service node derives the enclave's key from the master key using a
deterministic [HMAC Key Derivation Function (HKDF)](https://en.wikipedia.org/wiki/HKDF). As the KDS derives keys using a
deterministic HKDF, it doesn't need a database to store individual keys. It needs to store only the master key. This 
secure, stateless design makes the KDS cluster scalable.

### Backup nodes

The backup nodes are offline, bare-metal machines in which the master key is sealed and stored locally in an 
encrypted form. If the KDS cluster loses the master key in an unlikely event, it can retrieve the master key from the 
backup nodes.

The backup nodes are _not_ a part of the KDS cluster. The backup nodes are _not_ a part of the KDS cluster. Backup 
nodes must be periodically tested to ensure that they can decrypt their copy of the master key.

## Master key exchange between nodes

This section explains how the nodes securely share the master key inside the KDS cluster. The master key is distributed
across all the nodes in the KDS cluster. The nodes use [Conclave Mail](mail.md) (which uses Noise Protocol and Diffie 
Hellman key exchange) for sharing the master key with each other.

### Master key request

When a node joins the KDS cluster, it triggers a `POST` request on the `/master-key` endpoint of the KDS cluster. 
This request is a JSON object containing the serialized `EnclaveInstanceInfo` of the requesting KDS enclave, encoded
as a Base64 string.

### Master key response

The node that receives the master key request checks if the MRENCLAVE (`requestingEii.enclaveInfo.codeHash`) of 
the incoming `EnclaveInstanceInfo` matches with its own MRENCLAVE. If they match, the node creates a Mail object using:

* The `EnclaveInstanceInfo` of the node that requests the master key.
* The encrypted master key.
* A serialized copy of `EnclaveInstanceInfo` of the node that sends the master key (in the Mail envelope).

The node returns the Mail object containing the master key as a serialized, Base64-encoded JSON string.

```json
{
  "masterKeyResponseMail": <base64 encoded Mail and enclave instance info>
}
```

### Master key verification

The node receiving the encrypted master key checks:

1. If the MRENCLAVE of the responding node is the same as its own.
2. If the Mail object originated from the responding node (by comparing the `authenticatedSender` field in the Mail 
   object with the `encryptionKey` field in the `EnclaveInstanceInfo`).
3. If the master key matches with the existing master key (if the node already has the master key).

If all these checks pass, the node accepts the master key and saves it. Otherwise, the node exits with an error code.

### Master key storage

After verifying the master key, a node saves it in the enclave's encrypted file system. This process encrypts the master
key and the enclave's file system to a physical file on the host using an MRSIGNER-derived SGX sealing key.