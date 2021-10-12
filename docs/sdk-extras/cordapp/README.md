# CorDapp Sample - Java

This is a simple CorDapp using the Conclave API. It is licensed under the Apache 2 license, and therefore you 
may copy/paste it to act as the basis of your own commercial or open source apps.

## Usage

Unlike most CorDapps this one will *only run on Linux* due to the need for an enclave. But don't fear: you can use 
virtualisation to run it on Windows and macOS as well. For Windows and MacOS, this will unfortunately require you to
do some manual work to set up a docker container (instructions below).

### Linux

1.  Download a Java 8 jdk and unpack it somewhere.
2.  Run `./gradlew workflows:test`

### MacOS

1.  Download a (Linux!) Java 8 jdk and unpack it somewhere.
2.  Download and install docker desktop if it isn't installed already. Ensure that it is running.
3.  Create and enter a linux execution environment:
    ```
    ./gradlew enclave:setupLinuxExecutionEnvironment
    docker run -it --rm -v ${HOME}:/home/${USER} -w /home/${USER} conclave-build /bin/bash
    ```
    This will mount your home directory and give you a linux shell.
4.  Change directory to your project and run the project as you would under linux:
    ```
    cd <project-directory>
    ./gradlew workflows:test
    ```

### Windows

1.  Download a (Linux!) Java 8 jdk and unpack it somewhere.
2.  Download and install docker desktop if it isn't installed already. Ensure that it is running.
3.  Create and enter a linux execution environment:
    ```
    .\gradlew.bat enclave:setupLinuxExecutionEnvironment
    docker run -it --rm -v ${HOME}:/home/${env:UserName} -w /home/${env:UserName} conclave-build /bin/bash
    ```
    This will mount your user directory and give you a linux shell.
4.  Change directory to your project and run the project as you would under linux:
    ```
    cd <project-directory>
    ./gradlew workflows:test
    ```

Alternatively, ubuntu 18.04 via wsl2 ([windows subsystem for linux 2](https://docs.microsoft.com/en-us/windows/wsl/install)
may also prove to work for you, though this has not been extensively tested.

For instructions involving docker, see [here](https://docs.conclave.net/system-requirements.md#running-conclave-projects) for information on how these commands may be adapted to your own project.

## Corda Node Identity Validation
The [certificates](/certificates) folder contains the truststore.jks Java KeyStore that contains the Corda Root certificate authority (CA) public key
that can be used for development or testing purposes. It is used to generate the trustedroot.cer that
is then embedded as a resource in the enclave, and used to validate a Corda node's identity when a
message is relayed from the host to the enclave.

To learn more about root certificates in the Corda network, please refer to [the Corda documentation](https://docs.corda.net/docs/corda-os/4.7/permissioning.html).

The public Corda Network Root Certificate can be found at https://trust.corda.network/.

### Usage
Use the shell script `dmp-cordarootca.sh` to dump the Root CA cert, then copy and paste the
output to the cordapp/enclave/src/main/resources/trustedroot.cer. *Note that this has been already
done for you, and is reported here only for documentation purpose*.

```shell
cordapp/certificates> ./dump-cordarootca.sh
-----BEGIN CERTIFICATE-----
MIICCTCCAbCgAwIBAgIIcFe0qctqSucwCgYIKoZIzj0EAwIwWDEbMBkGA1UEAwwS
Q29yZGEgTm9kZSBSb290IENBMQswCQYDVQQKDAJSMzEOMAwGA1UECwwFY29yZGEx
DzANBgNVBAcMBkxvbmRvbjELMAkGA1UEBhMCVUswHhcNMTcwNTIyMDAwMDAwWhcN
MjcwNTIwMDAwMDAwWjBYMRswGQYDVQQDDBJDb3JkYSBOb2RlIFJvb3QgQ0ExCzAJ
BgNVBAoMAlIzMQ4wDAYDVQQLDAVjb3JkYTEPMA0GA1UEBwwGTG9uZG9uMQswCQYD
VQQGEwJVSzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABGlm6LFHrVkzfuUHin36
Jrm1aUMarX/NUZXw8n8gSiJmsZPlUEplJ+f/lzZMky5EZPTtCciG34pnOP0eiMd/
JTCjZDBiMB0GA1UdDgQWBBR8rqnfuUgBKxOJC5rmRYUcORcHczALBgNVHQ8EBAMC
AYYwIwYDVR0lBBwwGgYIKwYBBQUHAwEGCCsGAQUFBwMCBgRVHSUAMA8GA1UdEwEB
/wQFMAMBAf8wCgYIKoZIzj0EAwIDRwAwRAIgDaL4SguKsNeTT7SeUkFdoCBACeG8
GqO4M1KlfimphQwCICiq00hDanT5W8bTLqE7GIGuplf/O8AABlpWrUg6uiUB
-----END CERTIFICATE-----
```

### Note on conclave modes
By default, this sample will build and run in [mock mode](https://docs.conclave.net/mockmode.html), and so won't use a
secure enclave. For a list of modes and their properties, see [here](https://docs.conclave.net/tutorial.html#enclave-modes).
For instructions on how to set the mode at build time, see [here](https://docs.conclave.net/tutorial.html#selecting-your-mode).
