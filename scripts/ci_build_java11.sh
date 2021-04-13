#!/usr/bin/env bash
set -xeuo pipefail

# This configuration is used as a regression test to make sure PR changes do not break
# Java 11 compatibility. See java11.md in the internal docs for details.
#
# A full composite build is performed but no artifacts are published. The samples use
# the local SDK composite build rather than pulling published artifacts
#
# If the build changes the build-image itself then this is published before the remainder
# of the build commences in order to ensure the correct build environment

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

# Kill any lingering Gradle workers
ps auxwww | grep Gradle | grep -v grep | awk '{ print $2; }' | xargs -r kill || true

loadBuildImage

# Then build and run the tests. Note that the SDK build excludes dokka due to the dokka plugin not supporting
# Java 11.
TEST_OPTS=${TEST_OPTS:-}
runDocker com.r3.sgx/sgxjvm-build "JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 JVMCI_HOME=\$LABSJDK_HOME ./gradlew test sdk -PexcludeDokka $TEST_OPTS -i \
    && cd samples && JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 JVMCI_HOME=\$LABSJDK_HOME ./gradlew --refresh-dependencies test -i"
