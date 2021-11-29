### Constraints

How do you know the [`EnclaveInstanceInfo`](api/-conclave/com.r3.conclave.common/-enclave-instance-info/index.html) you've got is for the enclave you really intend to interact with? In normal
client/server programming you connect to a host using some sort of identity, like a domain name or IP address. TLS
is used to ensure the server that picks up is the rightful owner of the domain name you intended to connect to. In
enclave programming the location of the enclave might not matter much because the host is untrusted. Instead, you have
to verify *what* is running, rather than *where* it's running.

!!! note
    The domain name of the server can still be important in some applications, in which case you should still use TLS
    in addition to enclave constraints.

One way to do this is by inspecting the properties on the [`EnclaveInstanceInfo`](api/-conclave/com.r3.conclave.common/-enclave-instance-info/index.html) object and hard-coding some logic. That
works fine, but testing an `EnclaveInstanceInfo` is a common pattern in enclave programming, so we provide an API to
do it for you.

The [`EnclaveConstraint`](api/-conclave/com.r3.conclave.common/-enclave-constraint/index.html) class takes an [`EnclaveInstanceInfo`](api/-conclave/com.r3.conclave.common/-enclave-instance-info/index.html) and
performs some matching against it. A constraint object can be built in code, or it can be loaded from a small domain
specific language encoded as a one-line string. The string form is helpful if you anticipate frequent upgrades that
should be whitelisted or other frequent changes to the acceptable enclave, as it can be easily put into a
configuration file, JSON, XML or command line flags.

The constraint lets you specify:

1. Acceptable code hashes (measurements)
2. Acceptable signing public keys
3. The minimum revocation level
4. The product ID
5. The security level of the instance: `SECURE`, `STALE`, `INSECURE`
6. The maximum age of the attestation in ISO-8601 duration format

If you specify a signing public key then you must also specify the product ID, otherwise if the organisation that
created the enclave makes a second different kind of enclave in the future, a malicious host might connect you with the
wrong one. If the input/output commands are similar then a confusion attack could be opened up. That's why you must
always specify the product ID.

The simplest possible string-form constraint looks like this:

`C:F86798C4B12BE12073B87C3F57E66BCE7A541EE3D0DDA4FE8853471139C9393F`

It says "accept exactly one program, with that measurement hash". In this case the value came from the output of the
build process as shown above. This is useful when you neither trust the author nor the host of the enclave, and want to
audit the source code and then reproduce the build.

Often that's too rigid. We trust the *developer* of the enclave, just not the host. In that case we'll accept any enclave
signed by the developer's public key. We can express that by listing code signing key hashes, like this:

`S:5124CA3A9C8241A3C0A51A1909197786401D2B79FA9FF849F2AA798A942165D3 PROD:1`

When constraining to a signing key we must also specify the product ID, because a key can be used to sign more than
one product.

As you can see from the above code, the enclave constraint is passed into the client via a command line flag:

```bash
--constraint="S:5124CA3A9C8241A3C0A51A1909197786401D2B79FA9FF849F2AA798A942165D3 PROD:1 SEC:INSECURE"
```

!!! tip
    Replace the signing key in the snippet above with the enclave signer hash that was printed when you
    built the enclave.

The above constraint says that any enclave (even if run in simulation mode) signed by this hash of a code signing key
with product ID of 1 is acceptable. Obviously in a real app, you would remove the part that says `SEC:INSECURE`, but
it's convenient to have this whilst developing.

[`EnclaveConstraint.check`](api/-conclave/com.r3.conclave.common/-enclave-constraint/check.html) compares the
enclave's [`EnclaveInstanceInfo`](api/-conclave/com.r3.conclave.common/-enclave-instance-info/index.html) against the constraint and
throws an [`InvalidEnclaveException`](api/-conclave/com.r3.conclave.common/-invalid-enclave-exception/index.html)) if it doesn't
match. This check is done automatically by
[`EnclaveClient.start`](api/-conclave/com.r3.conclave.client/-enclave-client/start.html) when using `EnclaveClient`.

If needed, more than one key hash could be added to the list of enclave constraints (e.g. if simulation and debug
modes use a distinct key from release mode). The enclave is accepted if one key hash matches.

```
// Two distinct signing key hashes can be accepted.
S:5124CA3A9C8241A3C0A51A1909197786401D2B79FA9FF849F2AA798A942165D3 S:01280A6F7EAC8799C5CFDB1F11FF34BC9AE9A5BC7A7F7F54C77475F445897E3B PROD:1 SEC:INSECURE
```

If you are building an enclave in mock mode then the enclave reports it is using a signing key hash consisting of
all zeros. If you want to allow a mock enclave to pass the constraint check then you need to include this dummy
signing key in your constraint:

```
// The zero dummy hash can be accepted.
S:5124CA3A9C8241A3C0A51A1909197786401D2B79FA9FF849F2AA798A942165D3 S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE
```

It is also possible to specify a maximum age for the attestation using the EXPIRE keyword:

```
S:5124CA3A9C8241A3C0A51A1909197786401D2B79FA9FF849F2AA798A942165D3 PROD:1 SEC:INSECURE EXPIRE:P6M2W5D
```

When specified, this will cause the check to fail if the timestamp within the attestation object indicates an
age older than the specified duration. If no period is specified then no expiry check will be applied. The age string
uses the ISO-8601 duration format. The above example is enforcing a maximum age of 6 months, 2 weeks and 5 days.