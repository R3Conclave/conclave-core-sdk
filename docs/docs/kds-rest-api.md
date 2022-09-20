# REST API - Key Derivation Service

## Introduction

This page describes how applications can communicate with the Key Derivation Service (KDS). The KDS uses JSON to send 
and receive data. You can access the KDS through its REST API endpoints given below.

## Endpoints
The Key Derivation Service (KDS) supports two endpoints.

* [`/public`](#endpoint-public)
* [`/private`](#endpoint-private)

You can find the request & response parameters and other details for these endpoints below.

### Endpoint - `/public`
The `/public` endpoint returns a [Curve25519](https://en.wikipedia.org/wiki/Curve25519) public key.

The client requests a public key after validating the KDS.

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

| Field | Description                                                                                                              |
| ----- |--------------------------------------------------------------------------------------------------------------------------|
| name | A name for the key. The KDS uses the name field to generate unique keys with the same master key and constraint configuration. |
| masterKeyType | The type of master key provider the KDS should use to source the master key.                                          |
| policyConstraint | The constraint policy to apply to the key. The KDS will reveal the keys only to an enclave that meets these constraints. |


`Response body`
```JSON

{
    "publicKey": string,
    "signature": string,
    "kdsAttestationReport": string
}
```

| Field | Description                                                                                                                                                                                                             |
| ----- |-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| publicKey | A [Base64](https://en.wikipedia.org/wiki/Base64) encoded public key.                                                                                                                                                    |
| signature | A signature in Base64 that the caller can use with the `kdsAttestationReport` to verify that the KDS enclave returned the public key. This verification is [crucial for security reasons](#public-key-integrity-check). |
| kdsAttestationReport | The `EnclaveInstanceInfo` of the KDS enclave in Base64. The application enclave needs to validate this report before checking the public key signature to ensure that the the KDS enclave returned the public key. |

The client can access this Curve25519 public key irrespective of the enclave constraints. In contrast, the
KDS provides a Curve25519 private key material to the application enclave only if it meets the constraints defined 
by the client.

#### Request Example

```bash
curl --location --request POST 'localhost:8090/public' \
--header 'API-version: 1' \
--header 'Content-Type: application/json' \
--data-raw '{
    "name": "MasterKeyForTesting",
    "masterKeyType": "development",
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

!!!Warning
    
    It's critical to ensure the message's integrity and authenticity. You can verify the message by validating the
    `kdsAttestationReport` and the signature of the message.

### Endpoint - `/private`

The `/private` endpoint retrieves the private key defined by a key specification. It always returns a 32-byte array 
that forms the raw key material. You can use this key material directly as an AES256 key or as an input to a 
Curve25519 private key generation function.

The KDS enclave returns the private key only if the calling enclave meets the policy requirements defined in the key 
specification.

Note that the key specification is not encrypted or authenticated. However, if a malicious host modifies the key 
specification or the constraints, it creates a different derived key. So, any data encrypted by a client using the 
correct constraint can't be accessed using a tampered key specification.


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

| Field | Description                                                                                                                                                                           |
| ----- |---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| appAttestationReport | The `EnclaveInstanceInfo` in Base64 of the application enclave requesting access to the private key. The KDS validates this field against the policy referred in the key specification. The KDS returns the private key only if the policy check passes. |
| name | A name for the key. The KDS uses the name field to generate unique keys with the same master key and constraint configuration. |
| masterKeyType | The type of master key provider the KDS should use to source the master key.                                          |
| policyConstraint | The constraint policy to apply to the key. The KDS will reveal the keys only to an enclave that meets these constraints. |


`Response body`
```JSON
{
    "kdsAttestationReport": string,
    "encryptedPrivateKey": string
}
```

| Field | Description                                                                                                                                                                                                                                                                                                                                                                                                                      |
| ----- |----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| kdsAttestationReport | The `EnclaveInstanceInfo` in Base64 of the KDS enclave. The application enclave should validate this report before trusting the private key returned by the KDS enclave.                                                                                                                                                                                                                                           |
| encryptedPrivateKey | A Base64 field that contains the private key packaged as a Mail object encrypted using the application enclave key. The application enclave decrypts the Mail object to extract the private key. The envelope in the Mail contains the name, masterKeyType, and policyConstraint parameters of the KDS request. These parameters must be checked against the original. See below for how to deserialize the envelope. |

#### How to Deserialize the Envelope
The envelope in the private key Mail is a byte array structured as below.

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

The API version field contains the API version used for the request. The master key type field contains the type ID 
of the master key used for derivation (0, 1, and 2 for development, cluster, and Azure key vault, respectively). 

#### Request Example

```bash
curl --location --request POST 'localhost:8090/private' \
--header 'API-VERSION: 1' \
--header 'Content-Type: application/json' \
--data-raw '{
    "appAttestationReport": "RUlJAAAALDAqMAUGAytlcAMhAINkJMp+IBZSnSWEY1ux0z/HRfpzllu1I2oT0R6zl82MAAAAIARTUJux1z0gV889af4tL3iOAM300VuZbWppAZLZ/QwzAgAAAY0AAAAAYfK2fykTkGBIIPM3auay8gNNO3pLSKd4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAHAAAAAAAAAAcAAAAAAAAAWN4G6qxjHqRGYJjDJViSVwClv6xlhw5HtR26hr1swdoAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEkkyjqcgkGjwKoaJKQHqoZAHSt5+p/4STLaeYqUIWbUAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAIczKEHJt2DTFS6SfYsgVDWPAMMgy6wnPBhVHs8tfNiZevzUaW2+1dYfm62aUeGmAOI24vKtY++UV6nkTWYWRXcB",
    "name": "MasterKeyForTesting",
    "masterKeyType": "development",
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


### Error response
The endpoints send error responses in the below format:

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

## Public Key Integrity Check

You can verify the integrity of the public key present in the response as follows:

Create a byte array as described in the diagram below:

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

The name, master key type, and the policy constraint come from the request. The public key comes from the response 
after being decoded from Base64. The API version field contains the API version used for the request.

You can use this data, along with the `dataSigningKey` contained in the `kdsAttestationReport` from the response, and 
the `signature` field to check the response integrity.

The API response holds the public key in 44 bytes. The first 12 bytes represent the algorithm identifier as per 
[RFC-8410](https://datatracker.ietf.org/doc/html/rfc8410#page-3). The remaining 32 bytes hold the actual public key 
data. 

The key import fails if a library doesn't handle the initial bytes with the algorithm identifier. You can prevent 
this failure in two ways:

* Slice the byte array, ignore the first 12 bytes, and use the last 32 bytes to read the key.
* Check the first 12 bytes to know if the algorithm is supported, and use the last 32 bytes to read the key.

The integrity check ensures that no one has tampered with the public key inside the response. But it 
does not guarantee that the KDS sent the message. To ensure the authenticity of the sender, you must validate 
the `kdsAttestationReport` field in the response.

!!!Note

    Java and Kotlin developers can use [`post office`](api/-conclave%20-core/com.r3.conclave.client/-post-office-builder/using-k-d-s.html)
    instead of the `/public` end point to retrieve the KDS public keys.
