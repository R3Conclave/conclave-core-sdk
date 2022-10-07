# Known issues

The Conclave API is by now mostly stable, but small changes may still occur. In particular, we may adjust
Conclave Mail to
support streaming access.
You should expect to spend a few minutes updating your code between releases, but not more.

This release ships with the following known issues that we plan to address in future versions:

1. Some system level exceptions like divide by zero or using null reference may crash the enclave/host process.
2. Opening network sockets doesn't work. We plan to support opening *outbound* sockets in the future, but running
   socket-based *servers* inside an enclave is probably not the best way to use enclave technology. Please read
   about [Conclave Mail](mail.md) to learn how to send messages to and from an enclave.
3. Mail is limited in size by the size of the enclave heap, and the size of a Java array (2 gigabytes).
4. Enclaves built using Conclave currently do not have a stable measurement, meaning that each time you build your
   enclave you will end up with a different MRSIGNER value.
5. JavaDocs don't integrate with IntelliJ properly. This is due to a bug in IntelliJ when loading modules from
   on disk repositories.
6. On Windows and macOS, serialization and reflection configuration files configured as described on
   [enclave configuration](enclave-configuration.md) must use non-relative paths. Additionally, on Windows, the paths
   must use forward slashes rather than Windows standard backslashes.
7. Conclave works *only* in [mock mode](enclave-modes.md#mock-mode) on
   [new Mac computers with Apple silicon](https://support.apple.com/en-in/HT211814) due to the reliance on x64 binaries.
