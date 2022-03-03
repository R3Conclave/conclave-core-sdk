# REST API - Key Derivation Service

## Introduction

This page describes how applications can communicate with the Key Derivation Service, also known as KDS. The service is made accessible
through its REST API endpoints which are presented below. As many applications, KDS also uses JSON to send and receive data.

## Endpoints
Currently, the service supports the endpoints [`/public`](#endpoint-public), and [`/private`](#endpoint-private). Samples of the requests and responses are provided for each one of them.

### Endpoint - `/public`
Returns a Curve25519 public key corresponding to a private key that was created using key material derived from the given
key specification. Access to a public key is not subject to constraints.

This allows a client to firstly attest to the
validity of the KDS to obtain trust in the KDS, then to request a public key whereby an application enclave can only get
the private key material for the same key specification if it meets the constraints defined by the client. An application
enclave that obtains the key material can then generate a Curve25519 private key corresponding to the public key, establishing
a cryptographic link between the client and the enclave.

`PUT /public`

`Content-Type: application/json`

`API-VERSION: 1`

`Request body`
```JSON
{
    "name": string,
    "masterKeyType": string,
    "policyConstraint": string
}
```

| Field | Description |
| ----- | ----------- |
| name | A name for the key. The name is used during key derivation so can be used to ensure that keys with the same master key and constraint configuration can be uniquely generated. |
| masterKeyType | The type of master key provider that is used to source the master key. |
| policyConstraint | The constraint policy to apply to the key. A key will only be released to an enclave that meets these constraints. |

`Response body`
```JSON

{
    "publicKey": string,
    "signature": string,
    "kdsAttestationReport": string
}
```

| Field | Description |
| ----- | ----------- |
| publicKey | The Base64 encoded public key. The caller should verify this public key using the signature to ensure it was returned by the KDS enclave. |
| signature | A signature in Base64 that can be used with the `kdsAttestationReport` to verify that the public key was returned by the KDS enclave. This verification is crucial for security reasons. For more details refer to [`Appendix A`](#appendix-a-signature-verification).|
| kdsAttestationReport | The `EnclaveInstanceInfo` of the KDS enclave in Base64. The application enclave should validate this report before checking the public key signature to ensure the public key was returned by the KDS enclave. |



#### Request Example

```bash
curl --location --request POST 'localhost:8090/public' \
--header 'API-version: 1' \
--header 'Content-Type: application/json' \
--data-raw '{
    "name": "MasterKeyForTesting",
    "masterKeyType": "debug",
    "policyConstraint":"S:4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4 PROD:1 SEC:INSECURE"
}'
```

#### Response Example
```JSON
{
    "publicKey": "oCBCwBelzlEOPskvmSF/Dy8uZLPRmssAyjFAXlg4a1I=",
    "signature": "8pOGWHJOPJcnE0xO7A4Gf38/b5W1ndlmjpx/nIM8zIG1TNcsn7Es1ZEcSa914SfR1O2aHuv+6LjzfP/dUP0MAA==",
    "kdsAttestationReport": "RUlJAAAALDAqMAUGAytlcAMhAOONDzHLUMilE8HfIDnIU6+3iO+x24l29LxuzVyd+RKtAAAAIHHHePxfmrr5h1brrQZQN5+3qkuk4y+LxU3pX5cEsP11AgAAAY0AAAAAYgOK2Sw4VK5IIPM3auay8gNNO3pLSKd4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAHAAAAAAAAAAcAAAAAAAAA46JQJHO6suPiR3b0zz2/68OefYXXgaKx7MdLexDi9GUAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEkkyjqcgkGjwKoaJKQHqoZAHSt5+p/4STLaeYqUIWbUAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEcd1TchJASf1ztwUwtw1fIUL4HgBw6OYCP0nXg8LIBw7KTOCDG8PefON6lPrJ3WrIbJ1WCzE2eqHC2OODODGZMB"
}
```

!!! warning
    Ensuring the message integrity and authenticity is paramount. Failing to do so is a security risk and invalidates
    the protection provided by Conclave. Verify that the message was sent by KDS by validating the
    `kdsAttestationReport` and then check the integrity of the message by validating the signature of the message.

### Endpoint - `/private`
Retrieves the private key defined by a key specification. This always returns a 32 byte array that forms the raw
key material. For example, this can be used directly as an AES256 key, or it can be used as the input to a
Curve25519 private key generation function.

The retrieval of the private key is performed by the KDS enclave. The enclave will only respond
with the key if the calling enclave meets the policy requirements defined in the key specification
provided as input parameters to this call.

Note that the key specification is not encrypted or authenticated therefore a malicious KDS host could modify
the key specification, including the constraints. However, if the key specification is modified then this
will result in a different derived key meaning that any data encrypted by a client using the correct constraint
cannot be accessed by the altered key specification.


`POST /private`

`Content-Type: application/json`

`API-VERSION: 1`

`Request body`
```JSON
{
    "appAttestationReport": string,
    "name": string,
    "masterKeyType": string,
    "policyConstraint": string
}
```

| Field | Description |
| ----- | ----------- |
| appAttestationReport | The `EnclaveInstanceInfo` in Base64 of the application enclave requesting access to the private key. This will be validated against the policy referred to by the key specification. The private key will be returned only if the policy check passes. |
| name | A name for the key. The name is used during key derivation so can be used to ensure that keys with the same master key and constraint configuration can be uniquely generated. |
| masterKeyType | The type of master key provider that is used to source the master key. |
| policyConstraint | The constraint policy to apply to the key. A key will only be released to an enclave that meets these constraints. |


`Response body`
```JSON
{
    "kdsAttestationReport": string,
    "encryptedPrivateKey": string
}
```

| Field | Description |
| ----- | ----------- |
| kdsAttestationReport | The `EnclaveInstanceInfo` in Base64 of the KDS enclave. The application enclave should validate this report before trusting the private key returned by the KDS enclave. |
| encryptedPrivateKey | A Base64 field that contains the private key packaged as a Mail object encrypted using the application enclave key. The application enclave will decrypt the Mail object to extract the private key. The envelope in the Mail contains the name, masterKeyType, and policyConstraint parameters of the KDS request. These parameters must be checked against the original. See below how to deserialize the envelope.|

#### How to Deserialize the Envelope
The envelope present in the private key mail is a byte array structured as illustrated below. The master key type field
can only contain one of the values 0 for a debug private key and 1 for a release private key. The API version field will
contain the API version used for the request (in this case, 1).
```
[API version]     [Name] [Master key type] [Policy constraint]
     |              |            |                  |
 +-------+ +------------------+ +-------+ +------------------+            
 v       v v                  v v       v v                  v     
+---------+--------+-----------+---------+--------+-----------+   
| version | length |UTF-8 bytes|  type   | length |UTF-8 bytes|    
+---------+--------+-----------+---------+--------+-----------+
  1 byte   4 bytes   variable    1 byte    4 bytes  variable
            (BE)    sized field             (BE)   sized field
```


#### Request Example

```bash
curl --location --request POST 'localhost:8090/private' \
--header 'API-VERSION: 1' \
--header 'Content-Type: application/json' \
--data-raw '{
    "appAttestationReport": "RUlJAAAALDAqMAUGAytlcAMhAINkJMp+IBZSnSWEY1ux0z/HRfpzllu1I2oT0R6zl82MAAAAIARTUJux1z0gV889af4tL3iOAM300VuZbWppAZLZ/QwzAgAAAY0AAAAAYfK2fykTkGBIIPM3auay8gNNO3pLSKd4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAHAAAAAAAAAAcAAAAAAAAAWN4G6qxjHqRGYJjDJViSVwClv6xlhw5HtR26hr1swdoAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEkkyjqcgkGjwKoaJKQHqoZAHSt5+p/4STLaeYqUIWbUAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAIczKEHJt2DTFS6SfYsgVDWPAMMgy6wnPBhVHs8tfNiZevzUaW2+1dYfm62aUeGmAOI24vKtY++UV6nkTWYWRXcB",
    "name": "MasterKeyForTesting",
    "masterKeyType": "debug",
    "policyConstraint": "S:4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4 PROD:1 SEC:INSECURE"
}'
```

#### Response Example
```JSON
{
    "kdsAttestationReport": "RUlJAAAALDAqMAUGAytlcAMhAOONDzHLUMilE8HfIDnIU6+3iO+x24l29LxuzVyd+RKtAAAAIHHHePxfmrr5h1brrQZQN5+3qkuk4y+LxU3pX5cEsP11AgAAAY0AAAAAYgOK2Sw4VK5IIPM3auay8gNNO3pLSKd4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAHAAAAAAAAAAcAAAAAAAAA46JQJHO6suPiR3b0zz2/68OefYXXgaKx7MdLexDi9GUAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEkkyjqcgkGjwKoaJKQHqoZAHSt5+p/4STLaeYqUIWbUAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEcd1TchJASf1ztwUwtw1fIUL4HgBw6OYCP0nXg8LIBw7KTOCDG8PefON6lPrJ3WrIbJ1WCzE2eqHC2OODODGZMB",
    "encryptedPrivateKey": "ALcBAAAAAAAAAAAAJDFiNTM5ZmU1LTEwM2ItNGU3Ni04MjA3LTBiMjNmNmZlZjg2YwByAAAAE01hc3RlcktleUZvclRlc3RpbmcBAAAAVlM6NDkyNENBM0E5QzgyNDFBM0MwQUExQTI0QTQwN0FBODY0MDFEMkI3OUZBOUZGODQ5MzJEQTc5OEE5NDIxNjZENCBQUk9EOjEgU0VDOklOU0VDVVJFABJIIPM3auay8gNNO3pLSKd4AAFcCteYfWjtqXiY5X59P7ZeUzAU2plPazMtWQHRDxAIb06QhAIUFdgiCpcj3+3Qhj+lAxtxY0BeWqB6LEhCcaiWlXBzRVgIPhqMCbghpIR2ozj02XlKbqoqT62VvwtJyA2HAFbXt5t8MtPvtIKnQNzahkbcrFtc83TQa9j4zUH+uLfgM69Pb1Lg10VR9V1bwfWYgZ40IrybQkMtFDd132nW8PkIzgDw1xj/uasOMpOZLbdA5xj4w0zT/AASmfjJMsJ+qye6AjAkJllBW7Ow"
}
```

!!! note

    The key specification is passed in an unencrypted, unauthenticated form from
    the application enclave to the KDS Enclave. The reason for this is that it might not
    be possible for the application enclave to know the constraints at build time (for
    example if the constraint includes a MRSIGNER measurement). Therefore, it is imperative
    that an application using the KDS is designed to ensure that the key being used by
    the enclave is verified, either by communicating with a client that is using the
    correct public key, or by using some other means of validating the key.


### Error response
All endpoints might send error responses with the following structure.

```JSON
{
  "reason": "The application enclave does not meet the required key policy"
}
```

| Field | Description |
| ----- | ----------- |
| reason | Error message. |

| Error Response | Reason |
| ------ | ------------------ |
| 400 Bad Request | The request is not formatted correctly. |
| 404 Not Found | The master key for the given configuration could not be retrieved. |

# Appendix A - Public Key Integrity Check

The integrity of the public key present in the response can be verified as follows:

1. Create a byte array as described in the diagram below. The name, master key type, and policy constraint come from the
fields in the request sent. Whereas the public key comes from the response after being decoded from Base64. The API version
field will contain the API version used for the request (in this case, 1).
```
[API version]      [Name]   [Master key type] [Policy constraint]   [Public key]
     |               |              |               |                    |
 +-------+ +------------------+ +-------+ +------------------+ +--------------------+              
 v       v v                  v v       v v                  v v                    v       
+---------+--------+-----------+---------+--------+-----------+----------+-----------+      
| version | length |UTF-8 bytes|  type   | length |UTF-8 bytes|  length  | key bytes |     
+---------+--------+-----------+---------+--------+-----------+----------+-----------+
  1 byte   4 bytes   variable    1 byte   4 bytes   variable    2 bytes    variable
            (BE)    sized field            (BE)    sized field   (BE)     sized field
```

2. Deserialize the `kdsAttestationReport` in the response by calling the method `deserialize` in `EnclaveInstanceInfo`:
```Java
eii = EnclaveInstanceInfo.deserialize(kdsAttestationReport)
```

3. Decode the Base64 signature in the response message:
```Java
signature = Base64.getDecoder().decode(base64EncodedSignature);
```

4. Create an instance of `net.i2p.crypto.eddsa.EdDSAEngine` and initialise it with `eii.dataSigningKey`:
```Java
EdDSAEngine sig = new EdDSAEngine();
sig.initVerify(eii.getDataSigningKey());
```

5. Call the method `sig.update` with the byte array created in step 1.
6. Check the public key integrity by ensuring the return value from the call `sig.verify(signature)` is true.

!!! note
    The dependency used by KDS that enables EdDSAEngine has the following Gradle coordinates: net.i2p.crypto:eddsa:0.3.0.

!!! warning

    The integrity check ensures that the public key has not been tampered with or altered inside the response. But it does not guarantee
    the authenticity of the sender, i.e., the integrity check does not guarantee that the message was sent by the KDS. To ensure the authenticity
    of the sender, the `kdsAttestationReport` field in the response must be validated.

!!! tip

    Instead of using the `/public` end point to retrieve KDS public keys to encrypt mails for the enclaves, 
    it is recommended that Java and Kotlin developers use the builder methods `fromURL` and `fromInputStream`
    of the `KDSPostOfficeBuilder` class.  
    These methods will create post offices to automatically validate the signature for you.
