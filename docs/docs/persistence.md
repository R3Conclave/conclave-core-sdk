# Enclave persistence

## Conclave filesystems

Conclave enclaves can do file I/O operations, but these are not directly mapped to the host filesystem. 

Instead, all filesystem activity is mapped to an **in-memory** and/or to a **persisted** filesystem,
the latter being represented as a single file on the host.

With minimal configuration of sizes, you can choose which filesystems to use;
Conclave will mount them in a single filesystem structure according to your choice:

* in case you choose to have **both** the filesystems available, the persisted filesystem will be mapped to the `/` directory
and the directory `/tmp` will be reserved for the in-memory filesystem.
All files and directories that you write into `/tmp` directory will be considered as temporary
(as commonly happens on the Linux operating system).  
All the other locations will be used to persist your files.

* in case you choose to have **either** the in-memory or the persisted filesystem, the filesystem
of your choice will be mounted to the `/` directory and you can write your files and directories wherever you want.

## In-memory filesystem
The goal of the in-memory filesystem support is to allow libraries and applications to have a simple
'scratch space' for files to enable you to use file APIs to prepare data before it is encrypted for storage.

The in-memory filesystem does not allow persistence, and it is empty every time the enclave restarts.

## Persistent encrypted filesystem
When you need to persist files to make them available after your enclave
restarts, you can use the persisted filesystem.

This will not map your files into the host directly, but instead creates a
single encrypted file in the host filesystem containing a representation of the
enclave filesystem in an encrypted format.

The key that is used to encrypt the persistent filesystem can only be accessed
within Conclave enclaves, meaning that the entire filesystem is protected from
being read or modified by any observer outside the enclave, including the host.

The actual key that is used to encrypt the filesystem is derived from one of two
sources, based on how you configure your enclave. If you [provide the necessary
configuration to use the Conclave KDS](kds-configuration.md) then data persisted
in the filesystem can be accessed by your enclave regardless of which physical
system it is running on. If you do not provide a KDS configuration then the key
is derived from the root sealing key of the CPU, thus the filesystem data is
bound to that particular CPU. If you are running your enclave on a system
provided by a cloud service provider such as Azure, you should configure your
enclave to use the KDS otherwise if the cloud service provider redeploys your
service to a different physical system then you will lose access to the files
inside the enclave.

## Rewind attacks against the persistent filesystem
A rewind attack against the persistent filesystem can be performed by a
malicious host environment by deliberately restarting the enclave, providing an
older image of the encrypted filesystem. Intel SGX does not provide any way for
an enclave to determine if it has been restarted or if it has been reverted to a
previous state. To achieve this, the enclave will need to have access to a
security device such as a monotonic counter, or rely on a trusted external
entity to keep track of the current state.

To help prevent this type of attack, Conclave provides the [`persistentMap`
key-value store](persistence.md). This enlists the help of the client systems to
keep track of enclave state, giving the enclave a chance to detect replay
attacks.

The persistent filesystem does not automatically use the `persistentMap` to
protect against replays as the implementation of the prevention mechanism is
application specific. However, it is fairly easy to combine the use of the
`persistentMap` with the persistent filesystem to protect certain files. For
example, whenever you write a file to the filesystem that you want to protect,
you could generate a hash of the file contents, then store that hash in the
`persistentMap`. When reading the file, you can then verify the hash against the
value stored in the `persistentMap` giving some assurance against rewind
attacks.

When using the file hash-based approach in combination with the `persistentMap`
to detect replay attacks, care must be taken to ensure that the file data being
hashed is actually the file data that is then subsequently used by the enclave.
For example, consider these two attack scenarios:

### Associating new file data with a hash in the `persistentMap`
1. The enclave modifies or writes a file as part of its normal operation.
2. The enclave now _reads_ the file via the host for the purpose of computing
   the hash, and writes the hash into the persistent map.

The problem here is that the data the enclave is computing the hash on may not
be the data that it last wrote to the persistent filesystem if the host maliciously
serves stale or old file data to the enclave.

### Checking file contents against a hash in the `persistentMap`
1. The enclave reads a file for the purpose of computing its hash.
2. The enclave compares the calculated hash to the value previously stored in
   the `persistentMap`.
3. The enclave decides the data has not been rewound and continues to use
   filesystem operations to access the file.

In this scenario, the hash was correctly calculated over the latest version of
file but subsequent file operations on the same file may again request the data
from the host giving it the opportunity to serve stale data.

One way to protect from the above attacks is to ensure the enclave code keeps
the entire file data in memory whilst it is using and calculating hashes over
the file contents. A simple way to achieve this could be to copy the file into
the in-memory temporary filesystem when working with the file inside the
enclave, writing the file back to the persistent filesystem only after it has
been bound to the `persistentMap` using a hash.

## Additional information - How the persistent filesystem works
The persistent filesystem within the enclave provides the developer with access
to the full set of Java filesystem operations. Rather than delegating the
management of files and directories to the host environment and only encrypting
the contents of each file, the Conclave persistent filesystem actually provides
its own implementation of a FAT based filesystem, converting all file operations
into the equivalent of disk sector based reads and writes.

The diagram shows how this is implemented. 

![](images/file-persistence.png)

The enclave code uses standard Java file operations to interact with the
filesystem. The JDK converts these into what would normally be calls to the host
operating system via a Java native interface. Conclave intercepts these calls by
providing a POSIX emulation layer which implements the calls by passing them to
a FAT filesystem library. The filesystem library converts the file operations
into sector reads and writes inside the enclave.

The enclave itself cannot persist these sector level operations as it has no
access to a physical disk. So instead it needs to delegate this to the host.
When delegating to the host, the enclave needs to protect the confidentiality
and integrity of the sector data. To do this it encrypts each sector using
AES-GCM with a key derived from either the Key Derivation Service, or with the
root sealing key of the CPU based on the configuration provided when the enclave
was built.

Once the data has been encrypted, the order of the sectors is randomised by the
enclave using the encryption key as a seed. This provides a level of protection
against the host where an observer can gather information about the operation of
the enclave by monitoring sector reads and writes. Without randomisation,
writing a large file inside the enclave would result in a number of sequential
sector writes on the host, giving the host an idea of the size of the file and
the amount of data being written. With randomisation, the host will still see
the same number of sector writes from the enclave, but will not be able to
determine that the writes all relate to the same file.

A further level of protection will be added in a later version of Conclave to
support oblivious reads and writes to further mask access patterns of sectors
from the host. This will introduce random sector read and write operations from
the enclave to the host, effectively masking the real reads and writes from
host.

The host receives requests to read and write encrypted sectors via an outgoing
call from the enclave. The host then maintains a single persistent file to
handle the requests from the enclave. The host simply reads from or writes to
the file at an offset based on the sector number requested by the enclave.
