#!/usr/bin/env bash

set -eou pipefail

# This script requires a local build of the SDK at build/repo, which can be produced by running
# ./gradlew publishAllPublicationsToBuildRepository.

echo
echo Testing Conclave Init
echo

pushd build

conclaveInitJar=$(find repo/com/r3/conclave/conclave-init/ -name 'conclave-init-*jar' -not -name 'conclave-init-*javadoc.jar' -not -name 'conclave-init-*-sources.jar')

echo Create Java project

"$JAVA_HOME"/bin/java -jar "$conclaveInitJar" \
  --enclave-class-name "MegaEnclave" \
  --package "com.megacorp" \
  --target "mega-project" \

pushd mega-project

echo Run Java project unit tests

# As a user-facing tool, Conclave Init will produce a project which will download all the Conclave dependencies from
# Maven Central. However, for testing against a particular build of the SDK, we need to tell the generated project to
# use our local repo containing the SDK under test.

# Gradle takes into consideration the order in which the repositories are listed. Because we are applying changes to gradle files using the sed,
# the repos will appear in reverse order in the file.
# Please ensure that the url conclave-maven always appears after the repo to ensure that Gradle first looks at the local repo before searching in Artifactory.
sed -i "s/repositories {/repositories {\nmaven { url = 'https:\/\/software.r3.com\/artifactory\/conclave-maven' }/" build.gradle
sed -i "s/repositories {/repositories {\nmaven { url = '..\/..\/repo' }/" build.gradle
sed -i "s/repositories {/repositories {\nmaven { url = '..\/repo' }/" settings.gradle
./gradlew test

echo Run Java project host and client

# NOTE: this command is only run in mock mode, so that the signer can be provided below.
host_output_file="host_output.log"
./gradlew :host:bootRun & > $host_output_file
_PID=$!

sleep 5
./gradlew :client:run \
  --args="'S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE'"

# Ensure the logs from the host are working (CON-1193)
# Log output to the screen for debugging purposes
cat $host_output_file
# To check if the logs are working, strings from different lines will be checked
# The number of lines that were found in the logs will be stored in the variable
numberOfExpectedMatchingLines=3
numberOfLinesFoundInLogs=$(grep -c "Remote attestation for enclave\|Tomcat started on port(s):\|Started EnclaveWebHost.Companion in" $host_output_file)
rm host_output.log
if [ "$numberOfLinesFoundInLogs" != "$numberOfExpectedMatchingLines" ]; then
  echo "The logs might not be working properly. Number of matching lines found: $numberOfLinesFoundInLogs. Expected number of matching lines: $numberOfExpectedMatchingLines"
  exit 1
fi

# kill the host
kill -9 $_PID
sleep 10
popd


echo Create Kotlin project

"$JAVA_HOME"/bin/java -jar $conclaveInitJar \
  --enclave-class-name "MegaEnclave" \
  --package "com.megacorp" \
  --target "mega-kotlin-project" \
  --language kotlin \

pushd mega-kotlin-project

echo Run Kotlin project unit tests

# Gradle takes into consideration the order in which the repositories are listed. Because we are applying changes to gradle files using the sed,
# the repos will appear in reverse order in the file.
# Please ensure that the url conclave-maven always appears after the repo to ensure that Gradle first looks at the local repo before searching in Artifactory.
sed -i "s/repositories {/repositories {\nmaven { url = 'https:\/\/software.r3.com\/artifactory\/conclave-maven' }/" build.gradle
sed -i "s/repositories {/repositories {\nmaven { url = '..\/..\/repo' }/" build.gradle
sed -i "s/repositories {/repositories {\nmaven { url = '..\/repo' }/" settings.gradle
./gradlew test

echo Run Kotlin project host and client

# NOTE: this command is only run in mock mode, so that the signer can be provided below.
./gradlew :host:bootRun &
 _PID=$!

sleep 5
./gradlew :client:run \
  --args="'S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE'"

# kill the host
kill -9 $_PID
sleep 10
popd

# clean up
rm -r mega-project
rm -r mega-kotlin-project
popd
