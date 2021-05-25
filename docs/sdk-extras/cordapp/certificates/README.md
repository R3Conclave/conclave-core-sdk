# Corda Root CA

This folder contains the truststore.jks Java KeyStore that contains the Corda Root CA public key
that can be used for development/testing purpose. It is used to generate the trustedroot.cer that
is then embedded as a resource in the enclave, and used to validate a Corda node identity when a 
message is relayed from the host to the enclave.

To learn more about root certificates in the Corda network, please refer to the following wiki:
https://docs.corda.net/docs/corda-os/4.7/permissioning.html

Also, the reference Corda Network Root Certificate can be found here:
https://trust.corda.network/

# Usage
Use the shell script 'dmp-cordarootca.sh' to dump the Root CA cert, then copy and paste the
output to the cordapp/enclave/src/main/resources/trustedroot.cer. *Note that this has been already
done for you, and this is reported here only for documentation purpose*.

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
