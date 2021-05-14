#pragma once

#include <jni.h>
#include <exception>

void raiseException(JNIEnv *jniEnv, const char *message);
void raiseException(JNIEnv *jniEnv, const char* message, const char* cls);
void checkJniException(JNIEnv *jniEnv);

template<class T>
struct JniPtr {
    jbyteArray const array;
    jbyte * const rawPtr;
    T * const ptr;
    JNIEnv * const jniEnv;
    jint releaseMode = JNI_ABORT;
    JniPtr() = delete;
    JniPtr(const JniPtr&) = delete;
    JniPtr(JNIEnv *jniEnv, jbyteArray array) :
            array(array),
            rawPtr(array == nullptr ? nullptr : jniEnv->GetByteArrayElements(array, nullptr)),
            ptr(rawPtr == nullptr ? nullptr : reinterpret_cast<T *>(rawPtr)),
            jniEnv(jniEnv) {
    }
    ~JniPtr() {
        if (rawPtr != nullptr) {
            jniEnv->ReleaseByteArrayElements(array, rawPtr, releaseMode);
        }
    }
    int size() const {
        return array == nullptr ? 0 : jniEnv->GetArrayLength(array);
    }
};

template<typename T>
class JniScopedRef {
public:
    JniScopedRef(T &&value, JNIEnv *jniEnv)
            : jniRef_(value), jniEnv_(jniEnv)
    {}

    T& value() {
        return jniRef_;
    }

    ~JniScopedRef() {
        jniEnv_->DeleteLocalRef(jniRef_);
    }

private:
    JniScopedRef(const JniScopedRef&) = delete;
    T jniRef_;
    JNIEnv *jniEnv_;
};

struct JniString {
    JniString() = delete;
    JniString(const JniString&) = delete;
    jstring const string;
    const char * const c_str;
    JNIEnv * const jniEnv;
    JniString(JNIEnv *jniEnv, jstring string) :
            string(string),
            c_str(string == nullptr ? nullptr : jniEnv->GetStringUTFChars(string, nullptr)),
            jniEnv(jniEnv) {
    }
    ~JniString() {
        if (c_str != nullptr) {
            jniEnv->ReleaseStringUTFChars(string, c_str);
        }
    }
};

struct JNIException: std::exception {
    static constexpr const char * c_msg = "JNI exception occurred";
    const char* what() const noexcept override {
        return c_msg;
    }
};

