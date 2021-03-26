# General integration tests

This houses the general integration tests which were originally native "unit" tests in the main code base and which only
tested using Avian as the runtime. To work around the slow building of native image enclaves, this project has several
general-purpose enclaves which are built only once during test execution and which are then reused by the tests.

The tests themselves are located in the `:general:tests` module, which acts as the "host" to the general enclaves. The
enclaves are designed to receive serialised `JvmTestTask` messages, which encapsulate the tests, deserialises them using
Kotlin serialisation and runs them inside the enclave. The results of the test are serialised back to the host test.
