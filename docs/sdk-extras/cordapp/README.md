# CorDapp Sample - Java

This is a simple CorDapp using the Conclave API. It is licensed under the Apache 2 license, and therefore you 
may copy/paste it to act as the basis of your own commercial or open source apps.

# Usage

Unlike most CorDapps this one will *only run on Linux* due to the need for an enclave. But don't fear: you can use 
virtualisation to run it on Windows and macOS as well. For Windows it will unfortunately require some manual work. 
On macOS you can install Docker Desktop and then use the handy `container-gradle` script found in the SDK scripts 
directory, which does everything for you.

## Linux

1. Download an Oracle Java 8 and unpack it somewhere.
2. Run `./gradlew workflows:test`

## MacOS

1. Download an Oracle Java 8 **for Linux** ("Linux x64 Compressed Archive") and unpack it somewhere **in your home directory**. 
   It must be under $HOME as otherwise Docker will complain. For example, use `~/jdk8`.
2. Run `env LINUX_JAVA_HOME=$HOME/jdk8 ../scripts/container-gradle workflows:test`

If you make any mistakes during this process you may need to use the `docker ps -a` and `docker rm` commands to delete
the container the script creates.

## Windows

We don't have tested instructions for that at this point but you can try using WSL2 to set up an Ubuntu environment
and then follow the Linux instructions. It should work! Of course, only in simulation mode.