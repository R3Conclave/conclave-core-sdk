# Performance Considerations

When you use Conclave to build an enclave, Conclave compiles your enclave code into native machine code using GraalVM Native Image. 
During this process the compiler removes any code that is not used and performs optimisations on the generated codebase.

Entering and exiting an enclave causes a context switch which is fairly expensive in terms of CPU clock cycles.
Also, memory accesses inside an enclave can be slower, particularly if memory paging is required.
This means that performance inside an enclave will normally be lower than with a regular HotSpot JVM. This table shows 
the performance difference and how they vary between variety of benchmarks taken from the 
[Computer Language Benchmarks Game](https://salsa.debian.org/benchmarksgame-team/benchmarksgame/).

!!! note
	The "empty" benchmark is measuring the overhead of entering and exiting the enclave, without doing any
	work. As entering/exiting triggers a variety of hardware mechanisms designed to block side channel attacks this is
	naturally expensive relative to the cost of a regular function call, however, once the enclave is doing real work
	this transition cost becomes less relevant.

| Benchmark     | Environment|        Score |          Error |  Units |
|---------------|------------|--------------|----------------|--------|
| empty         |   Enclave  |    51921.076 | ±     1697.024 |  ops/s |
| empty         |   Host     | 49453365.793 | ±  3404118.758 |  ops/s |
| binary_trees  |   Enclave  |      454.061 | ±       31.089 |  ops/s |
| binary_trees  |   Host     |     1758.980 | ±       79.428 |  ops/s |
| fannkuch      |   Enclave  |        4.181 | ±        0.024 |  ops/s |
| fannkuch      |   Host     |        5.925 | ±        0.063 |  ops/s |
| fasta         |   Enclave  |        3.185 | ±        0.028 |  ops/s |
| fasta         |   Host     |        4.022 | ±        0.127 |  ops/s |
| himeno        |   Enclave  |        0.179 | ±        0.004 |  ops/s |
| himeno        |   Host     |        0.366 | ±        0.003 |  ops/s |
| mandelbrot    |   Enclave  |        5.529 | ±        0.134 |  ops/s |
| mandelbrot    |   Host     |        6.385 | ±        0.132 |  ops/s |
| nbody         |   Enclave  |        1.205 | ±        0.021 |  ops/s |
| nbody         |   Host     |        1.279 | ±        0.017 |  ops/s |
| pidigits      |   Enclave  |        9.941 | ±        0.185 |  ops/s |
| pidigits      |   Host     |       24.722 | ±        0.301 |  ops/s |
| spectral_norm |   Enclave  |       11.923 | ±        0.274 |  ops/s |
| spectral_norm |   Host     |       17.345 | ±        0.930 |  ops/s |

Higher scores are better. As you can see, the performance hit overall of 
using an enclave is highly dependent on what exactly the code is doing 
(primarily, memory access patterns).
