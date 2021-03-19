# Configuration options for building enclaves

There are a number of different options that can be configured that affect the generation of your enclave when
building with the Conclave Gradle plugin.

The [tutorial](writing-hello-world.md) takes you through the configuration of an enclave project step-by-step.
This page goes into each possible option in detail and can be used as a reference when configuring your
enclave.


## Adding the Conclave Gradle plugin

The first thing to do when creating an enclave project is to add the Conclave Gradle plugin to your
`build.gradle` file:
```groovy hl_lines="2"
plugins {
    id 'com.r3.conclave.enclave'
}
```

Then add a dependency on the Conclave enclave library. The Conclave version is configured for you automatically:

```groovy hl_lines="2"
dependencies {
    implementation "com.r3.conclave:conclave-enclave"
    testImplementation "com.r3.conclave:conclave-testing"
    testImplementation "org.junit.jupiter:junit-jupiter:5.6.0"
}
```

## Conclave configuration options

The enclave's runtime environment is configured within the `conclave` section in the enclave `build.gradle`. 
The complete set of options with their default values is shown below. Items marked 'Mandatory' do not have
a default value and must be specified in your configuration. Items that have default values can be omitted
from your configuration.

```groovy
conclave {
    runtime = graalvm_native_image
    productID = 1                       // Mandatory
    revocationLevel = 0                 // Mandatory
    maxHeapSize = "256m"
    maxStackSize = "2m"
    maxThreads = 10
    supportLanguages = ""
    reflectionConfigurationFiles.from("config.json")
    serializationConfigurationFiles.from("serialization.json")

    simulation {
    }
    debug {
    }
    release {
    }
}
```

Each option is described below:

### runtime
_Default:_ `graalvm_native_image`

The runtime setting tells Conclave which runtime environment to use inside the enclave and can either be `avian` or
`graalvm_native_image`. If the setting is omitted then it defaults to `graalvm_native_image`. See
[Architecture overview](architecture.md) for details on the differences between the two supported runtime
environments. The `graalvm_native_image` value is new and has a few limitations, but runs much faster. 

Conclave needs access to a Linux build environment in order to build enclaves with the `graalvm_native_image` runtime. 
On macOS and Windows this is automatically created during the build process using Docker. If you do not have Docker
installed then the build will generate an error prompting you to switch to using either the `avian` runtime or to
install Docker on your system. Once Docker is installed and added to your `PATH` environment variable you can proceed
to build `graalvm_native_image` enclaves. Docker is not required for enclaves using the `avian` runtime.

### productID
_Default: None. You must provide a value_ 

The product ID is an arbitrary number that can be used to distinguish between different enclaves produced by the same
organisation (which may for internal reasons wish to use a single signing key). This value should not change once you have 
picked it.

### revocationLevel
_Default: None. You must provide a value_ 

The revocation level should be incremented whenever a weakness or vulnerability in the enclave code is discovered
and fixed. Doing this will enable clients to avoid connecting to old, compromised enclaves. The client can set an
[`EnclaveConstraint`](/api/com/r3/conclave/client/EnclaveConstraint.html) that specifies the required minimum 
revocation level when loading an enclave.

The revocation level in an enclave affects the keys that are generated for 'sealing' data in an enclave. 
Because enclaves can generate encryption keys private to themselves, encryption and authentication can be used to stop
the host editing the data. Data encrypted in this way is called **sealed data**. Sealed data can be re-requested from
the operating system and decrypted inside the enclave. 

Whenever the revocation level is raised for an enclave, the data that is sealed by the new version cannot be unsealed
and read by enclaves with a lower revocation level. This is not true in the opposite direction though: enclaves can 
unseal data that was encrypted by an enclave with a lower revocation level.

This directly affects enclaves that are using [mail as storage](mail.md#using_mail_for_storage). When a new enclave
is deployed with a higher revocation level and the host contains persisted data sealed with a previous version of
the enclave, the newer enclave is able to process the stored mail. If a malicious host decides to drop in an older
version of the enclave, potentially to exploit a discovered vulnerability in the enclave, then this older enclave
cannot read the data sealed using an enclave with a higher revocation level.

This behaviour allows for uninterrupted communication with clients across upgrades of the enclave. The persisted
data is automatically upgraded to the higher security level as it is consumed and resealed by the new enclave, 
incrementally phasing out the previous version of the enclave, thus recovering from a compromise.

!!! tip
    The revocation level should not be incremented on every new release, but only when security improvements have been made.

### maxHeapSize
_Default:_ `256m`

This setting defines the maximum size the heap is allowed to grow to in the runtime environment inside the enclave.

You might be familiar with the JVM option `-Xmx` which allows you to set the maximum heap size of a JVM based
application. Most of the time in a normal JVM application you can just leave this setting alone and let the JVM 
decide the heap size. However the option is there in case you need to increase beyond the default maximum, or if you
want to configure your application to use less memory to co-exist with other applications.

The `maxHeapSize` setting provides the same control over heap memory as the JVM provides, but for the runtime 
environment in the enclave.

Why is there a separate heap for enclaves? This requires a bit of explanation:

In order to keep your data private, all data allocated inside an enclave is encrypted. This encryption is implemented
using SGX hardware in a block of physical memory that Intel have named the "Encrypted Page Cache" or "EPC". Whenever
you create an object or store some data in memory inside an enclave it is stored in this EPC memory. This is why
the enclave runtime manages its own heap - to ensure all data stays with the EPC memory.

So, what should the heap size be set to? This depends. In most cases you can leave this setting at its default value
of 256Mb. For many enclaves this will be enough. However your enclave may want access to a very large set of data.
In this case you want to increase the heap size.

What happens if you want to specify a heap size that is greater than the EPC provided on your SGX system? Well, this
is not a problem as SGX allows EPC memory to be 'paged'. This means that when you want some EPC memory but none is
available, SGX will take an existing portion of memory, encrypt it inside the enclave then move it to conventional, 
non-EPC memory to make space for the new block. When the enclave needs to access the original memory, it juggles
other pages to make space in EPC to move the block back from conventional memory and decrypt it.

The downside to this 'paging' is that it has a performance impact. Therefore, if performance is important then 
it is recommended to keep your enclave memory usage as small as possible, preferably less than the size of the EPC 
on your SGX system to reduce the amount of paging that occurs.

This is something to consider when looking at sizing your SGX capable system. For example, your enclave may run 
without any problems on a system with 128Mb of EPC but it may run much faster and with less CPU load on a system 
with 256Mb or more EPC.

!!! tip
    The size is specified in bytes but you can put a `k`, `m` or `g` after the value to specify it in kilobytes, 
    megabytes or gigabytes respectively.

### maxThreads
_Default:_ `10`

This is an advanced setting that defines the maximum number of threads that can be active inside an enclave 
simultaneously. If you're interested, you can read [this detailed technical description](threads.md), otherwise you
can safely leave this at the default value. Changing this value does not affect the maximum number
of threads that you can simultaneously call into a Conclave enclave but affect the number of threads that you
can create inside the enclave.

The `maxThreads` option defines how many EPC slots are available for threads that are simultaneously
active inside the enclave. Setting a higher number for this results in a larger SGX EPC memory requirement
for the enclave even if not all the thread slots are currently in use inside the enclave.

### deadlockTimeout
_Default:_ `10`

This is an advanced setting related to [maxThreads](#maxthreads) that determines the time after which all enclave
threads have been blocked that Conclave will assume the threads are deadlocked and abort the enclave.

See [this section on deadlocks in enclave threads](threads.md#handling-deadlocks) for more information.

!!! tip
    If you are having problems with deadlocks in your enclave threads then we recommend contacting R3 support
    for help in solving the problem.

### maxStackSize
_Default:_ `2m`

This is an advanced setting that specifies the stack size that will be allocated for each thread that runs inside
the enclave. Normally you would not need to specify this setting in your configuration, the default of 2Mb being
sufficient for most applications. Only change this setting if you are seeing errors related to the stack
overflowing.

The stack is used internally by the JVM to hold information about the current function; the chain of functions
that called the current function (the 'call stack'); temporary variables and other contextual information. 
Stack can be consumed in different ways but the default value provided for `maxStackSize` ensures you will
likely never see an exhausted stack unless you accidentally generate an infinite recursion via a function calling
itself.

When the host calls into the enclave and the context is switched from the host JVM into the enclave runtime, the
host stack cannot be used as it could potentially leak secret information on return. Instead, an in-enclave
stack is used. When a value is provided for `maxStackSize` the in-enclave stack is set to this size for each
thread that runs inside the enclave.

!!! tip
    As with `maxHeapSize`, the size is specified in bytes but you can put a `k`, `m` or `g` after the value to 
    specify it in kilobytes, megabytes or gigabytes respectively.

### supportLanguages
_Default:_ `""`

A comma separated list of languages to support though the polyglot context capability provided by GraalVM that
is available when using `graalvm_native_image` enclaves.

This allows for code in the supported languages to be parsed and invoked by the enclave giving the ability 
to deploy dynamic code, or to develop part of your enclave logic in a different language.

The current version of conclave only supports JavaScript so the value for this setting can either be the
default empty string or `"js"` indicating the enclave should provide support for JavaScript polyglot contexts.

See [this page on running JavaScript in your enclave](javascript.md) for details on how to use this setting.

!!! tip
    This setting only applies to enclaves that are built using the `graalvm_native_image` runtime.

### simulation, debug and release

These sections contain the settings for signing the enclave. See [the page on signing](signing.md) for information
on these settings.

### reflectionConfigurationFiles
_Default:_ empty list

A list of reflection configuration files as specified in the
[Reflection](https://www.graalvm.org/reference-manual/native-image/Reflection/#manual-configuration) section
of Graal's reference manual.

This allows for code which rely on reflection to specify which classes, methods, fields and their properties
will be available at run time.

!!! tip
    This setting only applies to enclaves that are built using the `graalvm_native_image` runtime.

### serializationConfigurationFiles
_Default:_ empty list

A list of serialization configuration files as specified in Graal's
[Serialization](https://github.com/oracle/graal/blob/vm-21.0.0/substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/configure/doc-files/SerializationConfigurationFilesHelp.txt)
documentation file.

This allows to specify classes which are expected to be serialized using Java serialization.

!!! tip
    This setting only applies to enclaves that are built using the `graalvm_native_image` runtime.
