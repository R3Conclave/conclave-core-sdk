# General integration tests

This houses the general integration tests which were originally native "unit" tests in the main code base and which only
tested using Avian as the runtime. To work around the slow building of native image enclaves, this project has several
general-purpose enclaves which are built only once during test execution and which are then reused by the tests.

The tests themselves are located in the `:general:tests` module, which acts as the "host" to the general enclaves. The
enclaves are designed to receive serialised `EnclaveTestAction` messages, which encapsulate the tests, deserialises them using
Kotlin serialisation and runs them inside the enclave. The results of the test are serialised back to the host test.

There is a separate `:general:mock-compatibility-tests` from the main `:general:tests` module. This houses tests which
need to load the same enclave in both mock mode and non-mock mode. Recall that `EnclaveHost.load` will automatically
use mock mode if it detects that the enclave is on its classpath. If it detects that both the enclave .so file and the
enclave class exist on the classpath at the same time then it errors an exception. We don't want this exception to be
thrown for the mock compatibility tests and so we have them in a separate module to avoid any issues with the other 
tests.
