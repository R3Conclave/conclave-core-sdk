# Breaking changes in Conclave 1.1

If you are upgrading your project from a previous version of Conclave then you may have to make some
changes in order to get your project to build with Conclave 1.1.

## Mock mode changes

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

