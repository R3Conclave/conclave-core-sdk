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
#include "conclave-timespec.h"

// Define STUB macros for functions that are needed to satisfy the linker but
// throw an exception or are just ignored if called. Each stub function is 
// specified in a section in the relevant implementation file
#ifdef __cplusplus
#define STUB(x) extern "C" void x() { enclave_trace(#x); throw_jvm_runtime_exception(#x); abort(); }
#else
#define STUB(x) void x() { enclave_trace(#x); throw_jvm_runtime_exception(#x); abort(); }
#endif

#ifdef __cplusplus
extern "C" {
#endif

    //  Function called by Kotlin Enclave to pass some filesystem related parameters and to allow    
    //    the Enclave to store the JVM object
    JNIEXPORT void JNICALL Java_com_r3_conclave_enclave_internal_Native_setupFileSystems(JNIEnv *jniEnv,
											 jobject,
											 jlong in_memory_size,											
											 jlong persistent_size,
											 jstring in_memory_mount_path_in,
											 jstring persistent_mount_path_in,
											 jbyteArray encryption_key_in);

    // Debug print and trace functions for stubs and debug output
    extern void debug_print_enclave(const char* msg, int length, bool allow_debug_print);
    int enclave_print(const char *s, ...);
    int enclave_trace(const char *s, ...);

    // Definitions of types used by the stubs
#define FILE SGX_FILE

#define NS_PER_SEC (1000 * 1000 * 1000)

    typedef int pid_t;
    typedef int sigset_t;
    typedef int uid_t;
    typedef unsigned int gid_t;
    typedef unsigned int mode_t;
    typedef unsigned long long off64_t;
    
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

    // From <dirent.h>
    typedef uint64_t           ino_t;
    typedef int64_t            off_t;
    typedef unsigned long int           ino64_t;
    typedef long int                    doff64_t;

#pragma pack(push, 1)
    struct dirent {
        uint64_t          d_ino;       /* inode number */
        int64_t           d_off;       /* offset to the next dirent */
        unsigned short    d_reclen;    /* length of this record */
        unsigned char     d_type;      /* type of file; not supported
					  by all file system types */
        char              d_name[256]; /* filename */
    };

    struct dirent64 {
        uint64_t          d_ino;       /* inode number */
        int64_t           d_off;       /* offset to the next dirent */
        unsigned short    d_reclen;    /* length of this record */
        unsigned char     d_type;      /* type of file; not supported
					  by all file system types */
        char              d_name[256]; /* filename */
    };
#pragma pack(pop)

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
    int unlink(const char* pathname);
    int rmdir(const char* pathname);

    // sys/stat.h
    int stat64(const char *pathname, struct stat64*);
    int lstat64(const char *path_name, struct stat64* stat_buf);
    int __fxstat64(int ver, int fildes, struct stat64* stat_buf);
    int __xstat(int, const char*, struct stat*);
    int __fxstat(int, int , struct stat*);
    int __lxstat(int, const char*, struct stat*);
    int __lxstat64(int, const char*, struct stat64*);

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

    // sys/sendfile.h
    ssize_t sendfile64(int out_fd, int in_fd, off64_t * offset, size_t count);

    // sys/.h
    int fsetxattr(int fd, const char *name, const void *value, size_t size, int flags);

    ssize_t flistxattr(int fd, char *list, size_t size);

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

    // dlfcn.h
    typedef struct {
	const char *dli_fname;  /* Pathname of shared object that contains address */
	void       *dli_fbase;  /* Base address at which shared object is loaded */
	const char *dli_sname;  /* Name of symbol whose definition overlaps addr */
	void       *dli_saddr;  /* Exact address of symbol named in dli_sname */
    } Dl_info;

    // stdio.h
    FILE *fopen_impl(const char *path, const char *mode, int& err);
    int remove_impl(const char* pathname, int& err);

    // fcntl.h    
    int open_impl(const char* __file, int oflag, int& err);
    int lstat_impl(const char* pathname, struct stat* statbuf, int& err);
    int lstat64_impl(const char* pathname, struct stat64* statbuf, int& err);

    off64_t lseek64_impl(int fd, off64_t offset, int whence);
    ssize_t read_impl(int fd, void* buf, size_t count);
    ssize_t pread_impl(int fd, void* buf, size_t count, off_t offset);
    int close_impl(int fildes);
    ssize_t write_impl(int fd, const void *buf, size_t count);
    ssize_t pwrite_impl(int fd, const void *buf, size_t count, off_t offset);
    int rename_impl(const char *oldpath, const char *newpath, int& err);
    //sys/socket.h
    int socketpair_impl(int domain, int type, int protocol, int sv[2]);

    // unistd.h
    int dup2_impl(int oldfd, int newfd);
    int unlink_impl(const char* pathname, int& err);
    int rmdir_impl(const char* pathname, int& err);

    int access_impl(const char* pathname, int mode, int& err);
    int ftruncate_impl(int fd, off_t offset, int& err);

    int fchown_impl(int fd, uid_t owner, gid_t group, int& err);
    int fchmod_impl(int fd, mode_t mode, int& err);

    // dirent.h
    void* opendir_impl(const char* dirname, int& err);
    struct dirent64* readdir64_impl(void* dirp, int& err);
    struct dirent* readdir_impl(void* dirp, int& err);
    int closedir_impl(void* dirp, int& err);

    int utimes_impl(const char *filename, const struct timeval times[2], int& err);

#ifdef __cplusplus
}
#endif
