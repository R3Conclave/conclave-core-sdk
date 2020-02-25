# First enclave

!!! tip

    This tutorial assumes you've read and understood the [conceptual overview](enclaves.md).

We'll go through these steps to make an enclave:

1. Configure Gradle. At this time Conclave projects must use Gradle as their build system.
2. Create a new subclass of [`Enclave`](api/com/r3/conclave/enclave/Enclave.html)
3. Implement the `EnclaveCall` interface for local communication.
4. **(Temporary)** Supply XML configurations
5. Write a host program.
6. Run the host and enclave in simulation mode.
7. Run the program in debug mode.

<!---
TODO: Complete this tutorial:

      Signing/running in release mode.
      Remote attestation.
      Java versions of the code (mkdocs-material supports code tabs out of the box)
      Update as API is completed.
-->

!!! note

    At this time Conclave development must be done on Linux.

## Configure Gradle

Create a new Gradle project via whatever mechanism you prefer, e.g. IntelliJ can do this via the New Project wizard.
Create two modules defined in ther project: one for the host and one for the enclave. The host program may be an
existing server program of some kind, e.g. a web server, but in this tutorial we'll write a dedicated host.

You will need to add plugins and libraries to your project, using artifacts from the Conclave SDK. 

!!! note 
    If you don't have a Conclave SDK, please contact R3 and request a trial.
    
In the unzipped SDK there is a directory called `repo` that contains a local Maven repository. Add it to your Gradle
modules with a snippet of code like this at the top level `build.gradle` file:

```groovy
repositories {
    maven {
        url = "path/to/the/sdk/repo"
    }
}
```

You may wish to extract this out into a property each developer can set locally. It can be done like this:

```groovy 
repositories {
    maven {
        url = findProperty('conclave.repo') ?: throw Exception("You must set the property conclave.path in your gradle.properties file.") 
    }
}
```

Now a developer can add to [the `gradle.properties` file](https://docs.gradle.org/current/userguide/organizing_gradle_projects.html#declare_properties_in_gradle_properties_file)
a line like this:

```
conclave.repo=/path/to/sdk/repo
```

<!--- TODO: Check these instructions actually work -->

### Configure the host build

In the host module add a dependency on the Conclave host library:

```groovy hl_lines="2"
dependencies {
    implementation "com.r3.conclave:conclave-host"
}
```

SGX enclaves can be be built in one of three modes: simulation, debug and release. Simulation mode doesn't require any
SGX capable hardware. Debug executes the enclave as normal but allows the host process to snoop on and modify the
protected address space, so provides no protection. Release locks out the host and provides the standard SGX
security model, but (at this time) requires the enclave to be signed with a key whitelisted by Intel. See ["Deployment"](deployment.md)
for more information on this.

Add this bit of code to your Gradle file to let the mode be chosen from the command line:

```groovy
// Override the default (simulation) with -PenclaveMode=
def mode = findProperty("enclaveMode")?.toString()?.toLowerCase() ?: "simulation"
```

and then use `mode` in another dependency, this time, on the enclave module:

```groovy hl_lines="2"
dependencies {
    runtimeOnly project(path: ":my-enclave", configuration: mode)
}
```

This says that at runtime (but not compile time) the enclave must be on the classpath, and configures dependencies to
respect the three different variants of the enclave.

<!--- TODO: fat jar? -->

### Configure the enclave build

Add the Conclave Gradle plugin:

```groovy hl_lines="2"
plugins {
    id 'com.r3.sgx.enclave'
}
```

and a dependency on the Conclave enclave library:

```groovy hl_lines="2"
dependencies {
    implementation "com.r3.conclave:conclave-enclave"
}
```

Enclaves are similar to standalone programs and as such have an equivalent to a "main class". This class must be a
subclass of [`Enclave`](/api/com/r3/conclave/enclave/Enclave.html) and we'll write it in a moment. The name of the
class must be specified in the JAR manifest like this, so Conclave can find it:

```groovy
jar {
    manifest {
        attributes("Enclave-Class": "com.superfirm.enclave.MyEnclave")    // CHANGE THIS NAME!
    }
}
```

## Create a new subclass of `Enclave`

Create your enclave class:

```kotlin
package com.superfirm.enclave

class MyEnclave : EnclaveCall, Enclave() {
    override fun invoke(bytes: ByteArray): ByteArray? {
        return bytes.reversedArray()
    }
}
```

The `Enclave` class by itself doesn't support direct communication with the host. This is because sometimes you
don't need that and shouldn't have to implement message handlers. In this case we'll use that functionality because
it's a good place to start learning, so we also implement the `EnclaveCall` interface. There's one method we must
supply: `invoke` which takes a byte array and optionally returns a byte array back. Here we just reverse the contents.

!!! tip
    In a real app you would use the byte array to hold serialised data structures. You can use whatever data formats you
    like. You could use a simple string format or a binary format like protocol buffers.

## Supply XML configurations

!!! note
    This step is temporary. Future versions of the Conclave Gradle plugin will automate it for you.

Enclaves must be configured with some environmental information. This is done via an XML file, one for each type of
release.

Create directories called:

* `src/sgx/Simulation`
* `src/sgx/Debug`
* `src/sgx/Release`

where `src` is the same directory your `src/main/java` directory is in.

!!! warning

    The uppercase starting letter of this directory matters.

Inside the `Simulation` directory create a file called `enclave.xml` and copy/paste this content:

```xml
<EnclaveConfiguration>
    <ProdID>0</ProdID>
    <ISVSVN>0</ISVSVN>
    <StackMaxSize>0x280000</StackMaxSize>
    <HeapMaxSize>0x8000000</HeapMaxSize>
    <TCSNum>10</TCSNum>
    <TCSPolicy>1</TCSPolicy>
    <DisableDebug>0</DisableDebug>
    <MiscSelect>0</MiscSelect>
    <MiscMask>0xFFFFFFFF</MiscMask>
</EnclaveConfiguration>
```

This switches control resource usage and identity of the enclave. For now we can accept the defaults.

<!--- TODO: We should force developers to specify the ISV SVN and ProdID in future -->

## Write a host program

An enclave by itself is just a library. It can't (at this time) be invoked directly. Instead you load it from inside
a host program.

It's easy to load then pass data to and from an enclave:

```kotlin
fun main() {
    val host: EnclaveHost = EnclaveHost.loadFromResources("com.superfirm.enclave.MyEnclave", EnclaveMode.SIMULATION)
    host.start()
    host.use {
        val answer: ByteArray = host.callEnclave("Hello World!".toBytes())
        println(String(answer))
    }
}
```

This code starts by creating an [`EnclaveHost`](api/com/r3/conclave/host/EnclaveHost.html) object. It names the class
to load and then calls `start`, which actually loads and initialises the enclave and the `MyEnclave` class inside it.
Note that an `EnclaveHost` allocates memory out of a pool called the "enclave page cache" which is a machine-wide limited
resource. It'll be freed if the host JVM quits, but it's good practice to close the `EnclaveHost` object by calling
`close` on it when done.

Starting and stopping an enclave is not free, so **don't** load the enclave, use it and immediately close it again
as in the above example. Treat the enclave like any other expensive resource and keep it around for as long as you
might need it.

Once we started the enclave, we call it, passing in a string as bytes. The enclave will reverse it and we'll print out
the answer.

!!! tip
    You can have multiple `EnclaveHost` objects in the same host JVM but they must all use same mode.

## Run the host and enclave in simulation mode

We can apply the Gradle `application` plugin and set the `mainClassName` property
[in the usual manner](https://docs.gradle.org/current/userguide/application_plugin.html#application_plugin) to let us run
the host from the command line.

Now run:

`gradle host:run`

and it should print "Hello World!" backwards.

During the build you should see output like this:

```
> Task :enclave:generateEnclaveletMetadataSimulation
Succeed.
Enclave measurement: 89cec147162cf2174d3404a2d8b3814eb7c6f818f84ee1ab202ae4e4381f4b49
```

This hex value is called the *measurement*, and is a hash of the code of the enclave. It includes both all the Java
code inside the enclave as a fat-JAR, and all the support and JVM runtime code required. As such it will change any
time you alter the code of your enclave, the version of Conclave in use or the mode (sim/debug/release) of the enclave.

The measurement is reported in an `EnclaveInstanceInfo` remote attestation structure (see [enclaves](enclaves.md) for
a discussion of remote attestation). Everyone should be able to get the exact same value when doing the build, so in
this way your users can audit the contents of a remote enclave over the network.
