## Quick start

```bash
./scripts/idea.sh      # starts IDEA in a container
./scripts/clion.sh     # starts CLion in a container
```

## Containerization

The Oblivium project has many native components and is heavily reliant on reproducibility. For this reason the build,
the dev environment and the deployment itself is containerized.

## Oblivium build components

### cpp/

This is where all of the C++ code resides. This is mostly a CMake project, with a wrapper build.gradle extracting the
artifacts.

### cpp/linux-sgx

This is the Intel SGX SDK. We used to tweak this, but currently it's in sync with upstream.

### cpp/openjdk8

This is OpenJDK 8. We need this for the Avian build.

### cpp/avian

The avian build, modified to enable an SGX backend. Note that the SGX specific code does *not* reside here, that's in
cpp/jvm-enclave-avian.

### cpp/zlib

This is a dependency of avian, we tweak the build to build a position independent version.

### cpp/jvm-edl

This is where we generate the ECALL/OCALL boundary using Intel's EDL language. Note that the boundary is minimal, the
JVM boundary is implemented on top of this.

### cpp/jvm-enclave-common

JVM enclave code independent of the specific JVM implementation, like threading. This is supposed to be shared between
different JVM implementations (i.e. Avian, HotSpot, SubstrateVM).

### cpp/jvm-enclave-avian

Avian-specific enclave code. This project produces a partially linked enclave that only needs the application JAR to be
linked in.

### cpp/dummy-host

A dummy enclave host used to test enclaves without a JVM frontend.

### enclave-api:enclave

The API provided for the JVM enclave. This is the core API providing the Enclave interface, OCALLs and the like.

### enclave-api:host

The API from the host side used to access the enclave. This is used for creation of enclaves, making ECALLs etc.

### host-enclave-common

Code shared between the host and the enclave JVM.

### enclave-build

A library used to finish building of an enclave. This includes the final ld link as well as signing of the enclave.

### enclave-testing

A library used to test enclaves.

### enclavelet-host/grpc

The GRPC interface of the enclavelet host service.

### enclavelet-host/server

The enclavelet host implementation.

### example-enclave

An sample enclave project. The build is quite complicated at the moment.

### sgx-jvm-plugin

An SGX plugin providing build task classes. In the future it should provide the tasks themselves and hide the complexity
of building enclave images.

### containers/*

Gradle subprojects building various containers relating to the build itself as well as deployment artifacts.

### deployment/ansible

Ansible inventories of the test and CI clusters as well as playbooks installing dev ssh keys and the Intel SGX driver.

### deployment/kubernetes

Kubernetes configuration examples relating to Oblivium deployment. Note that currently this is incomplete and just a
placeholder, at the moment it's more instructive to look at configurations in enclavelet-host/server/src/test/resources.
