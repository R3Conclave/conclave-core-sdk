package com.r3.conclave.host.internal;

/**
 * Do not use this class directly, use NativeShared instead.
 * The required native libraries are loaded by NativeShared.
 */
class NativeSharedInternal {
    public static native void checkPlatformEnclaveSupport(boolean enableSupport);
    public static native void enablePlatformHardwareEnclaveSupport();
    public static native long getCpuFeatures();
}
