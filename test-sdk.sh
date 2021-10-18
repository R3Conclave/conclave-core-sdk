#!/usr/bin/env bash

set -eou pipefail

sgx_mode=$1

gradle_args="-PenclaveMode=$sgx_mode"

echo
echo Now trying to build and test the hello-world sample
echo
pushd build/distributions/conclave-sdk-*/hello-world
# just run unit test
./gradlew --stacktrace $gradle_args host:test
#build and start the host
./gradlew -q $gradle_args host:installDist
./host/build/install/host/bin/host  --enclave.class=com.r3.conclave.sample.enclave.ReverseEnclave --sealed.state.file=/tmp/host.state &
# starting spring boot might take a while
sleep 5

./gradlew --stacktrace -q client:run --args "reverse me!"

# kill the host process
kill -9 `ps -ef | grep com.r3.conclave.sample.enclave.ReverseEnclave | grep -v grep | tr -s ' ' | cut -d ' ' -f2`
rm -rf /tmp/host.state

popd

# Java 8 is required to run Corda
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
echo
echo Testing the CorDapp sample
echo
export PATH=$JAVA_HOME/bin:$PATH
pushd build/distributions/conclave-sdk-*/cordapp
./gradlew $gradle_args workflows:test
popd
