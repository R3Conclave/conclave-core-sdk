#include <jni_utils.h>
#include <stdexcept>
#include <string>
#include <cstring>
#include <jni.h>
#include <cstdio>

extern "C" {
int printf(const char *s, ...);
}

void checkJniException(JNIEnv *jniEnv) {
    if (jniEnv->ExceptionCheck() == JNI_TRUE) {
        throw JNIException();
    }
}

void raiseException(JNIEnv *jniEnv, const char *message) {
    auto exceptionClass = jniEnv->FindClass("java/lang/RuntimeException");
    if (exceptionClass == nullptr) {
        throw std::runtime_error("Cannot find exception class");
    }
    jniEnv->ThrowNew(exceptionClass, message);
}
