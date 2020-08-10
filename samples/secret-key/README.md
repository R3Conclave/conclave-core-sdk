## Mock secret key compatibility test

This is not actually a sample but rather a hardware-only test to make sure that the keys produced by
`MockEnclaveEnvironment.getSecretKey` behave similarly to those produced in hardware.

We don't currently have a nice place for tests which only run in hardware mode, which is why this exists as a "sample".
 