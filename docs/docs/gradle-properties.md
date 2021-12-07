# Gradle Properties

## Introduction

The Conclave samples use
[Gradle properties](https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties)
to import the SDK libraries into Conclave projects. The properties in question are `conclaveRepo` and `conclaveVersion`.

This is configured in `settings.gradle`. For example, consider the file
`/path/to/conclave/sdk/hello-world/settings.gradle`, where these properties are used to import the Conclave maven
repository and apply the Conclave plugin:
```groovy
pluginManagement {
    repositories {
        maven {
            def repoPath = file(rootDir.relativePath(file(conclaveRepo)))
            // Error handling
            url = repoPath
        }
        ...
    }

    plugins {
        id 'com.r3.conclave.enclave' version conclaveVersion apply false
    }
}

...
```

## Set conclaveRepo and conclaveVersion

Gradle properties are typically set from `gradle.properties` files. For more information, see
[the Gradle docs](https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties).

!!! tip
    [Conclave Init](conclave-init.md) can configure `gradle.properties` for you! Just run the tool and let it handle
    the rest.


### User settings
The recommended place to set the `conclaveRepo` and `conclaveVersion` properties is in the user-wide `gradle.properties`
file. By default, this file is located at `$HOME/.gradle/gradle.properties`

This approach means that you only have to set the properties once, rather than for every project.

### Project settings
You may notice that each sample in the Conclave SDK contains a `gradle.properties` file in the project root.
This allows the projects to compile as soon as the SDK is unzipped, without requiring any additional configuration.
You can also use project-level `gradle.properties` in your own projects.

!!! warning
    Properties set in the user-wide `gradle.properties` file will override those set in any individual project.

## Use a different resolution strategy
Using the `conclaveRepo` and `conclaveVersion` properties is just a convention, and it is entirely up to you how you
import the repo and apply the plugin in your projects.