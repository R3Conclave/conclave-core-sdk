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
./host/build/install/host/bin/host --sealed.state.file=/tmp/host.state & _PID=$!
# starting spring boot might take a while
sleep 5

./gradlew --stacktrace -q client:run --args "reverse me!"

# kill the host process
kill -9 $_PID
rm -rf /tmp/host.state

popd
sleep 10

echo
echo Now testing Conclave Init
echo

pushd build/distributions/conclave-sdk-*/

conclaveVersion=${PWD#*conclave-sdk-}
conclaveRepo=$PWD/repo

# create java project
$JAVA_HOME/bin/java -jar conclave-init.jar \
  --enclave-class-name "MegaEnclave" \
  --package "com.megacorp" \
  --target "mega-project"

# run the unit tests of the new project
pushd mega-project
./gradlew test
popd

# create kotlin project
$JAVA_HOME/bin/java -jar conclave-init.jar \
  --enclave-class-name "MegaEnclave" \
  --package "com.megacorp" \
  --target "mega-kotlin-project" \
  --language kotlin

# run unit tests
pushd mega-kotlin-project
./gradlew test
popd

# clean up
rm -r mega-project
rm -r mega-kotlin-project
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
