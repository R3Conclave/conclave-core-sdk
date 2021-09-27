# Enclave persistence

## How to store a file on the real disk

If you have a byte stream you'd like to persist on the host, you must do this explicitly by using the mail-to-self
pattern.

Create a mail using the regular APIs as covered in the [tutorial](writing-hello-world.md) and send it with
a routing hint like `self` or any other string that your host code will recognise as meaning "this should be stored".
At startup the host can read the mails it saved on behalf of the enclave and deliver them. Your enclave can then
deserialize and save the contents to an in-memory hashmap, or if you'd rather do the decoding later just stash the bytes
in the in-memory file system. If the enclave acknowledges a mail, that tells the host to delete it.

It's up to the host how to organise the actual files on disk. By keeping this logic outside the enclave the host/developers
can change how storage works later, for instance, switching from local disk to S3 compatible blob storage, or using
a database, or splitting files over multiple types of disk, doing replication etc. The attested logic won't change and
no extra audit work will be generated.

## File system

Conclave enclaves can do file I/O, but it's not mapped to the host filesystem. Instead, all filesystem activity is
mapped to an in-memory file system. The goal of this support is to enable usage of libraries and applications that
need to load data files from disk, to provide a simple 'scratch space' for files if you find them easier to work with,
and to enable you to use file APIs to prepare data before it's encrypted for storage.

## Why not direct mapping to the host?

Some enclave platforms map file IO directly to the host file system. Conclave doesn't do this for these reasons:

1. Data given to the host must be protected in various ways. It must be encrypted, authenticated and made robust against 
   [security patches that rotate keys](renewability.md). The enclave may want to verify it's been given the most recent
   stored data by the host, to block rewind attacks. Data may need to be padded to stop the host guessing what's inside
   it by looking at the message size. In more advanced scenarios you may want to store data in such a way that any 
   enclave in a cluster can read it. All these features are provided already by the mail API, so it makes sense to
   re-use it.
2. Sophisticated side channel attacks exist when the host can observe the pattern of accesses to a data store. With
   regular file IO, software outside your control (e.g. from libraries) may do reads, writes and seeks in patterns that
   leak important parts of the data. By receiving mails at startup and decrypting them into the in-memory file system
   you avoid exposing your file IO patterns to the host by exploiting the platform's built in protections for hiding
   memory access patterns.
3. A common need for files is simply libraries that require them for configuration. In this case it makes sense to ship
   a pre-baked *unencrypted* filesystem as part of the enclave source code itself, where it can be audited and incorporated into
   the remote attestation.
4. Log files are a common output of enclaves, but it doesn't make sense to seal them to the enclave itself. That would
   just slow things down for no reason. It makes more sense to buffer logs in memory and then from time to time either 
   emit them straight to the host unencrypted, or send them somewhere else like the client that triggered the action that
   generated the logs, to a trusted administrator client, or even another enclave. Conclave's local calls and mail 
   functionality make this easy.
