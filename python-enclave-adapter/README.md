# Adapter enclave for PoC Python support

This module contains the code for a "normal" Conclave enclave that extends `Enclave`, `PythonEnclaveAdapter`. This 
class delegates the calls it receives into a user specified Python script. The Python code is executed using
[Jep](https://github.com/ninia/jep). This allows us to implement the enclave functionality in Python without 
re-implementing all the underlying enclave and Mail code.

This attempt is currently a PoC, and so is the Python API. Only mock mode works, and it hasn't yet been wired up to 
the plugin. Have a look at the tests to see how to create and run the enclave. The tests can be run in the usual way:

```shell
./gradlew python-enclave-adapter:test
```

## Python API

The API uses duck typing and the enclave will look for the following optional global functions:

* `on_enclave_startup()` - executed by `onStartup`
* `on_enclave_shutdown()` - executed by `onShutdown`
* `receive_from_untrusted_host(bytes)` - executed by `receiveFromUntrustedHost`. The Java byte array is converted to 
  Python bytes. If there’s no return value then it is treated as null, otherwise the return value is expected to be 
  bytes.
* `receive_enclave_mail(mail)` - executed by `receiveMail`. The Java `EnclaveMail` object is converted to a simpler 
  Python version which is just a class holding the body, envelope and authenticated sender. The topic and sequence 
  number are ignored for now. The authenticated sender is represented by its encoded binary form in bytes. This 
  function is also much simpler than `receiveMail` in that the response can simply be returned from the function and 
  it will encrypted as a response mail back to the sender key and posted.

There’s also a global `enclave_sign(data)` function available which delegates to the Conclave enclave’s signing 
ability via `signer()`.

## Next steps

The next steps would be get the other modes working and integrated with the Gradle plugin. The idea is that the plugin 
will think `PythonEnclaveAdapter` is the "end" user enclave and build a Gramine-based enclave around it. The user 
will also need to be able to specify the Python script containing the implementation of their enclave. This would 
then get bundled together with the `PythonEnclaveAdapter` fat jar and run inside Gramine.
