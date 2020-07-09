//
// OS Stubs for file functions
//
#pragma once 

#include <pthread.h>
#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <errno.h>
#include <time.h>
#include <sgx_tprotected_fs.h>
#include <sgx_trts.h>
#include <sgx_errors.h>
#include "jvm_t.h"
#include "jni_utils.h"

// Define STUB macros for functions that are needed to satisfy the linker but
// throw an exception or are just ignored if called. Each stub function is 
// specified in a section inthe relevant implementation file
#ifdef __cplusplus
#define STUB(x) extern "C" void x() { debug_print(#x, strlen(#x)); throw_jvm_runtime_exception(#x); abort(); }
#define STUB_NO_ABORT(x) extern "C" int x() { debug_print(#x, strlen(#x)); return 0; }
#else
#define STUB(x) void x() { debug_print(#x, strlen(#x)); throw_jvm_runtime_exception(#x); abort(); }
#define STUB_NO_ABORT(x) int x() { debug_print(#x, strlen(#x)); return 0; }
#endif

#ifdef __cplusplus
extern "C" {
#endif

// Debug print and trace functions for stubs and debug output
int enclave_print(const char *s, ...);
int enclave_trace(const char *s, ...);

// Definitions of types used by the stubs
#define FILE SGX_FILE

typedef int pid_t;
typedef int sigset_t;
typedef int uid_t;

#undef stdout
#undef stderr
extern FILE *stdout;
extern FILE *stderr;

// From <sys/time.h>
struct timeval {
   time_t      tv_sec;   // Number of whole seconds of elapsed time
   long int    tv_usec;  // Number of microseconds of rest of elapsed time minus tv_sec. Always less than one million
};
typedef int clockid_t;

struct timezone {
   int tz_minuteswest;
   int tz_dsttime;
};

// From <sys/stat.h>
struct stat64 {
	unsigned long long	st_dev;
	unsigned char	__pad0[4];

	unsigned long	__st_ino;

	unsigned int	st_mode;
	unsigned int	st_nlink;

	unsigned long	st_uid;
	unsigned long	st_gid;

	unsigned long long	st_rdev;
	unsigned char	__pad3[4];

	long long	st_size;
	unsigned long	st_blksize;

	/* Number 512-byte blocks allocated. */
	unsigned long long	st_blocks;

	unsigned long	st_atime;
	unsigned long	st_atime_nsec;

	unsigned long	st_mtime;
	unsigned int	st_mtime_nsec;

	unsigned long	st_ctime;
	unsigned long	st_ctime_nsec;

	unsigned long long	st_ino;
};
#define	S_IFMT	0170000

// From <sys/socket.h>
typedef unsigned int socklen_t;

// From <sys/resource.h>
typedef unsigned long long rlim_t;

struct rlimit {
    rlim_t rlim_cur;  /* Soft limit */
    rlim_t rlim_max;  /* Hard limit (ceiling for rlim_cur) */
};

// From <langinfo.h>
typedef unsigned int nl_item;
#define CODESET                     14

// From <resource.h>
#define RLIMIT_NOFILE               7

// From <confname.h>
#define _SC_PAGESIZE                30
#define _SC_NPROCESSORS_ONLN        84
#define _SC_PHYS_PAGES              85

// From <signal.h>
typedef void (*sighandler_t)(int);

extern void throw_jvm_runtime_exception(const char *str);
extern void jni_throw(const char *msg, ...);

/////////////////////////////////////////////////////////////////////////
// Standard C Runtime Library functions

// stdio.h
int printf(const char *s, ...);
int vfprintf(FILE *stream, const char *s, va_list va);
int fprintf(FILE *f, const char *s, ...);
int puts(const char *str);
int fputs(const char *s, FILE *stream);
int fputc(int c, FILE *stream);
int sprintf(char *str, const char *format, ...);
FILE *fopen(const char *path, const char *mode);
int fclose(FILE*);
int fflush(FILE*);
size_t fread(void*, size_t, size_t, FILE*);
size_t fwrite(const void*, size_t, size_t, FILE*);

// stdlib.h
void exit(int status);
char *getenv(const char *varname);
int putenv(char*);
char *realpath(const char *path, char *resolved_path);

// string.h
char *strcpy(char *dest, const char *src);
char *stpcpy(char *dest, const char *src);

// time.h
char *ctime(const time_t *timep);
char *ctime_r(const time_t*, char *buf);
void tzset();

// locale.h
char *setlocale(int, const char *locale);

// langinfo.h
char *nl_langinfo(nl_item item);

// ctype.h
const unsigned short * * __ctype_b_loc();

// libc.h
int __vfprintf_chk(FILE *stream, int, const char *s, va_list va);
int __fprintf_chk(FILE *f, int, const char *s, ...);

/////////////////////////////////////////////////////////////////////////
// Posix functions

// unistd.h
int access(const char *pathname, int);
ssize_t read(int fh, void* p, size_t len);
ssize_t write(int fd, const void *buf, size_t count);
int close(int);
int dup(int oldfd);
int dup2(int fd1, int fd2);
pid_t fork();
char *getcwd(char *buf, size_t size);
int pipe(int[2]);
int execvp(const char *file, char *const[]);
long sysconf(int name);
uid_t getuid();
unsigned int sleep(unsigned int seconds);
long syscall(long number, ...);

// sys/stat.h
int stat64(const char *pathname, struct stat64*);
int __fxstat64(int ver, int fildes, struct stat64 * stat_buf);
int __xstat(int, const char*, struct stat*);
int __fxstat(int, int , struct stat*);
int __lxstat(int, const char*, struct stat*);

// fcntl.h
int fcntl(int fd, int, ... );
int open(const char*, int);
int open64 (const char *__file, int , ...);

// sys/time.h
int gettimeofday(struct timeval *tv, struct timezone *tz);

// signal.h
int kill(pid_t, int);
int sigemptyset(sigset_t*);
int sigfillset(sigset_t*);
int sigaddset(sigset_t*, int);
int sigaction(int, const struct sigaction*, struct sigaction*);
int sigprocmask(int, const sigset_t*, sigset_t*);
sighandler_t signal(int signum, sighandler_t handler);
int __libc_current_sigrtmax(void);

// sigthread.h
int pthread_kill(pthread_t thread, int sig);

// sys/wait.h
int waitpid(pid_t, int*, int);

// sys/utsname.h
int uname(struct utsname *buf);

// sys/resource.h
int getrlimit(int resource, struct rlimit *rlim);
int setrlimit(int resource, const struct rlimit *rlim);

// pwd.h
struct passwd *getpwuid(uid_t uid);

// netdb.h
int getnameinfo(const struct sockaddr *addr, socklen_t addrlen,
                       char *host, socklen_t hostlen,
                       char *serv, socklen_t servlen, int flags);
int getaddrinfo(const char *node, const char *service,
                const struct addrinfo *hints,
                struct addrinfo **res);
void freeaddrinfo(struct addrinfo *res);
const char *gai_strerror(int errcode);

#ifdef __cplusplus
}
#endif
