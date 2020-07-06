#include "vm_enclave_layer.h"

// The code below is from the GDB documentation and defines a macro that embeds
// a .debug_gdb_scripts section into the enclave elf file.
// This is then used to embed the name 'svmhelpers.py' which tells GDB what python
// helper file it should look for to help with debugging Graal/SVM based object files.
/* Note: The "MS" section flags are to remove duplicates.  */
#ifdef DEBUG
#define DEFINE_GDB_PY_SCRIPT(script_name) \
  asm("\
.pushsection \".debug_gdb_scripts\", \"MS\",@progbits,1\n \
.byte 1 /* Python */\n \
.asciz \"" script_name "\"\n \
.popsection \n \
");
DEFINE_GDB_PY_SCRIPT("svmhelpers.py")
#endif

void jni_throw(const char *msg, ...) {
  va_list va;
  va_start(va, msg);
  char buffer[512];
  int n = vsnprintf(buffer, sizeof(buffer), msg, va);
  // Print the cause of the error in case JVM throwing fails
  enclave_trace(buffer);

  throw_jvm_runtime_exception(buffer);
}

//
// This function is used to print to the console for debug and simulation enclaves. It
// has not effect on release enclaves
//
int enclave_print(const char *s, ...) {
#ifdef DEBUG_PRINT_OUTPUT
  va_list va;
  va_start(va, s);
  int res = vfprintf(stdout, s, va);
  va_end(va);
  return res;
#else
  return 0;
#endif
}

//
// Same as above but for trace output from the enclave stubs
//
int enclave_trace(const char *s, ...) {
#ifdef DEBUG_TRACE_OUTPUT
  va_list va;
  va_start(va, s);
  int res = vfprintf(stdout, s, va);
  va_end(va);
  return res;
#else
  return 0;
#endif
}
