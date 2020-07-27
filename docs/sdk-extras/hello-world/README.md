## Conclave Sample

This is a simple app using the Conclave API. It is licensed under the Apache 2 license, and therefore you may 
copy/paste it to act as the basis of your own commercial or open source apps.

# How to run

Start the host on a Linux system, which will build the enclave and host:

```
./gradlew host:run
```

It should print out some info about the started enclave. Then you can use the client to send it strings to reverse:

```
./gradlew client:run --args="reverse me!"
```