# Template Enclave
A template project to use as a starting point for Conclave applications.

## How to build and run
Build the host:
```shell
./gradlew host:bootJar
```

Build the client:
```shell
./gradlew client:shadowJar
```

Run the host:
```shell
java -jar host/build/libs/host-mock.jar
```

Run the client:
```shell
java -jar client/build/libs/client-all.jar \
    "S:0000000000000000000000000000000000000000000000000000000000000000 \
    PROD:1 SEC:INSECURE"
```

For full instructions on building and running the project, see https://docs.conclave.net/conclave-init.html.

