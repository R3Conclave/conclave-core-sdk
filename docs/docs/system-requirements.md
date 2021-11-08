# System requirements
## Building and running Conclave projects
Building and running Conclave projects has different requirements depending on which mode you are using.
See [Enclave Modes](enclave-modes.md#system-requirements) for more information.

## JDK Compatibility
As of the most recent release, we test building and running conclave applications using the latest OpenJDK 8 and 11.

### Enclave
All code inside the enclave module must be compatible with Java 8 or higher. The Conclave gradle plugin will automatically compile
the enclave module to Java 11 bytecode, regardless of the Java version used to build the rest of the application.
The user can change their enclave to be built in Java 8 by specifying Java source/target compatibility in the enclave's `build.gradle` file
(see [Java 11](api-changes.md#java-11)). 

### Host and Client
The host and client are normal Java libraries targeting Java 8, so all code outside the enclave module can be written
and built with any Java version that is 8 or higher.

## Gradle
The tool used to build Conclave is Gradle. The recommended version is 6.6.1.
