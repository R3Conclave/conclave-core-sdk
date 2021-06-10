# Known issues

The Conclave API is by now mostly stable, but small changes may still occur. In particular we may adjust mail to support streaming access. 
You should expect to spend a few minutes updating your code between releases, but not more.

This release ships with the following known issues that we plan to address in future versions:

1. Some system level exceptions like divide by zero or using null reference may crash the enclave/host process.
1. Opening network sockets doesn't work. We plan to support opening *outbound* sockets in future, but running socket
   based *servers* inside an enclave is probably not the best way to use enclave technology. Please read about [mail](mail.md)
   to learn how to send messages to and from an enclave.
1. Mail is limited in size by the size of the enclave heap, and the size of a Java array (2 gigabytes).
1. Enclaves built using Conclave currently do not have a stable measurement, meaning that each time you build your enclave you will end up with a different MRSIGNER value.
1. JavaDocs don't integrate with IntelliJ properly. This is due to a bug in IntelliJ when loading modules from
   on disk repositories.