# CorDapp Sample - Java

This is a simple CorDapp using the Conclave API. It is licensed under the Apache 2 license, and therefore you 
may copy/paste it to act as the basis of your own commercial or open source apps.

## Usage

Unlike most CorDapps this one will *only run on Linux* due to the need for an enclave. But don't fear: you can use 
virtualisation to run it on Windows and macOS as well. For Windows it will unfortunately require some manual work. 
On macOS you can install Docker Desktop and then use the handy `container-gradle` script found in the SDK scripts 
directory, which does everything for you.

### Linux

1. Download an Oracle Java 8 and unpack it somewhere.
2. Run `./gradlew workflows:test`

### MacOS

1. Download an Oracle Java 8 **for Linux** ("Linux x64 Compressed Archive") and unpack it somewhere **in your home directory**. 
   It must be under $HOME as otherwise Docker will complain. For example, use `~/jdk8`.
2. Run `env LINUX_JAVA_HOME=$HOME/jdk8 ../scripts/container-gradle workflows:test`

If you make any mistakes during this process you may need to use the `docker ps -a` and `docker rm` commands to delete
the container the script creates.

### Windows

We don't have tested instructions for that at this point but you can try using WSL2 to set up an Ubuntu environment
and then follow the Linux instructions. It should work! Of course, only in simulation mode.

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
