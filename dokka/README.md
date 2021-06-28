# Conclave Dokka fork

## Why do we fork Dokka?
The latest version of Dokka (as of 21/04/21) is a complete rewrite of the version we were previously using. Although
this is published via the public plugin repository, it is marked as an Alpha in the Dokka github releases list and does
contain a number of significant bugs that affect the output of our documentation.

However, the previous version had many bugs too and the output looked dated so we decided to move ahead with the latest
version and patch any issues to create a decent output.

## What has been fixed?
Each fix is a separate commit in the 'conclave-changes' branch of https://github.com/R3Conclave/dokka, and consists of:

1. Dokka includes a setting named 'includeNonPublic' which default to false. In this configuration only public members 
   are published to documentation. However, protected members form part of the interface and should be included. Setting
   this to true results in the protected members being visible but also all private members. Our change groups public and
   protected together when 'includeNonPublic' is false.
2. The Dokka setting, 'suppressObviousFunctions' removes methods such as `hashCode` and `toString` from the documentation
   but leaves obvious properties in place. Our patch removes these from the documentation.
3. Dokka filters were not being run on Enum values resulting in obvious functions/properties being present in the 
   documentation.
4. If a function is provided for a getter/setter for a property then the documentation for the getter/setter was not being
   generated.
5. Added translation for some basic JVM types that were left as Kotlin types during the conversion to Java documentation, 
   including `void`, `int`, `long` and `byte[]`.
6. Update class template for javadoc to include a missing marker that is required for IDE integration.
7. On "See also" sections, don't try to show the FQN. Instead, use the link name. This is because the 'hack' to get the
   FQN in docker misses off the target if it is a method or property.
8. Fix "See also" links to companion objects that were unresolved.
9. Workaround issue where the @JvmStatic annotation was not being resolved by the compiler. This should really be investigated
   and fixed properly by resolving the reference but the workaround works for now.
10. Modify 'isObvious' logic to allow FAKE_OVERRIDE methods to be documented otherwise inherited non-obvious methods are hidden.
11. In class summary page, always show full documentation rather than just first sentence.
12. Rename "Functions" to "Methods".
13. Hide 'final' modifier from functions.


## Using the R3 dokka version
Our SGXJVM root gradle project refers to the dokka plugin, therefore the plugin itself cannot be built as part of our build 
script. Instead, dokka must be built and published and be made available in a maven repository before Conclave can be built. 
To achieve this, we publish a pre-built zip file containing the required repository to our github fork of dokka. This is 
then downloaded and unzipped in our sgxjvm-build container. The repo is put into /opt/dokka. The conclave build.gradle 
then has a mavenLocal repository configuration pointing to this directory.

## Modifying and building a new version.
There is a script named build_dokka.sh in this folder. This clones our specific fork of dokka and builds it. It then captures the
dokka repository in a zip file.

Once you have the zip file you need to create a new github release: https://github.com/R3Conclave/dokka/releases/new.
Upload the zip file as part of the release then update the sgxjvm-build container dockerfile to download the new zip
file.
