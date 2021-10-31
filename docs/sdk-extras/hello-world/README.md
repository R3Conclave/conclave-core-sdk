## Conclave Sample

This is a simple app using the Conclave API. It is licensed under the Apache 2 license, and therefore you may 
copy/paste it to act as the basis of your own commercial or open source apps.

# How to run

Start the host on a Linux system, which will build the enclave and host:

```
./gradlew host:run --args="--sealed.state.file=/tmp/hello-world-sealed-state"
```

It should print out some info about the started enclave. Then you can use the client to send it strings to reverse:

```
./gradlew client:run --args="reverse me!"
```

### Note on conclave modes
By default, this sample will build and run in [mock mode](https://docs.conclave.net/mockmode.html), and so won't use a
secure enclave. For a list of modes and their properties, see [here](https://docs.conclave.net/tutorial.html#enclave-modes).
For instructions on how to set the mode at build time, see [here](https://docs.conclave.net/tutorial.html#selecting-your-mode).
