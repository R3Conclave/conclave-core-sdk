# Security

## Memento attacks

Your enclave has protected RAM which it uses to store normal, in-memory data. Reads are guaranteed to observe prior writes.

But an enclave is just an ordinary piece of code running in an ordinary program. That means it cannot directly access
hardware. Like any other program it has to ask the kernel to handle requests on its behalf, because the kernel is the
only program running in the CPU's supervisor mode where hardware can be controlled ("ring 0" on Intel architecture chips).
In fact, inside an enclave there is no direct access to the operating system at all. The only thing you can do is
exchange messages with the host code. The host may choose to relay your requests through the kernel to the hardware
honestly, or any of those components (host software, kernel, the hardware itself) may have been modified maliciously.
The enclave protections are a feature of the *CPU*, not the entire computer, and the CPU maintains state only until
it is reset or the power is lost.

You can think of this another way. If the enclave stores some data to disk, nothing stops the owner of the computer
stopping the enclave host process and then editing the files on disk.

Because enclaves can generate encryption keys private to themselves, encryption and authentication can be used to stop
the host editing the data. Data encrypted in this way is called **sealed data**. Sealed data can be re-requested from
the operating system and decrypted inside the enclave.


Conclave handles the sealing process for you. Unfortunately there's one class of attack encryption cannot stop. It
must instead be considered in the design of your app. That attack is when the host gives you back older data than
was requested. The system clock is controlled by the owner of the computer and so can't be relied on. Additionally
the owner can kill and restart the host process whenever they like.

Together this means an enclave's sense of time and ordering of events can be tampered with to create confusion. By
snapshotting the stored (sealed, encrypted) data an enclave has provided after each network message from a client is
delivered, the enclave can be "rewound" to any point. Then stored messages from the clients can be replayed back to
the enclave in different orders, or with some messages dropped. We call this a Memento attack, after [the film in which
the protagonist has anterograde amenesia](https://en.wikipedia.org/wiki/Memento_(film)).

!!! warning

    A full discussion of Memento attacks is beyond the scope of this document. The Conclave project strives to provide
    you with high level protocol constructs that take them into account, but when writing your own protocols you should
    consider carefully what happens when messages can be re-ordered and re-tried by the host.

## Side channel attacks

TODO

## Secure time access

TODO