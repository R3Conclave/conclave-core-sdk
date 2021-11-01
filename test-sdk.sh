#!/usr/bin/env bash

set -eou pipefail

enclave_mode=$1

gradle_args="-PenclaveMode=$enclave_mode"

echo
echo Now trying to build and test the hello-world sample
echo
pushd build/distributions/conclave-sdk-*/hello-world
# just run unit test
./gradlew --stacktrace $gradle_args test
# build the host web server and client
./gradlew -q $gradle_args host:shadowJar client:shadowJar
# start the web server
$JAVA_HOME/bin/java -jar $(ls host/build/libs/host-*.jar) & _PID=$!
# wait for the web server to be ready
sleep 5
# client sends two requests, using the same state file
for ((i=0;i<2;i++));
do
    $JAVA_HOME/bin/java -jar $(ls client/build/libs/client-*.jar) "reverse me!" \
        -u=http://localhost:8080 \
        -f=client/build/state \
        -c="S:4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4 PROD:1 SEC:INSECURE"
done
# kill the web server process
kill -9 $_PID
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
