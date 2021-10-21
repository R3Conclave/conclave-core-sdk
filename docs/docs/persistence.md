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
(as it commonly happens on Linux operating system).  
All the other locations will be used to persist your files.

* in case you choose to have **either** the in-memory or the persisted filesystem, the filesystem
of your choice will be mounted to the `/` directory and you can write your files and directory wherever you want.

### In-memory filesystem
The goal of the in-memory filesystem support is to allow libraries and applications to have a simple
'scratch space' for files to enable you to use file APIs to prepare data before it is encrypted for storage.

The in-memory filesystem does not allow persistence, and it comes as empty every time the enclave restarts.

### Persistent encrypted filesystem
When you need to persist files to make them available after your enclave restarts, you can use the persisted
filesystem.

This will not map your files into the host directly, but it will create a single encrypted file in the host filesystem,
which will contain a representation of your filesystem in an encrypted format.
The enclave will use this "filesystem file" by encrypting/decrypting the stream of bytes with encryption keys derived from `MRSIGNER`.

In order to **prevent rewind attacks**, the persisted filesystem needs to be combined with the [`persistentMap` key-value store](persistence.md).

The persisted filesystem is also currently susceptible to **side channel attacks** if the host observes the frequency of reads/writes from/to the file;
we plan to add an oblivious mechanism in a future release to cope with this.




