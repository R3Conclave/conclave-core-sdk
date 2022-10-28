# Performance Considerations

When you use Conclave to build an enclave, Conclave compiles your enclave code into native machine code using GraalVM Native Image. 
During this process the compiler removes any code that is not used and performs optimisations on the generated codebase.

Entering and exiting an enclave causes a context switch which is fairly expensive in terms of CPU clock cycles.
Also, memory accesses inside an enclave can be slower, particularly if memory paging is required.
This means that performance inside an enclave will normally be lower than with a regular HotSpot JVM. This table shows 
the performance difference and how they vary between variety of benchmarks taken from the 
[Computer Language Benchmarks Game](https://salsa.debian.org/benchmarksgame-team/benchmarksgame/).

The configuration used to perform the benchmarks were:
1) Warmup iteration: 10 
2) Number of iteration for each benchmark: 100
3) Azure machine series: Standard DC4s v3 (4 vcpus - Intel(R) Xeon(R) Platinum 8370C CPU @ 2.80GHz, 32 GiB memory)
4) Operating system: Ubuntu 20.04.5 LTS 
5) Conclave version: 1.3

!!! note
	The "empty" benchmark is measuring the overhead of entering and exiting the enclave, without doing any
	work. As entering/exiting triggers a variety of hardware mechanisms designed to block side channel attacks this is
	naturally expensive relative to the cost of a regular function call, however, once the enclave is doing real work
	this transition cost becomes less relevant.

| Benchmark    | SGX   | Score (ops/s) | Score Delta (%) | Error (ops/s)  | Error (%) |
|--------------|-------|---------------|-----------------|----------------|-----------|
| empty        | 	Off	| 24908588.801	| N.A             | ±42949.048     | ±0.17     |
| empty        | 	On	|    15659.463  | -99.94          | ± 1247.194	   | ±7.96     |
| binary_trees | 	Off	|     1495.911	| N.A             | ±    8.387     | ±0.56     |  
| binary_trees | 	On	|      675.869  | -54.82          | ±   10.270     | ±1.52     |
| fannkuch     | 	Off	|        5.103  | N.A             | ±    0.011     | ±0.22     |
| fannkuch     | 	On	|        4.600  | -9.86           | ±    0.003     | ±0.07     |
| fasta        | 	Off	|        3.536  | N.A             | ±    0.003     | ±0.08     |
| fasta        | 	On	|        2.855  | -19.26          | ±    0.002     | ±0.07     |
| himeno       | 	Off	|        0.394  | N.A             | ±    0.002     | ±0.51     |
| himeno       | 	On	|        0.266  | -3.49%          | ±    0.003     | ±1.13     |
| mandelbrot   | 	Off	|        5.045  | N.A             | ±    0.007     | ±0.14     |
| mandelbrot   | 	On	|        4.523  | -10.35          | ±    0.003     | ±0.07     |
| nbody        | 	Off	|        1.385  | N.A             | ±    0.002     | ±0.14     |
| nbody        | 	On	|        1.411  | 1.88            | ±    0.002     | ±0.14     |
| pidigits     | 	Off	|       19.449  | N.A             | ±    0.035     | ±0.18     |
| pidigits     | 	On	|       14.950  | -23.13	      | ±    0.050     | ±0.33     |
| spectral_norm| 	Off	|       12.818	| N.A             | ±    0.019     | ±0.15     |
| spectral_norm| 	On	|        9.677  | -24.50	      | ±    0.009     | ±0.09     |

Higher scores are better. As you can see, the performance hit overall of 
using an enclave is highly dependent on what exactly the code is doing 
(primarily, memory access patterns). The score delta shows the difference in percentage between the 
same benchmark with SGX turned on and off. As expected there is a performance hit when SGX is used.
