package com.r3.conclave.host.internal;

/**
 * The Enclave JNI. We don't use System.loadLibrary, but instead rely on our custom dlsym to find the relevant symbols.
 */
public class GramineNative {

    public static native int initQuoteDCAP(String bundlePath);

    public static native Object[] getQuoteCollateral(byte[] fmspc, int pck);
}
