#!/usr/bin/env bash

set -eou pipefail

enclave_mode=$1

gradle_args="-PenclaveMode=$enclave_mode"

# Java 8 is required to run Corda
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
echo
echo Testing the CorDapp sample
echo
export PATH=$JAVA_HOME/bin:$PATH
pushd build/distributions/conclave-sdk-*/cordapp
./gradlew $gradle_args workflows:test
popd
