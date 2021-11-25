# Conclave Hello World

This is a simple app using the Conclave API. It is licensed under the Apache 2 license, and therefore you may 
copy/paste it to act as the basis of your own commercial or open source apps.

## How to run

Start the host on a Linux system, which will build the enclave and host:

```bash
./gradlew host:bootJar
java -jar host/build/libs/host-mock.jar
```

It should print out some info about the started enclave. Then you can use the client to send it strings to reverse:

```bash
./gradlew client:shadowJar
java -jar client/build/libs/client.jar "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE" "reverse me"
```

## Next step

To understand what the first string parameter to the client is or why the host jar has "mock" in the file name then 
head over to our [online docsite](https://docs.conclave.net/running-hello-world.html). A local copy is also 
available in the SDK zip.
