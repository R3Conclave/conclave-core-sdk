# API changes

## 1.0 to 1.1

The `MockHost` class for loading an enclave in mock mode has been removed. You will need to update your
code to use `EnclaveHost.load` instead of `MockHost.loadMock` as below:

```java
EnclaveHost enclave = EnclaveHost.load(className)
```

You also need to replace any test dependency on `conclave-testing` with `conclave-host` in your gradle
projects. For example, in the enclave `build.gradle` ensure you have `conclave-host` as a test dependency
and remove the dependency on `conclave-testing` as below:

```groovy hl_lines="3"
dependencies {
    implementation "com.r3.conclave:conclave-enclave"
    testImplementation "com.r3.conclave:conclave-host"
    testImplementation "org.junit.jupiter:junit-jupiter:5.6.0"
}
```

[Learn more about the changes to mock mode](writing-hello-world.md#testing)

## Beta 4 to 1.0
Between beta 4 and 1.0 the API for creating mail has changed. `MutableMail` has been replaced by `PostOffice` which is a
factory for creating encrypted mail. There's no longer any need to manually increment the sequence number as that's done
for you. Instead make sure to only have one instance per sender key and topic. This allows the enclave to check for
dropped or reordered mail. `Mail.decrypt` and `EnclaveInstanceInfo.decryptMail` have been replaced by `PostOffice.decryptMail`.
Decrypt any response mail using the same post office instance that created the request.

Inside the enclave `Enclave.createMail` has been replaced by `Enclave.postOffice` which returns a cached post office for
the destination and topic. This means you don't need to manage post office instances inside the enclave as you do in the
client.

The routing hint parameter in `Enclave.receiveMail` has been moved to the end to make the method signature consistent
with `EnclaveHost.deliverMail`.
