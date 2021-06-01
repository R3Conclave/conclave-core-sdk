# Performance Considerations

Enclave code can run on one of two different Java virtual machines. Both can execute bytecode. One is a small, specialised
runtime called Avian. Avian is slow but can dynamically load bytecode, which some Java frameworks like to do. The other
is GraalVM Native Image (SubstrateVM). The latter compiles your entire program to native code ahead of time, erasing
any code that isn't used and optimising it as a whole. This can yield large speedups and memory usage improvements, at
the cost of being unable to dynamically load new classes.

The differences between the two runtimes is summarised by the table below:

|                          | Avian JVM                  | GraalVM Native Image |
|--------------------------|----------------------------|----------------------|
| Dynamic class loading    | :heavy_check_mark:         | :heavy_multiplication_x: |
| Fast ahead of time code  | :heavy_multiplication_x:   | :heavy_check_mark: |

The speedups from using Native Image can be significant. However, as the enclave environment is small the performance will
still be lower than with a regular HotSpot JVM. This table shows the performance difference and how they vary between
a variety of benchmarks taken from the [Computer Language Benchmarks Game](https://salsa.debian.org/benchmarksgame-team/benchmarksgame/).

!!! note
The "empty" benchmark is measuring the overhead of entering and exiting the enclave, without doing any
work. As entering/exiting triggers a variety of hardware mechanisms designed to block side channel attacks this is
naturally expensive relative to the cost of a regular function call, however, once the enclave is doing real work
this transition cost becomes less relevant.

| Benchmark     | Runtime    |        Score |          Error |  Units |
|---------------|------------|--------------|----------------|--------|
| empty         |     Avian  |    15970.313 | ±      837.783 |  ops/s |
| empty         |   GraalVM  |    51921.076 | ±     1697.024 |  ops/s |
| empty         |   HotSpot  | 49453365.793 | ±  3404118.758 |  ops/s |
| binary_trees  |     Avian  |       19.727 | ±        0.733 |  ops/s |
| binary_trees  |   GraalVM  |      454.061 | ±       31.089 |  ops/s |
| binary_trees  |   HotSpot  |     1758.980 | ±       79.428 |  ops/s |
| fannkuch      |     Avian  |        0.277 | ±        0.007 |  ops/s |
| fannkuch      |   GraalVM  |        4.181 | ±        0.024 |  ops/s |
| fannkuch      |   HotSpot  |        5.925 | ±        0.063 |  ops/s |
| fasta         |     Avian  |        1.692 | ±        0.010 |  ops/s |
| fasta         |   GraalVM  |        3.185 | ±        0.028 |  ops/s |
| fasta         |   HotSpot  |        4.022 | ±        0.127 |  ops/s |
| himeno        |     Avian  |        0.104 | ±        0.001 |  ops/s |
| himeno        |   GraalVM  |        0.179 | ±        0.004 |  ops/s |
| himeno        |   HotSpot  |        0.366 | ±        0.003 |  ops/s |
| mandelbrot    |     Avian  |        1.855 | ±        0.861 |  ops/s |
| mandelbrot    |   GraalVM  |        5.529 | ±        0.134 |  ops/s |
| mandelbrot    |   HotSpot  |        6.385 | ±        0.132 |  ops/s |
| nbody         |     Avian  |        0.359 | ±        0.004 |  ops/s |
| nbody         |   GraalVM  |        1.205 | ±        0.021 |  ops/s |
| nbody         |   HotSpot  |        1.279 | ±        0.017 |  ops/s |
| pidigits      |     Avian  |        0.747 | ±        0.020 |  ops/s |
| pidigits      |   GraalVM  |        9.941 | ±        0.185 |  ops/s |
| pidigits      |   HotSpot  |       24.722 | ±        0.301 |  ops/s |
| spectral_norm |     Avian  |        2.819 | ±        1.076 |  ops/s |
| spectral_norm |   GraalVM  |       11.923 | ±        0.274 |  ops/s |
| spectral_norm |   HotSpot  |       17.345 | ±        0.930 |  ops/s |

Higher scores are better. As you can see, GraalVM based enclaves are around 4x-12x faster than with
Avian, depending on the task. The performance hit overall of using an enclave is also highly dependent on what exactly
the code is doing (primarily, memory access patterns).