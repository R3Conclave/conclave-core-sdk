# Enclave signing

Enclaves must be signed. With modern SGX servers you may self-sign your enclaves, but, a signature is still
required. This section covers:
 
* *Why* signing is required
* *Who* can sign enclaves
* *How* to sign your enclaves

## Why is signing required?

Signing requirements are a part of the Intel SGX architecture. The enclave signature is used for two different purposes:

1. Linking different enclave versions together into an upgrade path, so new enclaves can decrypt data stored by or sent
   to old enclaves.
2. Authorising which enclaves can be executed on a host.

Restricting which enclaves can launch on a host ensures that datacenter providers aren't hosting server processes they 
can't examine or turn over to law enforcement. It also makes it harder for people to write malware with un-debuggable 
cores, and is part of the privacy (anti-tracking) infrastructure in SGX.

Using signatures to link binaries into a single upgrade path is the same technique used by Android and iOS to move
permissions and stored data from old to new apps.

Signing is also used to authorise which enclaves can start. By default Intel chips won't start an enclave unless it's 
signed by an Intel key, but this behaviour can be changed and on Azure (see ["Deploying to Azure"](machine-setup.md)),
enclaves can be self-signed. If you want to use older hardware, getting it whitelisted by Intel is free and can be done 
quickly. 
It's a similar process to getting an SSL certificate but using different tools.

On Xeon E CPUs with Intel FLC support in the chipset, and a recent enough kernel driver, the owner can add their 
own whitelisting authorities via BIOS/UEFI firmware settings. This means they can replace the default "must be approved
by Intel" launch control logic with their own (which may e.g. only allow their own enclaves, or may allow any self-signed
enclave).

## How to sign your enclaves

### Signing keys for simulation and debug enclaves

It is not necessary to use a whitelisted signature for enclaves built in simulation and debug modes. However, it
is still required that these enclaves are signed. Conclave supports the generation of a dummy key during the build
process that can be used for signing simulation and debug enclaves.

### Obtaining a signing key for release enclaves

Firstly, [get a commercial license](https://software.intel.com/en-us/sgx/request-license). This is a lightweight
process and doesn't cost anything or impose other requirements. Following the instructions provided on that page 
should allow you to get a signing key.

!!! tip
    It's up to you whether to store the key in an HSM.

### Signing configurations

There are three different configurations available for signing enclaves using Conclave:

1. Using a dummy key
2. Using a private key
3. Using an external process such as a signing service or HSM

Dummy keys are only useful for signing simulation and debug enclaves. A release enclave signed with a dummy key
will not be whitelisted on any platform so will not be allowed to load.

A private key can be used to sign enclaves directly within the build process. The key must be accessible on the
filesystem of the machine building the enclave.

An external process is used when the signing key is not available on the machine building the enclave, or a 
manual or air-gapped process is required to sign the enclave. In this case the Conclave project  is built in two 
steps. The first step generates the material to be signed. The second step provides the signed material to the 
Conclave build to continue and complete the build process.

### How to configure signing for your enclaves

The signing method used by your enclaves is configured in the ```build.gradle``` file for your enclave project. You can
specify different signing settings for simulation, debug and release in the same project.

The signing configuration is specified inside the relevant enclave type inside the conclave configuration. For
example:

```groovy
conclave {
    simulation {
        // Simulation signing configuration using a dummy key
        signingType = dummyKey
    }

    debug {
        // Debug signing configuration using a private key
        signingType = privateKey
        signingKey = file("../signing/sample_private_key.pem")
    }

    release {
        // Release signing configuration using an external key
        signingType = externalKey
        mrsignerSignature = file("../signing/signature.bin")
        mrsignerPublicKey = file("../signing/external_signing_public.pem")
        signatureDate = new Date(1970, 0, 1)
    }
}
```

For simulation and debug enclaves ```signingType``` defaults to ```dummyKey``` so the configuration can be
optionally omitted.

Release enclaves default to having a ```signingType``` of ```externalKey``` so the configuration parameters for the
external signing type must be specified. Alternatively the ```signingType``` can be changed to a different type. 

!!! note 
    Changing the signing type of a release enclave to ```dummyKey``` will result in an enclave that cannot be
    used as it will never be whitelisted on an SGX platform.

The configuration consists of a set of properties which depend on the signing type.

#### Dummy Key
| Property     | Description                                                                                        |
|:-------------|:---------------------------------------------------------------------------------------------------|
| signingType  | Must be set to ```dummyKey```. This is the default signing type for simulation and debug enclaves. |


#### Private Key
| Property    | Description                                                                                                                                        |
|:------------|:---------------------------------------------------------------------------------------------------------------------------------------------------|
| signingType | Must be set to ```privateKey```.                                                                                                                   |
| signingKey  | The file that contains the private key to use when signing the enclave. This can be an absolute path or relative to the enclave project directory. |


#### External Key
| Property          | Description                                                                                                                                                                                                                                                                                                                                                           |
|:------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| signingType       | Must be set to ```externalKey```. This is the default signing type for release enclaves.                                                                                                                                                                                                                                                                              |
| signatureDate     | Specifies the date to be embedded in the material generated in the first stage of the external key signing process.                                                                                                                                                                                                                                                   |
| signingMaterial   | The file that that Conclave will generate when preparing an enclave for signing in the first stage of the external key signing process. The resulting file needs to be signed by the external signing process. This can be an absolute path or relative to the enclave project directory. If this is omitted then the default filename of ```build/enclave/[simulation|debug|release]/signing_material.bin``` is used|
| mrsignerSignature | Used in the second part of the external key process to specify the file containing the signature generated by the external signing process. This can be an absolute path or relative to the enclave project directory.                                                                                                                                                |
| mrsignerPublicKey | Used in the second part of the external key process to specify the file containing the public part of the key that was used to externally sign the signing material. This can be an absolute path or relative to the enclave project directory.                                                                                                                       |

### Generating keys for signing an enclave

When using ```privateKey``` or ```externalKey``` signing types you can create your own keys
for testing or production:

#### Creating an RSA private key suitable for signing enclaves
The generated key can be used to sign enclaves using the ```privateKey``` or ```externalKey``` signing types.
When using the ```externalKey``` type you will need to generate the public key from the private key.
```
openssl genrsa -out my_private_key.pem -3 3072
```

#### Creating a password protected RSA private key suitable for signing enclaves
The generated key can only be used for the ```externalKey``` signing type as it prompts for a password during
use.
```
openssl genrsa -aes128 -out my_private_key.pem -3 3072
```

#### Obtaining the public key from a private key
The public key is required for the ```externalKey``` signing type.
```
openssl rsa -in my_private_key.pem -pubout -out my_public_key.pem
```
!!!note
	Make sure to generate the keys using ```openssl```. Other tools, such as ```ssh-keygen```, may not generate keys with properties required by Intel.

### Building a signed enclave

Signing is performed automatically during the build process when using a dummy or private key.

Additional steps are required when using an external key.

#### Add the ```prepareForSigning``` task to the host ```build.gradle```
This is an optional but recommended step to create a more consistent calling convention 
for invoking Gradle. It adds a Gradle task that can be called regardless of the build type of the enclave.

Add the following code to your _host_ ```build.gradle``` file. This adds a new Gradle task that
can be used to generate the material that will be signed by the external signing process.

```groovy
tasks.register("prepareForSigning") {
    it.dependsOn(":enclave:generateEnclaveSigningMaterial" + mode.capitalize())
}
```

The task works by using the ```mode``` Gradle property, which is set to one of ```simulation```, ```debug``` or ```release```
depending on which enclave type is currently being built, to set a dependency on the relevant Conclave task that generates
the signing material. 

The task makes the following two Gradle invocations equivalent:
```
./gradlew prepareForSigning -PenclaveMode="Release"
./gradlew :enclave:generateEnclaveSigningMaterialRelease
```

See the [tutorial](running-hello-world.md) and the `hello-world` [sample](https://github.com/R3Conclave/conclave-tutorials/blob/HEAD/hello-world/host/build.gradle)
for an example of this configuration.

#### Generate the signing material

Invoke Gradle to generate the files that need to be signed by the external signing process.

```
./gradlew prepareForSigning -PenclaveMode="Release"
```

The output of this stage is a file that contains the material to be signed in ```enclave/build/enclave/release/signing_material.bin```

#### Sign the material

Perform the required steps to manually sign the file generated in the previous step. This might require copying the file onto a different platform or onto an HSM to generate the signed file.

As an example, given a private key the following command can be used to sign the file:
```
openssl dgst -sha256 -out signature.bin -sign my_private_key.pem -keyform PEM signing_material.bin
```

Once completed, copy the signed file and the public key back onto the build system into the location specified in the ```mrsignerSignature``` and ```mrsignerPublicKey``` properties in the enclave signing configuration.

#### Complete the build

Invoke Gradle to complete the build.

```
./gradlew :host:bootJar -PenclaveMode="Release"
```
