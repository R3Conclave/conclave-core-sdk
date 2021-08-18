# API changes

## 1.1 to 1.2

To facilitate future plans for enclave to enclave communication, the following classes have been moved from `
com.r3.conclave.client` to `com.r3.conclave.common`:

* `EnclaveConstraint`
* `InvalidEnclaveException`

Client code should be updated appropriately.

## 1.0 to 1.1

There have been a number of changes in the way that you use mock mode in your Conclave projects. You will
need to make some changes to your build files in order to build you earlier projects with Conclave 1.1.

Firstly, the `MockHost` class for loading an enclave in mock mode has been removed. You will need to update your 
code to use `EnclaveHost.load` instead of `MockHost.loadMock`. So the pre-1.1 code below:

```java
if (mode == "mock") {
    enclave = MockHost.loadMock(className);
}
else {
    enclave = EnclaveHost.load(className);
}
```

Changes to:

```java
enclave = EnclaveHost.load(className);
```

Secondly, the `conclave-testing` package has been removed. This previously contained `MockHost` but this is no
longer required. You need to remove any test dependency on `conclave-testing` from your `build.gradle`
files and remove any `import` statements that refer to `conclave-testing`.

Lastly, you must make sure that your host project (the one that loads the enclave) does not include the
enclave class on its classpath in anything other than mock mode. You can ensure this is the case by 
setting a `runtimeOnly` dependency on the enclave project in your host `build.gradle`.

```groovy
runtimeOnly project(path: ":enclave", configuration: mode)
```

If you need to access the enclave class in your host project in mock mode via the the `EnclaveHost.mockEnclave`
property then you will need to conditionally depend on the enclave project at compile or implementation time by
including this in your host `build.gradle`:

```groovy
if (mode == "mock") {
    implementation project(path: ":enclave", configuration: "mock")
} else {
    runtimeOnly project(path: ":enclave", configuration: mode)
}
```

[Learn more about the changes to mock mode](mockmode.md).

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
