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

void raiseException(JNIEnv *jniEnv, const char* message, const char* cls) {
    auto exceptionClass = jniEnv->FindClass(cls);
    if (exceptionClass == nullptr) {
        throw std::runtime_error("Cannot find exception class");
    }
    jniEnv->ThrowNew(exceptionClass, message);
}

void raiseException(JNIEnv *jniEnv, const char *message) {
    raiseException(jniEnv, message, "java/lang/RuntimeException");
}
