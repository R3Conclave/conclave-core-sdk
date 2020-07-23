#if __GNUC__ >= 7
#pragma GCC diagnostic ignored "-Wbuiltin-declaration-mismatch"
#endif

extern "C" {

extern void debug_print_enclave(const char *message, int length);
extern void abort();
extern void throw_jvm_runtime_exception(const char *str);

#define STUB(x) void x() { debug_print_enclave(#x, sizeof(#x) - 1); throw_jvm_runtime_exception(#x); abort(); }

STUB(accept);
STUB(chmod);
STUB(closedir);
STUB(connect);
STUB(dlopen);
STUB(fgets);
STUB(fstat);
STUB(fstat64);
STUB(fsync);
STUB(ftruncate);
STUB(ftruncate64);
STUB(getegid);
STUB(geteuid);
STUB(getgid);
STUB(gethostname);
STUB(getsockname);
STUB(getsockopt);
STUB(ioctl);
STUB(listen);
STUB(lseek);
STUB(lseek64);
STUB(lstat);
STUB(madvise);
STUB(mincore);
STUB(mkdir);
STUB(open64);
STUB(opendir);
STUB(pathconf);
STUB(readdir64_r);
STUB(readlink);
STUB(recv);
STUB(remove);
STUB(rename);
STUB(send);
STUB(setsockopt);
STUB(shutdown);
STUB(socket);
STUB(stat);
STUB(statvfs64);
STUB(strcat);
STUB(timezone);
STUB(umask);
STUB(utimes);

}
