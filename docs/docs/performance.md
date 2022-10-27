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
3) Azure machine series: Standard DC4s v3 (4 vcpus, 32 GiB memory)
4) Operating systems: Ubuntu 20.04.5 LTS 
5) Conclave version: 1.4

!!! note
	The "empty" benchmark is measuring the overhead of entering and exiting the enclave, without doing any
	work. As entering/exiting triggers a variety of hardware mechanisms designed to block side channel attacks this is
	naturally expensive relative to the cost of a regular function call, however, once the enclave is doing real work
	this transition cost becomes less relevant.

| Benchmark    | SGX   | Score (ops/s) | Score Delta (%) | Error (ops/s)  | Error (%) |
|--------------|-------|---------------|-----------------|----------------|-----------|
| empty        | 	Off	| 24892554.58	| N.A.            | ±7868.489      | ±0.03%    |
| empty        | 	On	| 23388.662	    | -99.91%         | ±846.568	   | ±3.62%    |
| binary_trees | 	Off	| 1508.331		| N.A.            | ±1.312	       | ±0.09%    |  
| binary_trees | 	On	| 669.865	    | -55.59%         | ±8.807	       | ±1.31%    |
| fannkuch     | 	Off	| 5.092		    | N.A.            | ±0.008	       | ±0.16%    |
| fannkuch     | 	On	| 4.588	        | -9.90%          | ±0.005	       | ±0.11%    |
| fasta        | 	Off	| 3.516	        | N.A.            | ±0.003	       | ±0.09%    |
| fasta        | 	On	| 2.847	        | -19.03%         | ±0.002	       | ±0.07%    |
| himeno       | 	Off	| 0.398	        | N.A.            | ±0.001	       | ±0.25%    |
| himeno       | 	On	| 0.264	        | -33.67%         | ±0.003	       | ±1.14%    |
| mandelbrot   | 	Off	| 5.039	        | N.A.            | ±0.001	       | ±0.02%    |
| mandelbrot   | 	On	| 4.525	        | -10.20%	      | ±0.001	       | ±0.02%    |
| nbody        | 	Off	| 1.384	        | N.A.            | ±0.002	       | ±0.14%    |
| nbody        | 	On	| 1.412	        | 2.02%	          | ±0.001	       | ±0.07%    |
| pidigits     | 	Off	| 19.434	    | N.A.            | ±0.011	       | ±0.06%    |
| pidigits     | 	On	| 14.981	    | -22.91%	      | ±0.026	       | ±0.17%    |
| spectral_norm| 	Off	| 12.843		| N.A.            | ±0.004	       | ±0.03%    |
| spectral_norm| 	On	| 9.688	        | -24.57%	      | ±0.004	       | ±0.04%    |

Higher scores are better. As you can see, the performance hit overall of 
using an enclave is highly dependent on what exactly the code is doing 
(primarily, memory access patterns).
