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

Then add your dependencies, in this case we are using junit for testing. You don't need to include conclave libraries
here as the enclave plugin will include them for you automatically.

```groovy
dependencies {
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
    productID = 1                       // Mandatory
    revocationLevel = 0                 // Mandatory
    maxHeapSize = "256m"
    maxStackSize = "2m"
    inMemoryFileSystemSize = "64m"
    persistentFileSystemSize = "0m"
    enablePersistentMap = false
    maxPersistentMapSize = "16m"
    maxThreads = 10
    supportLanguages = ""
    reflectionConfigurationFiles.from("config.json")
    serializationConfigurationFiles.from("serialization.json")

    kds {
    }

    simulation {
    }
    debug {
    }
    release {
    }
}
```

Each option is described below:

### productID
_Default: None. You must provide a value_ 

The product ID is an arbitrary number that can be used to distinguish between different enclaves produced by the same
organisation (which may for internal reasons wish to use a single signing key). This value should not change once you have 
picked it.

### revocationLevel
_Default: None. You must provide a value_ 

The revocation level should be incremented whenever a weakness or vulnerability in the enclave code is discovered
and fixed. Doing this will enable clients to avoid connecting to old, compromised enclaves. The client can set an
[`EnclaveConstraint`](api/-conclave%20-core/com.r3.conclave.common/-enclave-constraint/index.html) that specifies the required minimum 
revocation level when loading an enclave.

The revocation level in an enclave affects the keys that are generated for 'sealing' data in an enclave. 
Because enclaves can generate encryption keys private to themselves, encryption and authentication can be used to stop
the host editing the data. Data encrypted in this way is called **sealed data**. Sealed data can be re-requested from
the operating system and decrypted inside the enclave. 

Whenever the revocation level is raised for an enclave, the data that is sealed by the new version cannot be unsealed
and read by enclaves with a lower revocation level. This is not true in the opposite direction though: enclaves can 
unseal data that was encrypted by an enclave with a lower revocation level.

This directly affects enclaves that are using [mail as storage](mail.md#using_mail_for_storage). When a new enclave
is deployed with a higher revocation level, and the host contains persisted data sealed with a previous version of
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
decide the heap size. However, the option is there in case you need to increase beyond the default maximum, or if you
want to configure your application to use less memory to co-exist with other applications.

The `maxHeapSize` setting provides the same control over heap memory as the JVM provides, but for the runtime 
environment in the enclave.

Why is there a separate heap for enclaves? This requires a bit of explanation:

In order to keep your data private, all data allocated inside an enclave is encrypted. This encryption is implemented
using SGX hardware in a block of physical memory that Intel have named the "Encrypted Page Cache" or "EPC". Whenever
you create an object or store some data in memory inside an enclave it is stored in this EPC memory. This is why
the enclave runtime manages its own heap - to ensure all data stays with the EPC memory.

So, what should the heap size be set to? This depends. In most cases you can leave this setting at its default value
of 256Mb. For many enclaves this will be enough. However, your enclave may want access to a very large set of data.
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
    The size is specified in bytes, but you can put a `k`, `m` or `g` after the value to specify it in kilobytes, 
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
Stack can be consumed in different ways, but the default value provided for `maxStackSize` ensures you will
likely never see an exhausted stack unless you accidentally generate an infinite recursion via a function calling
itself.

When the host calls into the enclave and the context is switched from the host JVM into the enclave runtime, the
host stack cannot be used as it could potentially leak secret information on return. Instead, an in-enclave
stack is used. When a value is provided for `maxStackSize` the in-enclave stack is set to this size for each
thread that runs inside the enclave.

!!! tip
    As with `maxHeapSize`, the size is specified in bytes but you can put a `k`, `m` or `g` after the value to 
    specify it in kilobytes, megabytes or gigabytes respectively.

### inMemoryFileSystemSize
_Default:_ `64m`

This is a setting to specify the maximum size of the in-memory filesystem. This value needs to be smaller than
`maxHeapSize` above, as the memory used by the filesystem is taken from the Enclave's heap.
If you specify a value of 0, the in-memory filesystem will be disabled.

You can use the in-memory filesystem as a "scratch pad" in the Enclave memory;
your files will be lost if the enclave restarts.

When enabled together with the persisted filesystem (`persistentFileSystemSize` below), the directory `/tmp` will be reserved and
it will represent the mount point of the in-memory filesystem. Everything that you write there
will be lost if the enclave restarts.

When enabled alone, the mount point will be `/` and all files and directories will be considered
part of the in-memory filesystem. 

!!! warning
    The lower limit for the in-memory filesystem's size is 97792 bytes and the upper limit is the value of the `maxHeapSize`.

!!! tip
    As with `maxHeapSize` and `maxStackSize`, the size is specified in bytes but you can put a `k`, `m` or `g`
    after the value to specify it in kilobytes, megabytes or gigabytes respectively.

### persistentFileSystemSize
_Default:_ `0m`

This is a setting to specify the maximum size of the persisted filesystem.
If you specify a value of 0, the persisted filesystem will be disabled.

You can use the persisted filesystem to read/write files that needs to be available
after a restart of your enclaves. This is represented as a single encrypted file on the host;
your enclave will load/save/read/write all files and directories into that file.

The enclave host must provide a file where the encrypted file system will be persisted. If using the
[web host](conclave-web-host.md) then the `--filesystem.file` flag must be specified. If using a custom host then it 
must be passed into [`EnclaveHost.start`](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/start.html).

!!! note
    Once created with a specific enclave mode, the filesystem cannot be used by the same enclave built with a different enclave mode.

When this code gets executed for the first time, Conclave will create and encrypt this file using either a key derived from the `MRSIGNER` (default) or a key generated by a [Key Derivation Service](kds-configuration.md), if it is configured. When using the key derived from the `MRSIGNER`, the file can only be decrypted by the enclave running on the same physical machine. If using a key generated by a Key Derivation Service then the file is not restricted and can migrate to other machines.

Note that whilst the persisted filesystem is encrypted and the host can't read it, **this is not transactional when used alone**:
there would be no protection if the host performed rewind attacks by replacing the file with an older copy.

In order to **prevent such rewind attacks**, you need to use [the `persistentMap` key-value store](persistence.md)
in addition to the persisted filesystem.

The persisted filesystem is also currently susceptible to **side channel attacks** if the host observes the frequency of
reads/writes from/to the file; we plan to add an oblivious mechanism in a future release to cope with this.

!!! warning
    This value cannot change once the enclave has started the first time, please choose an appropriate value
    that is big enough for your needs in the long term.  
    The lower limit for the persisted filesystem's size is 97792 bytes and the upper limit is ~2TB (2,199,023,255,040 bytes). Note that, because of the encryption
    mechanisms, using very high values will cause a long initialization step.

!!! tip
    As with `maxHeapSize` and `maxStackSize`, the size is specified in bytes but you can put a `k`, `m` or `g`
    after the value to specify it in kilobytes, megabytes or gigabytes respectively.      

### enablePersistentMap & maxPersistentMapSize
_Defaults:_ `false` and `16m` respectively.

The persistent map is a persistent, encrypted key-value store that his hardened against rewind attacks. Enabling the
persistent map has potential performance implications, which is why it is disabled by default. For more information
regarding the persistent map, see [here](persistence.md).

### supportLanguages
_Default:_ `""`

A comma separated list of languages to support using the polyglot context capability provided by GraalVM.

This allows for code in the supported languages to be parsed and invoked by the enclave giving the ability 
to deploy dynamic code, or to develop part of your enclave logic in a different language.

The current version of conclave supports JavaScript and Python. The values for this setting can either be the
default empty string, `"js"`, or `"python"` indicating the enclave should provide support for JavaScript or Python 
polyglot contexts, respectively.

See [this page on running JavaScript/Python in your enclave](javascript-python.md) for details on how to use this setting.

### kds

This section contains the KDS (key derivation service) configuration settings. See
[the page on KDS configuration](kds-configuration.md) for information regarding these settings.

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

The path should be absolute or relative to the root of the enclave module.

!!!warning
    Due to a [known issue](known-issues.md), reflection configuration files must be specified using relative paths when
    building on Windows and macOS platforms. Additionally, on Windows, paths must use forwardslashes rather than the
    usual backslashes.

### serializationConfigurationFiles
_Default:_ empty list

A list of serialization configuration files as specified in Graal's
[Serialization](https://github.com/oracle/graal/blob/vm-21.2.0/substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/configure/doc-files/SerializationConfigurationFilesHelp.txt)
documentation file.

This allows to specify classes which are expected to be serialized using Java serialization.

The path should be absolute or relative to the root of the enclave module.

!!!warning
    Due to a [known issue](known-issues.md), serialization configuration files must be specified using relative paths
    when building on Windows and macOS platforms. Additionally, on Windows, paths must use forwardslashes rather than
    the usual backslashes.

## Assisted configuration of Native Image builds

You can generate the reflection and serialization configuration files by using the
[native-image-agent](https://www.graalvm.org/22.0/reference-manual/native-image/Agent/#assisted-configuration-of-native-image-builds)
and by running your project in mock mode. The agent will track the use of dynamic features and generate the 
configuration files.

To make sure that you include all the essential classes and resources in the configuration files, you should execute 
all the execution paths of the enclave code. You can do that by running extensive tests in mock mode. If you are not 
running the agent with tests, you can run the host as an executable JAR and trigger as much enclave logic as 
possible by sending requests from the host and the client. To create this executable Jar, you can use the [Shadow 
Gradle plugin](https://plugins.gradle.org/plugin/com.github.johnrengelman.shadow).

After generating the files, you can place the configuration files in `enclave/src/main/resources/META-INF/native-image`.
Native Image uses these files when you build the enclave in `simulation`, `debug`, or `release` mode.

Please note that running the host through Gradle and/or JUnit tests while the agent is enabled will likely cause 
Gradle, JUnit, or host's classes to be present in the configuration files.
To avoid this and ensure that only enclave-related classes and resources are included, you can configure
[filters](https://www.graalvm.org/22.0/reference-manual/native-image/Agent/#agent-advanced-usage).

You might need to adjust the generated configuration files and the filters a few times and edit them throughout the 
development process.

### Generate configuration files using an executable JAR

1. Create a `filter.json` file with the following code:

   ```json
   {
       "rules": [
           {"excludeClasses": "nonapi.**"},
           {"excludeClasses": "com.r3.conclave.host.**"}
       ]
   } 
   ```
2. Place the `filter.json` file in the following directory: 
   `path/to/enclave/src/main/resources/META-INF/native-image/` 
   
3. Download [GraalVM](https://github.com/graalvm/graalvm-ce-builds/releases/tag/vm-22.0.0.2) for your operating system 
   and [install](https://www.graalvm.org/22.0/docs/getting-started/) it.

4. Install the `native image`:
    ```bash
    $JAVA_HOME/bin/gu install native-image
    ```

5. Add the [Shadow Gradle plugin](https://plugins.gradle.org/plugin/com.github.johnrengelman.shadow) to the 
   `plugins` section of the `host`'s `build.gradle` file:

    ```groovy
    plugins {
        id 'com.github.johnrengelman.shadow' version '6.1.0'
    }
    ```

6. Add the `EnclaveWebHost` main class to the `host`'s `build.gradle` file, *after* the plugins section:
    
    ```groovy
    project.mainClassName = "com.r3.conclave.host.web.EnclaveWebHost" 
    ```
   
7. Generate the shadow jar:
    
    ```bash
    ./gradlew -PenclaveMode=mock host:shadowJar
    ```
    This command creates an executable *shadow* JAR which contains all the dependencies of the host and the enclave. You 
    can find the shadow jar in the default location `host/build/libs/host-all.jar`.

8. Run the host with the agent enabled to generate the configuration files:

    ```bash
    $JAVA_HOME/bin/java -agentlib:native-image-agent=config-output-dir=/path/to/enclave/src/main/resources/META-INF/native-image/,caller-filter-file=/path/to/enclave/src/main/resources/META-INF/native-image/filter.json -jar /path/to/host/build/libs/host-all.jar
    ```

9. Trigger the execution of the `enclave` logic by sending a `client` request.
    ```bash
    ./gradlew client:run
    ```
Now you should have generated your configuration files in ```/path/to/enclave/src/main/resources/META-INF/native-image```. 
Native Image will pick up these files when you build the enclave in simulation, debug, or release mode.