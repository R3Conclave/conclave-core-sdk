#include <singleton_jvm.h>

extern "C" {

extern void debug_print_enclave(const char* msg, int length);
// Raise a Java exception in the JVM singleton instance and print the java stack trace

void throw_jvm_runtime_exception(const char *message) {
  static const char* err_msg = "Error occurred in a thread spawn by Avian";
  auto jniEnv = r3::conclave::Jvm::instance().attach_current_thread();
  if (!jniEnv) {
    debug_print_enclave(err_msg, strlen(err_msg));
    debug_print_enclave(message, strlen(message));
    return;
  }
  auto exceptionClass = jniEnv->FindClass("java/lang/RuntimeException");
  if (exceptionClass == nullptr) {
      throw std::runtime_error("Cannot find exception class");
  }
  jniEnv->ThrowNew(exceptionClass, message);
  jniEnv->ExceptionDescribe();
}

}
