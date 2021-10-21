//
// OS Stubs for functions declared in stdio.h
//
#include "vm_enclave_layer.h"
#include "file_manager.h"
#include "unistd.h"

typedef unsigned int gid_t;

//////////////////////////////////////////////////////////////////////////////
// Stub functions to satisfy the linker
STUB(getegid);
STUB(geteuid);
STUB(getgid);
STUB(gethostname);
STUB(lseek);
STUB(pathconf);
STUB(readlink);
STUB(_exit);
STUB(fchown);
STUB(lchown);
STUB(chown);
STUB(fchmod);
STUB(symlink);
STUB(__xmknod);
STUB(link);

extern "C" {

// These two symbols are defined as parameters to the linker when running native-image.
// __ImageBase is a symbol that is at the address at the base of the image. __HeapSize is
// a symbol at the fake address of &__ImageBase + size of the heap ad defined in the enclave
// configuration. We can subtract one address from the other to get the actual heap size.
extern unsigned long __HeapSize;
extern unsigned long __ImageBase;
unsigned long heap_size = (unsigned long)((unsigned long long)&__HeapSize - (unsigned long long)&__ImageBase);

int access(const char *pathname, int mode) {
    enclave_trace("access(%s)\n", pathname);
    
    if (conclave::FileManager::instance().exists(pathname)) {
        return 0;
    } else {
        int err = 0;
        const int res = access_impl(pathname, mode, err);
	errno = err;
	return res;
    }
}

ssize_t pread(int fd, void *buf, size_t count, off_t offset) {
    conclave::File* file = conclave::FileManager::instance().fromHandle(fd);
    if (file) {
        enclave_trace("pread(%s)\n", file->filename().c_str());
        return file->read((unsigned char*)buf, count, offset);
    }

    ssize_t read = pread_impl(fd, buf, count, offset);
    if (read != -1) {
        return (ssize_t)read;
    }

    errno = -EPERM;
    enclave_trace("pread()\n");
    return (ssize_t)-1;
}

ssize_t pwrite(int fd, const void *buf, size_t count, off_t offset) {
    conclave::File* file = conclave::FileManager::instance().fromHandle(fd);
    if (file) {
        enclave_trace("pwrite(%s)\n", file->filename().c_str());
        return file->write((const unsigned char*)buf, count, offset);
    }
    enclave_trace("pwrite(%d)\n", fd);
    return pwrite_impl(fd, buf, count, offset);
}

ssize_t pread64(int fd, void *buf, size_t count, off_t offset) {
    return pread(fd, buf, count, offset);
}

ssize_t pwrite64(int fd, const void *buf, size_t count, off_t offset) {
    return pwrite(fd, buf, count, offset);
}

ssize_t read(int fd, void* buf, size_t count) {
    conclave::File* file = conclave::FileManager::instance().fromHandle(fd);
    if (file) {
        enclave_trace("read(%s)\n", file->filename().c_str());
        return file->read((unsigned char*)buf, count, 0);
    }

    ssize_t read = read_impl(fd, buf, count);
    if (read != -1) {
        return (ssize_t)read;
    }

    errno = -EPERM;
    enclave_trace("read()\n");
    return (ssize_t)-1;
}

ssize_t write(int fd, const void *buf, size_t count) {
    conclave::File* file = conclave::FileManager::instance().fromHandle(fd);
    if (file) {
        enclave_trace("write(%s)\n", file->filename().c_str());
        return file->write((const unsigned char*)buf, count, 0);
    }
    enclave_trace("write(%d)\n", fd);
    return write_impl(fd, buf, count);
}

int close(int handle) {
    enclave_trace("close\n");

    if (!conclave::FileManager::instance().close(handle)) {
        return 0;
    }
    return close_impl(handle);
}

int chdir(const char* path) {
    enclave_trace("chdir\n");
    errno = ENOENT;
    return -1;
}

int dup(int oldfd) {
    enclave_trace("dup\n");
    return -1;
}

int dup2(int fd1, int fd2) {
    enclave_trace("dup2\n");
    return dup2_impl(fd1, fd2);
}

pid_t fork() {
    enclave_trace("fork\n");
    errno = -ENOSYS;
    return -1;
}

char *getcwd(char *buf, size_t size) {
    enclave_trace("getcwd\n");
    const char* root = "/";
    const size_t minimumSize = strlen(root) + 1;

    if (size == 0 && buf != nullptr) {
        errno = EINVAL;
        return nullptr;
    }

    /*
        As an extension to the POSIX.1-2001 standard, glibc's getcwd() allocates the buffer dynamically using malloc(3) if buf is NULL. In this case, the allocated buffer
        has the length size unless size is zero, when buf is allocated as big as necessary. The caller should free(3) the returned buffer.
    */
    const size_t actualSize = size >= minimumSize ? size : minimumSize;
    if (!buf) {
        buf = (char*) calloc(actualSize, sizeof(char));
        if (!buf) {
            errno = ENOMEM;
            return nullptr;
        }
    }
    else if (size < minimumSize) {
        /*
            If the length of the absolute pathname of the current working directory, including the terminating null byte, exceeds size bytes, NULL is returned, and errno is set
            to ERANGE; an application should check for this error, and allocate a larger buffer if necessary.
        */
        errno = ERANGE;
        return nullptr;
    }
    strncpy(buf, root, actualSize);
    return buf;
}

int execvp(const char *file, char *const[]) {
    enclave_trace("execvp\n");
    errno = -ENOSYS;
    return -1;
}

int pipe(int[2]) {
    enclave_trace("pipe\n");
    errno = -ENOSYS;
    return -1;
}

long sysconf(int name) {
    switch (name) {
        case _SC_NPROCESSORS_ONLN:
            enclave_trace("sysconf(_SC_NPROCESSORS_ONL)\n");
            return 1;  // 1 active processor.
        case _SC_PAGESIZE:
            enclave_trace("sysconf(_SC_PAGESIZE)\n");
            return 4096;
        case _SC_PHYS_PAGES:
            enclave_trace("sysconf(_SC_PHYS_PAGES)=%lu\n", heap_size);
            return heap_size;
        default:
            enclave_trace("sysconf(%d)\n", name);
    }
    return -1;
}

uid_t getuid() {
    enclave_trace("getuid\n");
    return 1;   // Not zero, don't tell the app it's root.
}

unsigned int sleep(unsigned int seconds) {
    enclave_trace("sleep(%u)\n", seconds);
    return 0;
}

long syscall(long number, ...) {
    enclave_trace("syscall(%ld)\n", number);
    return 0;
}

int unlink(const char* pathname) {
    enclave_trace("unlink(%s)\n", pathname);
    int err = 0;
    const int res = unlink_impl(pathname, err);
    errno = err;
    return res;
}

int rmdir(const char* pathname) {
    enclave_trace("rmdir(%s)\n", pathname);
    int err = 0;
    const int res = rmdir_impl(pathname, err);
    errno = err;
    return res;
}
    
int fsync(int fd) {
    enclave_trace("fsync\n");
    return 0;
}

pid_t getpid(void) {
    enclave_trace("getpid\n");
    return 2;
}

pid_t getppid(void) {
    enclave_trace("getppid\n");
    return 1;
}

pid_t vfork() {
    enclave_trace("vfork\n");
    errno = ENOSYS;
    return -1;
}

int execve(const char* pathname, char* const argv[], char* const envp[]) {
    errno = EACCES;
    return -1;
}

off64_t lseek64(int fd, off64_t offset, int whence) {
    enclave_trace("lseek64\n");
    return lseek64_impl(fd, offset, whence);
}

int getgroups(int gidsetsize, gid_t grouplist[]) {
    enclave_trace("getgroups\n");
    return -1;
}

int fdatasync(int fd) {
    enclave_trace("fsync\n");
    return 0;
}

int ftruncate(int fd, off_t length) {
    enclave_trace("ftruncate(fs %d, length %lu)\n", fd, length);
    int err = 0;
    const int res = ftruncate_impl(fd, length, err);
    errno = err;
    return res;
}

int ftruncate64(int fd, off64_t length) {
    enclave_trace("ftruncate(fs %d, length %lu)\n", fd, length);
    int err = 0;
    const int res = ftruncate_impl(fd, length, err);
    errno = err;
    return res;
}

int isatty(int fd) {
    enclave_trace("isatty");
    errno = EBADF;
    return -1;
}

int tcgetattr(int fd, void* termios_p) {
    enclave_trace("tcgetattr");
    errno = EAGAIN;
    return -1;
}

int tcsetattr(int fd,
	      int optional_actions,	      
	      void *termios_p) {
    enclave_trace("tcsetattr");
    errno = EAGAIN;
    return -1;
}

}
