#include <jni.h>

#ifndef _Included_com_r3_conclave_host_internal_GramineNative
#define _Included_com_r3_conclave_host_internal_GramineNative

#ifdef __cplusplus
extern "C" {
#endif

    /*
     * Class:     com_r3_conclave_host_internal_GramineNative
     * Method:    initQuoteDCAP
     * Signature: (Ljava/lang/String;)I
     */
    JNIEXPORT jint JNICALL Java_com_r3_conclave_host_internal_GramineNative_initQuoteDCAP
    (JNIEnv *, jclass, jstring);

    /*
     * Class:     com_r3_conclave_host_internal_GramineNative
     * Method:    getQuoteCollateral
     * Signature: ([BI)[Ljava/lang/Object;
     */
    JNIEXPORT jobjectArray JNICALL Java_com_r3_conclave_host_internal_GramineNative_getQuoteCollateral
    (JNIEnv *, jclass, jbyteArray, jint);


#ifdef __cplusplus
}
#endif

#endif
