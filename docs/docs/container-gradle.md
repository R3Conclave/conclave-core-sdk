# Testing on macOS

The Container Gradle script (see `scripts/container-gradle`) runs Gradle inside a Docker container configured to support
enclaves in simulation mode. It can be useful for testing your enclave on macOS.

## Usage

Just run the script with the same command line arguments you'd pass to Gradle normally, and in the same directory.
To run your host use a Gradle task that does it e.g. with the
[Gradle application plugin](https://docs.gradle.org/current/samples/sample_building_java_applications_multi_project.html#header). Example:

```
cd hello-world
../scripts/container-gradle host:run
```

!!! warning
    Native Image requires a lot of memory to build. Ensure you configured Docker via its GUI to allocate at least
    6 GB of RAM to the virtual machine and quite possibly more. You can adjust this by clicking the little whale icon
    in your menu bar, clicking Preferences, going to the Resources section and moving the slider.

## Configuration
### Multiple containers
By default port 9999 is mapped from the container to the host. If you want to start multiple containers at once you'll
need to map a different port. You can set the `CONTAINER_PORT` environment variable before running the script to do this. 
If you want to map multiple ports or have more precise control, please edit the script.

!!! tip
    If you already tried to start a second container and received the error
    `Bind for 0.0.0.0:9999 failed: port is already allocated.`, [find and remove](#troubleshooting) the container
    which has an empty value in the `PORTS` column, and then try again with the `CONTAINER_PORT` variable.

### Alternative JDKs

The container will use OpenJDK 11 by default. You can request the use of a different OpenJDK by downloading one for Linux
and then running the script with the `LINUX_JAVA_HOME` environment variable set. This allows you to run your host
with Java 8, for example.

If you change `LINUX_JAVA_HOME` then any existing container will be unusable and you'll get an error.
[Delete the container](#troubleshooting) and re-run the script.

## Copying files out of the container

Changes made inside the container will *not* propagate to disk on macOS. This is deliberate and intended to avoid the
extremely slow Docker Linux/Mac filesystem interop layer. Instead build results are redirected to a ramdisk layered on
top of the Mac filesystem. This means if you delete the container or restart Docker you'll lose your build contents. 
Keep what you build and run inside the container small! Use [mock mode](mockmode.md) the rest of the time except when testing the enclave.

There are times when you need to copy files out of the container. Although this isn't needed for
actually compiling the enclave JARs, because containers and copying are handled for you in that case, when using 
`container-gradle` to run other tasks it may be needed. You may wish for example to browse HTML reports from unit test 
runs. The `docker cp` command unfortunately won't work due to [documented limitations in the implementation related to tmpfs](https://docs.docker.com/engine/reference/commandline/cp/#extended-description),
but you can use a regular Linux `cp` command instead.

Inside the container the `/project` directory is mapped read/write to the Mac directory where you ran `container-gradle`
from. Builds take place in `/overlay` and any files written under this directory are redirected to the RAMdisk. Thus by 
copying files out of `/overlay` into `/project` you can transfer them across the slow interop layer into macOS, like so:

```bash
$ pwd
/Users/foobar/my-project

$ docker exec my-project cp -rv /overlay/build/test /project/test-results
```

## Troubleshooting
If you encounter issues, you may need to remove any conclave build containers and try again.

Use this command to see a list of containers:
```text
> docker container ls -a
CONTAINER ID   IMAGE            COMMAND                  CREATED        STATUS        PORTS                    NAMES
3a322e580c12   conclave-build   "bash"                   45 hours ago   Up 45 hours   0.0.0.0:9999->9999/tcp   conclave-hello-world
```

Find the id of the container you want to delete and remove it. If you are unsure, try removing all containers that say
`conclave-build` in the `IMAGE` column:
```text
docker rm -f 3a322e580c12
```

Now try re-running the script.