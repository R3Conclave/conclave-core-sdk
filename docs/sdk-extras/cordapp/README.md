# CorDapp Sample - Java

This is a simple CorDapp using the Conclave API. It is licensed under the Apache 2 license, and therefore you 
may copy/paste it to act as the basis of your own commercial or open source apps.

# Usage

## Running the nodes (locally)

Build and deploy the nodes:

```
./gradlew deployNodes
```

After the build finishes, navigate to build/nodes/ where you will find two folders, one for each node (PartyA and PartyB).

Navigate to PartyA and PartyB folders, and from the terminal, in each, enter:

```
java -jar corda.jar
```

## Interacting with the nodes's shell

When started via the command line, each node will display an interactive shell.

To securely reverse a string between two parties, enter the following shell command:
- from PartyA's shell
```
flow start ReverseFlow sendTo: "PartyB"
```
- from PartyB's shell
```
flow start ReverseFlow sendTo: "PartyA"
```
