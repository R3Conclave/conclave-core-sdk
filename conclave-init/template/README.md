# Template Enclave
A template project to use as a starting point for Conclave applications.

Useful commands:
Build the host:
```bash
./gradlew bootJar
```

Build the client:
```bash
./gradlew shadowJar
```

Run the host:
```bash
java -jar host/build/libs/host-mock.jar
```

Run the client:
```bash
java -jar client/build/libs/client-all.jar \
    "S:0000000000000000000000000000000000000000000000000000000000000000 \
    PROD:1 SEC:INSECURE"
```

For instructions on building and running the project, see https://docs.conclave.net/conclave-init.html.

