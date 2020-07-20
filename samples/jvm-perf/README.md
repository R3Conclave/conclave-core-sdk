## Conclave JVM Performance Test

This app provides benchmarks for various different computation loads and allows them to be run on 3 different targets:

1. JVM on the host platform
2. Conclave using an Avian JVM runtime
3. Conclave using a GraalVM native-image runtime (SubstrateVM)

## Building the benchmarks
The benchmark app is built as one of the sample projects in the source tree.

```
cd samples
./gradlew build :jvm-perf:jvm-perf:build
```

## Running the benchmarks

The benchmarks use [JMH - Java Microbenchmark Harness](http://tutorials.jenkov.com/java-performance/jmh.html) in the 
host to run and measure the benchmarks. When running the benchmarks you can use any of the command-line parameters
supported by JMH.

The output of the JVM performance test build is `jvm-perf/build/libs/jvm-perf-[mode].jar`,
where `[mode]` is either `simulation` or `debug`. Release builds are supported so long as 
the enclaves are signed for release. The JAR is a fat JAR that contains all the classes and
enclaves necessary to run the benchmarks.

To run the full set of benchmarks, execute:

`java -jar jvm-perf-simulation.jar`

This will execute all benchmarks on all platforms; Avian-based enclaves, GraalVM native-image
based enclaves and on the host JVM.

It is possible to execute a subset of benchmarks by passing parameters to the app. You can get
a list of the supported benchmarks with the following command:

`java -jar jvm-perf-simulation.jar -l`

To execute a single set of tests you can just provide the last part of the name reported by the command above. E.g. to execute `com.r3.conclave.jvmperf.EnclaveBenchmark.binary_trees`:

`java -jar jvm-perf-simulation.jar binary_trees`

By default the benchmark will run all all supported runtimes. To run on a single runtime, 
e.g. Avian:

`java -jar jvm-perf-simulation.jar -p runtime=avian binary_trees`

### Running in a real SGX environment
The benchmarks can run in hardware SGX as well as simulation. 
To build the debug version, edit `samples/gradle.properties` and change
`sgx_mode=simulation` to `sgx_mode=debug`. You can also build a release version but you will
need to configure signing in this case.

To run the benchmarks you need to specify an SPID and attestation key. This can be done via
parameters to the benchmark app:

`java -jar jvm-perf-simulation.jar -p spid=[hex bytes] attestationKey=[hex bytes]`. An attestation
is performed at the start of every benchmark and applies to each test on each runtime. So for
'mandelbrot' on Avian, the attestation will be performed on the Avian enclave before the warmup
and the enclave is destroyed at the end of the 'mandelbrot' iterations.


## Interpreting the results
The default parameters for the tests measure throughput in operations per seconds and include
two warmup iterations and 5 measuring iterations. 

The warmup iterations are included to allow
the environment to settle and for the runtime to perform optimisations. 2 iterations may not
be enough for a JVM such as Hotspot to become stable but Conclave enclaves do not
perform runtime optimisation so more warmup iterations are unnecessary. In order for the host
benchmark measurements to be stable you may want to increase this number using the `wi` parameter. E.g.:

`java -jar jvm-perf-simulation.jar -wi 10 mandelbrot`

The actual benchmark measurements are taken following the warmups. You can see the data
for the measurements for both the warmups and the iterations in the output of the tool:

```
# Warmup Iteration   1: 5.990 ops/s
# Warmup Iteration   2: 6.059 ops/s
Iteration   1: 5.755 ops/s
Iteration   2: 5.881 ops/s
Iteration   3: 5.919 ops/s
Iteration   4: 5.894 ops/s
Iteration   5: 5.915 ops/s
```

When the tests are complete you get a summary of all tests:

```
# Run complete. Total time: 00:00:17

Benchmark                    (runtime)   Mode  Cnt  Score   Error  Units
EnclaveBenchmark.mandelbrot      avian  thrpt       2.099          ops/s
EnclaveBenchmark.mandelbrot    graalvm  thrpt       5.629          ops/s
EnclaveBenchmark.mandelbrot       host  thrpt       6.380          ops/s
```

This gives the names of the benchmarks and the runtime platform along with the mode in
which they were executed, how many iterations and the performance score.
By default the score is reported in operations per second which represents how many 
times could the code under test be run in 1 second. Higher numbers are better.

For more details on configuring JMH, interpreting the output and configuring the benchmark,
see [here](http://tutorials.jenkov.com/java-performance/jmh.html).

For a list of command-line options to our test tool (using JMH) see [here](https://github.com/guozheng/jmh-tutorial/blob/master/README.md).

## Description of individual tests
The tests below with the exception of 'Empty' and 'Himeno' were all taken from
[The Computer Language Benchmarks Game](https://benchmarksgame-team.pages.debian.net/benchmarksgame/fastest/java.html). 
They have only been modified slightly in order to work in our benchmark harness with changes such as the following:

* Implementation of our `Benchmark` interface for a consistent way to run the tests
* Removal of any output to stdout/stderr as we want purely computational performance rather than exiting enclaves to print to console
* Removal of any threading code to run all benchmarks singled-threaded

### Empty
`java -jar jvm-perf-simulation.jar empty`

This is a benchmark of an empty function. This does not make much sense for a host measurement
but is useful to compare the entry/exit speed of Avian vs GraalVM native-image enclave calls.

### Fannkuch
`java -jar jvm-perf-simulation.jar fannkuch`

[Fannkuch-redux Description](https://benchmarksgame-team.pages.debian.net/benchmarksgame/description/fannkuchredux.html#fannkuchredux)


### Mandelbrot
`java -jar jvm-perf-simulation.jar mandelbrot`

[Mandelbrot Description](https://benchmarksgame-team.pages.debian.net/benchmarksgame/description/mandelbrot.html#mandelbrot)

### N-Body
`java -jar jvm-perf-simulation.jar nbody`

[N-Body Description](https://benchmarksgame-team.pages.debian.net/benchmarksgame/description/nbody.html#nbody)

### Spectral-Norm
`java -jar jvm-perf-simulation.jar spectralnorm`

[Spectral-Norm Description](https://benchmarksgame-team.pages.debian.net/benchmarksgame/description/spectralnorm.html#spectralnorm)

### Fasta
`java -jar jvm-perf-simulation.jar fasta`

[Fasta](https://benchmarksgame-team.pages.debian.net/benchmarksgame/description/fasta.html#fasta)

### Pi Digits
`java -jar jvm-perf-simulation.jar pidigits`

[Pi Digits Description](https://benchmarksgame-team.pages.debian.net/benchmarksgame/description/pidigits.html#pidigits)

### Himeno
`java -jar jvm-perf-simulation.jar himeno`

This benchmark test program is measuring a cpu performance of floating point operation by a Poisson equation solver.

Written by Ryutaro Himeno, Dr. of Eng. Head of Computer Information Division, RIKEN (The
Institute of Physical and Chemical Research).

(From the original source file description)
This measures performance using a linear solver of pressure Poisson eq. which
appears in an incompressible Navier-Stokes solver. A point-Jacobi method is
employed in this solver.
