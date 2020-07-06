//
// Stubs for functionality that would normally be provided by the operating system.
//

#if __GNUC__ >= 7
// The new GCC flags a warnings which is then escalated to an error if we check a parameter for
// being null when the header says it shouldn't be null. Unfortunately gettimeofday specifies
// it's OK to pass null as the first param, it just doesn't make sense so gets warned. Suppress
// this here, we don't have complex enough code in this file to care.
#pragma GCC diagnostic ignored "-Wnonnull-compare"
#endif

#include <cerrno>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <sys/time.h>
#include <sys/utsname.h>
#include <ctime>
#include <sys/resource.h>
#include <csignal>
#include <clocale>
#include <langinfo.h>
#include <sys/types.h>
#include <pwd.h>
#include <unistd.h>
#include <sys/stat.h>
#include <cstdlib>

extern "C" {

#undef stdout
#undef stderr
FILE *stdout = (FILE*) 0;
FILE *stderr = (FILE*) 1;

extern void throw_jvm_runtime_exception(const char *str);
extern void debug_print(const void *msg, int n);

int __vfprintf_chk(FILE *stream, int, const char *s, va_list va) {
    int res = 0;
    if (stream == stdout || stream == stderr) {
        char msg[512];
        res = vsnprintf((char *) &msg, sizeof(msg), s, va);
        debug_print(msg, res);
    } else {
        char msg[512];
        vsnprintf(msg, sizeof(msg), s, va);
        printf("STUB: Attempt to write to file %s: %s\n", (char*)stream, msg);
    }
    return res;
}

int vfprintf(FILE *stream, const char *s, va_list va) {
    return __vfprintf_chk(stream, 0, s, va);
}

int __printf_chk(int, const char *s, ...) {
    va_list va;
    va_start(va, s);
    int res = vfprintf(0, s, va);
    va_end(va);
    return res;
}

int printf(const char *s, ...) {
    va_list va;
    va_start(va, s);
    int res = vfprintf(stdout, s, va);
    va_end(va);
    return res;
}

void jni_throw(const char *msg, ...) {
    va_list va;
    va_start(va, msg);
    char buffer[512];
    int n = vsnprintf(buffer, sizeof(buffer), msg, va);
    debug_print(buffer, n); //< still log error cause, in case JVM throwing fails
    throw_jvm_runtime_exception(buffer);
}

// puts/fputs calls are often the result of the compiler converting printf calls to them statically, to skip string parsing overhead.
int puts(const char *str) {
    return printf("%s\n", str);
}

int fputs(const char *s, FILE *stream) {
    // Note that whilst puts adds a newline, fputs doesn't.
    return fprintf(stream, "%s", s);
}

int fputc(int c, FILE *stream) {
    return fprintf(stream, "%c", c);
}

int __fprintf_chk(FILE *f, int, const char *s, ...) {
    va_list va;
    va_start(va, s);
    int res = vfprintf(f, s, va);
    va_end(va);
    return res;
}

int fprintf(FILE *f, const char *s, ...) {
    va_list va;
    va_start(va, s);
    int res = vfprintf(f, s, va);
    va_end(va);
    return res;
}

int sprintf(char *str, const char *format, ...) {
    // Unsafe version of snprintf.
    va_list  va;
    va_start(va, format);
    int res = vsnprintf(str, 1024*1024, format, va);
    va_end(va);
    return res;
}

FILE *fopen(const char *path, const char *mode) {
    jni_throw("STUB: Attempt to open %s with mode %s\n", path, mode);
    return (FILE*) strdup(path);
}

int fclose(FILE*) {
    return 0;
}

int fflush(FILE*) {
    return 0;
}
//__asm__(".symver fflush,fflush@@GLIBC_2.2.5");

size_t fread(void*, size_t, size_t, FILE*) {
    jni_throw("STUB: fread\n");
    errno = -EPERM;
    return (size_t) -1;
}

size_t fwrite(const void*, size_t, size_t, FILE*) {
    jni_throw("STUB: fwrite\n");
    errno = -EPERM;
    return (size_t) -1;
}

ssize_t read(int, void*, size_t) {
    jni_throw("STUB: read\n");
    errno = -EPERM;
    return (ssize_t) -1;
}

ssize_t write(int fd, const void *buf, size_t count) {
    if (fd == 0 || fd == 1 || fd == 2) {
        if (fd != 2) {
            debug_print(buf, count);
        }
    } else {
        printf("STUB: write(%d)\n", fd);
    }
    return count;
}

int close(int) {
    return 0;
}

int open(const char*, int) {
    jni_throw("STUB: open\n");
    errno = -EPERM;
    return -1;
}

int dup2(int fd1, int fd2) {
    jni_throw("STUB: dup2(%d, %d)\n", fd1, fd2);
    return -1;
}

int gettimeofday(struct timeval *tv, struct timezone *tz) {
    if (tv) {
        tv->tv_sec = 0;
        tv->tv_usec = 0;
    }
    if (tz) {
        tz->tz_dsttime = 0;
        tz->tz_minuteswest = 0;
    }
    return 0;
}

void exit(int status) {
    jni_throw("STUB: exit(%d)\n", status);
    while(1);   // Avoid warning about a noreturn function that actually returns.
}

pid_t fork() {
    jni_throw("STUB: fork\n");
    errno = -ENOSYS;
    return -1;
}

int kill(pid_t, int) {
    jni_throw("Unresolved function: kill\n");
    errno = -EPERM;
    return -1;
}

// Just some dummy environment variables.
const char *_environ[] = { "HOME=/", "HOSTNAME=enclave", NULL };
char **environ = (char**) environ;

char *getenv(const char *varname) {
    // Could do a proper search here, but this isn't the right way to pass data into an enclave anyway.
    if (!strcmp(varname, "HOME")) {
        return (char*) "/";
    } else if (!strcmp(varname, "HOSTNAME")) {
        return (char*) "enclave";
    }  else {
        return NULL;
    }
}

int putenv(char*) {
    return 0;
}

char *getcwd(char *buf, size_t size) {
    if (!buf) {
        buf = (char*) malloc(size);
    }
    if (!buf) {
        return NULL;
    }
    strncpy(buf, "/", size);
    return buf;
}

int execvp(const char *file, char *const[]) {
    jni_throw("STUB: execvp(%s)", file);
    errno = -ENOSYS;
    return -1;
}

int waitpid(pid_t, int*, int) {
    jni_throw("STUB: waitpid");
    errno = -ENOSYS;
    return -1;
}

static struct utsname uname_data = {
        /*sysname:*/ "linux-sgx",
        /*nodename:*/  "enclave",
        /*release:*/ "1.0",
        /*version:*/ "1.0",
        /*machine:*/ "enclave",
#ifdef _GNU_SOURCE
        /*domainname:*/ "enclave"
#endif
};

int uname(struct utsname *buf) {
    if (buf) {
        memcpy(buf, &uname_data, sizeof(struct utsname));
    } else {
        errno = -EFAULT;
        return -1;
    }
    return 0;
}

int pipe(int[2]) {
    jni_throw("STUB: pipe()");
    errno = -ENOSYS;
    return -1;
}

int fcntl(int fd, int, ... ) {
    jni_throw("STUB: fcntl(%d)", fd);
    errno = -ENOSYS;
    return -1;
}

const unsigned short * * __ctype_b_loc() {
    jni_throw("STUB: __ctype_b_loc");
    return NULL;
}

static char ctime_buf[256] = {0};

char *ctime(const time_t *timep) {
    return ctime_r(timep, ctime_buf);
}

char *ctime_r(const time_t*, char *buf) {
    if (!buf) {
        errno = -EFAULT;
        return NULL;
    }
    *buf = '\0';
    printf("STUB: ctime_r");
    return NULL;
}

int getrlimit(int resource, struct rlimit *rlim) {
    if (resource == RLIMIT_NOFILE) {
        rlim->rlim_max = 64;   // Fake FD limit.
    } else {
        printf("STUB: getrlimit\n");
        rlim->rlim_max = 0;
    }
    return 0;
}


int sigemptyset(sigset_t*) {
    return 0;
}

int sigfillset(sigset_t*) {
    return 0;
}

int sigaddset(sigset_t*, int) {
    return 0;
}

int sigaction(int, const struct sigaction*, struct sigaction*) {
    return 0;
}

int sigprocmask(int, const sigset_t*, sigset_t*) {
    return 0;
}

char *setlocale(int, const char *locale) {
    if (locale && *locale != '\0')
        printf("STUB: setlocale(%s)\n", locale);
    return (char*) "C";
}

char *strcpy(char *dest, const char *src) {
    return strncpy(dest, src, strlen(src) + 1);
}

char *stpcpy(char *dest, const char *src) {
    strcpy(dest, src);
    return dest + strlen(src);
}

char *nl_langinfo(nl_item item) {
    if (item != CODESET) {
        printf("STUB: nl_langinfo(%d)\n", item);
    }
    return (char*) "";
}

uid_t getuid() {
    return 1;   // Not zero, don't tell the app it's root.
}

static struct passwd passwd_info = {
   (char*) "enclave",       /* username */
   (char*) "",     /* user password */
   1,        /* user ID */
   1,        /* group ID */
   (char*) "",      /* user information */
   (char*) "/",        /* home directory */
   (char*) "there is no shell"      /* shell program */
};

struct passwd *getpwuid(uid_t uid) {
    if (uid != 1)
        printf("STUB: getpwuid(%d)\n", uid);
    return &passwd_info;
}

void tzset() {
}

long sysconf(int name) {
    switch (name) {
        case _SC_NPROCESSORS_ONLN:
            return 1;  // 1 active processor.
        case _SC_PAGESIZE:
            return 4096;
        default:
            printf("STUB: sysconf(%d)\n", name);
    }
    return -1;
}

char *realpath(const char *path, char *resolved_path) {
    if (!strcmp(path, "/."))
        return strcpy(resolved_path, "/");
    else if (!strncmp(path, "/[", 2) || !strcmp(path, "/avian-embedded/javahomeJar/lib/logging.properties") || !strcmp(path, "/avian-embedded/javahomeJar/lib"))
        return strcpy(resolved_path, path + 1);
    else {
        printf("STUB: realpath(%s)\n", path);
        return NULL;
    }
}

int stat64(const char *pathname, struct stat64*) {
    if (pathname[0] == '[') {
        // stat64("[embedded_foo_jar]")
        return -1;
    }
    jni_throw("STUB: stat64(%s)\n", pathname);
    return -1;
}

int access(const char *pathname, int) {
    jni_throw("STUB: access(%s)\n", pathname);
    errno = -EPERM;
    return -1;
}


}
