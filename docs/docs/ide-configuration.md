### IDE Documentation in the root `build.gradle` file
!!! info
    At the moment, IntelliJ IDEA has an issue that means it does not correctly display the documentation for Conclave,
    even if you provide this configuration. Instead, please refer to the [online Javadocs](https://docs.conclave.net/api/index.html)
    for Conclave.

Some IDEs are able to automatically display Conclave SDK documentation whilst editing code. In order for this to
work you may need to add some configuration to the root `build.gradle` depending on your IDE.

Start by adding the Gradle plugin required to support your IDE. Note that Visual Studio Code shares the configuration
provided by the `eclipse` plugin.

```groovy hl_lines="3 4"
plugins {
    id 'java'
    id 'idea'
    id 'eclipse'
}

```

Then add sections to tell the IDEs to download Javadoc for dependencies.

```groovy
eclipse {
    classpath {
        downloadJavadoc = true
    }
}

idea {
    module {
        downloadJavadoc = true
    }
}
```

Finally apply the same configuration to all subprojects.

```groovy hl_lines="2-13"
subprojects {
    apply plugin: 'idea'
    apply plugin: 'eclipse'
    idea {
        module {
            downloadJavadoc = true
        }
    }
    eclipse {
        classpath {
            downloadJavadoc = true
        }
    }

    repositories {
```

