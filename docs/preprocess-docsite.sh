#!/usr/bin/env bash

# This script takes the output of the gradle 'apidocs' task and performs any further
# processing prior to publishing or packaging. 

# The dokka plugin outputs kotlin ByteArray types incorrectly as kotlin.Array[]. Convert
# it to the correct type here.
find docs/api/ -iname "*.html" -type f -exec sed -i 's/kotlin.Array\[\]/byte\[\]/g' {} \;

# Remove @JvmStatic kotlin annotations. This removes removes the annotation, and if it
# is followed by a newline and a space they are removed too to ensure leading spaces
# are removed from subsequent annotations.
find docs/api/ -iname "*.html" -type f -exec sed -z -i 's/@JvmStatic\s* *//g' {} \;

