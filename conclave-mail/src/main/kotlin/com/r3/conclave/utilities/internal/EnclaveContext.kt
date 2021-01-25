package com.r3.conclave.utilities.internal

// TODO This could be achievede with stack walking, however that's expensive in Java 8. Investigate whether SVM can resolve
//      sun.reflect.Reflection.getCallerClass AOT.
interface EnclaveContext {
    fun isInsideEnclave(): Boolean

    companion object {
        private var instance: EnclaveContext? = null

        /**
         * Return true iff inside an enclave environment.
         */
        fun isInsideEnclave(): Boolean = instance?.isInsideEnclave() ?: false
    }
}
