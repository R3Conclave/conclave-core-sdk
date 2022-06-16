# Conclave Graal
This project builds and publishes the artifact graal-sdk.tar.gz required by Conclave. This module is a separate and 
independent Gradle project.

## Project structure
The files inside this project are grouped to make the project discovery easy.

### Containers
The folder 'containers' groups all the docker files which generate the docker images required to build Graal without
having to worry about installing any required tools in your system. 

### Patches
The folder 'patches' contains the patch that updates Graal source code as required by Conclave.

### Scripts
The folder 'scripts' groups all the scripts. All scripts prefixed with "ci_" are used by TeamCity, but they can be run
in your local machine. However, you must respect the order they are expected to be run. For instance, you must run
the 'ci_build_docker_images.sh' at least once before running 'ci_build.sh'. The 'ci_build_docker_images.sh' script
generates a docker image that can be used by 'ci_build.sh'. The docker image is stored inside the 'build' folder, so
if the folder gets deleted, you will have to run 'ci_build_docker_images.sh' again first.

Use the script 'devenv_shell.sh' to start a docker container that is properly configured to build and publish Graal. 
Consider using this shell while testing Gradle tasks on your machine.

## Building Graal
To build graal follow the instructions below. 
```
./scripts/devenv_shell.sh
./gradlew buildGraal
```
