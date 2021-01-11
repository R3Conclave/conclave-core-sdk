package com.r3.conclave.common.internal

/**
 * Flags to specify what features are available in the current CPU.
 * (linux-sgx/common/inc/internal/se_cpu_feature_defs_ext.h)
 */
enum class CpuFeature(val feature: Long) {

    /**
     * The processor is a generic IA32 CPU.
     */
    GENERIC_IA32(0x00000001L),

    /**
     * Floating point unit is on-chip.
     */
    FPU(0x00000002L),

    /**
     * Conditional mov instructions are supported.
     */
    CMOV(0x00000004L),

    /**
     * The processor supports the MMX technology instruction set extensions to Intel Architecture.
     */
    MMX(0x00000008L),

    /**
     * The FXSAVE and FXRSTOR instructions are supported for fast save and restore of the floating point context.
     */
    FXSAVE(0x00000010L),

    /**
     * Indicates the processor supports the Streaming SIMD Extensions Instructions.
     */
    SSE(0x00000020L),

    /**
     * Indicates the processor supports the Streaming SIMD.
     * Extensions 2 Instructions.
     */
    SSE2(0x00000040L),

    /**
     * Indicates the processor supports the Streaming SIMD.
     * Extensions 3 Instructions. (PNI)
     */
    SSE3(0x00000080L),

    /**
     * The processor supports the Supplemental Streaming SIMD Extensions 3 instructions. (MNI)
     */
    SSSE3(0x00000100L),

    /**
     * The processor supports the Streaming SIMD Extensions 4.1 instructions.(SNI)
     */
    SSE4_1(0x00000200L),

    /**
     * The processor supports the Streaming SIMD Extensions 4.1 instructions.
     * (NNI + STTNI)
     */
    SSE4_2(0x00000400L),

    /**
     * The processor supports MOVBE instruction.
     */
    MOVBE(0x00000800L),


    /**
     * The processor supports POPCNT instruction.
     */
    POPCNT(0x00001000L),


    /**
     * The processor supports PCLMULQDQ instruction.
     */
    PCLMULQDQ(0x00002000L),

    /**
     * The processor supports instruction extension for encryption.
     */
    AES(0x00004000L),

    /**
     * The processor supports 16-bit floating-point conversions instructions.
     */
    F16C(0x00008000L),

    /**
     * The processor supports AVX instruction extension.
     */
    AVX(0x00010000L),

    /**
     * The processor supports RDRND (read random value) instruction.
     */
    RDRND(0x00020000L),

    /**
     * The processor supports FMA instructions.
     */
    FMA(0x00040000L),

    /**
     * The processor supports two groups of advanced bit manipulation extensions. - Haswell introduced, AVX2 related.
     */
    BMI(0x00080000L),

    /**
     * The processor supports LZCNT instruction (counts the number of leading zero bits). - Haswell introduced.
     */
    LZCNT(0x00100000L),

    /**
     * The processor supports HLE extension (hardware lock elision). - Haswell introduced.
     */
    HLE(0x00200000L),

    /**
     * The processor supports RTM extension (restricted transactional memory) - Haswell AVX2 related.
     */
    RTM(0x00400000L),

    /**
     * The processor supports AVX2 instruction extension.
     */
    AVX2(0x00800000L),

    /**
     * The processor supports AVX512 dword/qword instruction extension.
     */
    AVX512DQ(0x01000000L),

    /**
     * The processor supports the PTWRITE instruction.
     */
    PTWRITE(0x02000000L),

    /**
     * KNC instruction set.
     */
    KNCNI(0x04000000L),

    /**
     * AVX512 foundation instructions.
     */
    AVX512F(0x08000000L),

    /**
     * The processor supports uint add with OF or CF flags (ADOX, ADCX).
     */
    ADX(0x10000000L),

    /**
     * The processor supports RDSEED instruction.
     */
    RDSEED(0x20000000L),

    /**
     * AVX512IFMA52: vpmadd52huq and vpmadd52luq.
     */
    AVX512IFMA52(0x40000000L),

    /**
     * The processor is a full inorder (Silverthorne) processor.
     */
    FULL_INORDER(0x80000000L),

    /**
     * AVX512 exponential and reciprocal instructions.
     */
    AVX512ER(0x100000000L),

    /**
     * AVX512 prefetch instructions.
     */
    AVX512PF(0x200000000L),

    /**
     * AVX-512 conflict detection instructions.
     */
    AVX512CD(0x400000000L),

    /**
     * Secure Hash Algorithm instructions (SHA).
     */
    SHA(0x800000000L),

    /**
     * Memory Protection Extensions (MPX).
     */
    MPX(0x1000000000L),

    /**
     * AVX512BW - AVX512 byte/word vector instruction set.
     */
    AVX512BW(0x2000000000L),

    /**
     * AVX512VL - 128/256-bit vector support of AVX512 instructions.
     */
    AVX512VL(0x4000000000L),

    /**
     * AVX512VBMI:  vpermb, vpermi2b, vpermt2b and vpmultishiftqb.
     */
    AVX512VBMI(0x8000000000L),

    /**
     * AVX512_4FMAPS: Single Precision FMA for multivector(4 vector) operand.
     */
    AVX512_4FMAPS(0x10000000000L),

    /**
     * AVX512_4VNNIW: Vector Neural Network Instructions for multivector(4 vector) operand with word elements.
     */
    AVX512_4VNNIW(0x20000000000L),

    /**
     * AVX512_VPOPCNTDQ: 512-bit vector POPCNT instruction.
     */
    AVX512_VPOPCNTDQ(0x40000000000L),

    /**
     * AVX512_BITALG: vector bit algebra in AVX512.
     */
    AVX512_BITALG(0x80000000000L),

    /**
     * AVX512_VBMI2: additional byte, word, dword and qword capabilities.
     */
    AVX512_VBMI2(0x100000000000L),

    /**
     * GFNI: Galois Field New Instructions.
     */
    GFNI(0x200000000000L),

    /**
     * VAES: vector AES instructions.
     */
    VAES(0x400000000000L),

    /**
     * VPCLMULQDQ: Vector CLMUL instruction set.
     */
    VPCLMULQDQ(0x800000000000L),

    /**
     * AVX512_VNNI: vector Neural Network Instructions.
     */
    AVX512_VNNI(0x1000000000000L),

    /**
     * CLWB: Cache Line Write Back.
     */
    CLWB(0x2000000000000L),

    /**
     * RDPID: Read Processor ID.
     */
    RDPID(0x4000000000000L),

    /**
     * IBT - Indirect branch tracking.
     */
    IBT(0x8000000000000L),

    /**
     * Shadow stack.
     */
    SHSTK(0x10000000000000L),

    /**
     * Intel Software Guard Extensions.
     */
    SGX(0x20000000000000L),

    /**
     * Write back and do not invalidate cache.
     */
    WBNOINVD(0x40000000000000L),

    /**
     * Platform configuration - 1 << 55.
     */
    PCONFIG(0x80000000000000L);

    override fun toString(): String = name.replace('_', '.')
}