## Enclave modes

Conclave enclaves can be used in one of four modes, in order of increasing realism:

| Mode       | What it is                                                                                                            | What it's for                                                                                                                                                   |
|------------|-----------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Mock       | Your enclave class is created in the host JVM and no native or SGX specific code is used.                             | This provides a pure Java development experience that is fully supported on all platforms. See [Mock Mode](mockmode.md) for more details.                       |
| Simulation | The enclave is compiled to native machine code and loaded, but SGX hardware does not need to be present.              | This allows you to check that your enclave can be successfully compiled and run as a native binary that is separate from the host JVM, but without needing access to SGX hardware.                  |
| Debug      | The enclave is loaded using SGX hardware and drivers, but with a back door that allows debugger access to the memory. | This mode extends simulation mode by also allowing you to verify that your application works as expected when exposed to the constraints of an SGX environment. |
| Release    | The enclave is loaded using SGX hardware and drivers, and there's no back door.                                       | This is the real deal!                                                                                                                                          |

Mock mode is supported on all platforms and is all you need to get started developing Conclave apps!
Eventually, you will need to use additional modes for testing and deployment.

Only release mode locks out the host and provides the standard SGX security model.

## System requirements
The table below summarizes which modes can be used in which environments.

| OS      | Mock               | Simulation                                   | Debug                     | Release                  |
|---------|:------------------:|:--------------------------------------------:|:-------------------------:|:------------------------:|
| Linux   | :heavy_check_mark: | :heavy_check_mark:                           | :heavy_check_mark:        | :heavy_check_mark:       |
| macOS   | :heavy_check_mark: | :heavy_check_mark::fontawesome-brands-linux: | :heavy_multiplication_x:  | :heavy_multiplication_x: |
| Windows | :heavy_check_mark: | :heavy_check_mark::fontawesome-brands-linux: | :heavy_multiplication_x:  | :heavy_multiplication_x: |

!!! info
    :fontawesome-brands-linux:: Using WSL or Docker. For
    instructions on running simulation mode enclaves on Windows or macOS using Docker, see the
    [Running hello world tutorial](running-hello-world.md#beyond-mock-mode).

    Due to overheads involved with file IO in docker, Windows and macOS builds using docker may run slower than those in
    native Linux environments.

The requirements for building and running enclaves in each mode are described below.

### Mock mode

As mock mode is pure Java this will run on any system with a JDK installed.

### Simulation mode
=== "macOS / Windows"
    * **Install Docker** and ensure it has been added to the `PATH` environment variable.
    * Conclave automatically uses Docker to create a Linux build environment for *building enclaves*.
    * We recommend allocating **at least 6GB of memory** to Docker.

      We've provided instructions on how to run the hello-world sample in simulation mode with Docker in the
      [tutorials](running-hello-world.md#beyond-mock-mode).

=== "Linux"
    Make sure the C++ compiler gcc is installed. If your build system uses the aptitude package manager then you can
    install it with this command:

    ```bash
    sudo apt-get install build-essential
    ```

!!! note
    If your enclave uses reflection and/or Java serialization, some
    additional configuration files may be required when advancing from mock mode to other modes.
    See [Conclave configuration options](enclave-configuration.md#conclave-configuration-options) and
    [Assisted configuration of Native Image Builds](enclave-configuration.md#assisted-configuration-of-native-image-builds)
    for more details.

### Debug and release mode
There are no additional requirements for **building** debug and release mode enclaves on any platform.

We test building and running release-mode enclaves on Ubuntu 20.04 LTS Server x86-64.

=== "macOS / Windows"
    **Running** debug and release mode enclaves is not possible on macOS or Windows.
=== "Linux"
    **Running** debug and release mode enclaves requires SGX hardware and an installation of the Intel
    SGX driver stack. See [non-cloud deployment](non-cloud-deployment.md) for instructions.

    Please be sure that the environment variable _SGX_AESM_ADDR_ is not set.
    Failing to do so will prevent the enclave from starting up.

## Set the enclave mode
You can choose the mode when declaring a dependency on an enclave module in Gradle. For example, you might add
the following to your host `build.gradle`:

```groovy
dependencies {
    runtimeOnly project(path: ":enclave", configuration: "simulation")
}
```

!!! tip
    In the Conclave samples, we define a Gradle property `enclaveMode` in the host `build.gradle` which allows us to
    set the mode from the command line using the `-PenclaveMode` parameter.
    ```groovy
    def mode = findProperty("enclaveMode")?.toString()?.toLowerCase() ?: "mock"

    dependencies {
        runtimeOnly project(path: ":enclave", configuration: mode)
    }
    ```
    This allows us to default to mock mode for fast, iterative development, whilst retaining the ability to use
    other modes as necessary.
