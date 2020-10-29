## Conclave Threading Test

A CLI application for testing threading in conclave.

There are two enclaves:
- **FibonacciEnclave** computes a given term in the fibonacci sequence.
- **BusyEnclave** increments a counter in a loop to simulate real work. Multiple threads can be created inside the enclave, each of which will perform this computation, and will return after the specified duration.

FibonacciEnclave creates a consistent amount of work with each call, while BusyEnclave creates calls with a consistent duration.

The enclaves can be accessed via the host application, which can call them multiple times, from multiple threads.

# How to run
First, ensure that the `conclaveRepo` and `conclaveVersion` are set in a `gradle.properties` file.

## Linux
```shell
$ ./gradlew host:assemble
$ tar -xvf host/build/distributions/host.tar
$ ./host/bin/host <args>
```
## macOS
Since SGX is only supported on linux, the host must be run in a docker container
```shell
$ ./gradlew host:assemble
$ docker run -t --rm \
      -v $PWD:/project \
      -p 9999:9999 \
      -w /project \
      --user $(id -u):$(id -g) \
      conclave-build \
      /bin/bash -c "mkdir -p run && \
      tar xf host/build/distributions/host.tar -C run && \
      ./run/host/bin/host <args>"
```

## Examples
Calculate random fibonacci terms between 20 and 30 (inclusive), 5 times, using 2 threads.
```shell
$ ./host/bin/host fib --calls 5 --threads 2 --min 20 --max 30
```

Call the busy enclave 3 times. Each call will spawn 2 threads in the enclave that will wait for 1000 milliseconds.
```shell
$ ./host/bin/host busy --calls 3 --hostThreads 2 --enclaveThreads 2 --duration 1000
```

## Output
the `fib` command creates an CSV in the `output` directory with the following fields:
* `JOB_NUMBER` the job number, in the order that they were called
* `TERM` the term in the fibonacci sequence that was computed
* `DURATION` the amount of time the enclave call took, in microseconds

# Data Analysis
The [analysis directory](./analysis) contains some python scripts for monitoring Memory and CPU and plotting results.
