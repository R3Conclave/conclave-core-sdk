//
// OS Stubs for functions declared in stdio.h
//
#include "vm_enclave_layer.h"
#include "file_manager.h"

//////////////////////////////////////////////////////////////////////////////
// Stub functions to satisfy the linker
STUB_NO_ABORT(fsync);
STUB(ftruncate);
STUB(ftruncate64);
STUB(getegid);
STUB(geteuid);
STUB(getgid);
STUB(gethostname);
STUB(lseek);
STUB(lseek64);
STUB(lstat);
STUB(pathconf);
STUB(readlink);
STUB(pread64);

extern "C" {

// These two symbols are defined as parameters to the linker when running native-image.
// __ImageBase is a symbol that is at the address at the base of the image. __HeapSize is
// a symbol at the fake address of &__ImageBase + size of the heap ad defined in the enclave
// configuration. We can subtract one address from the other to get the actual heap size.
extern unsigned long __HeapSize;
extern unsigned long __ImageBase;
unsigned long heap_size = (unsigned long)((unsigned long long)&__HeapSize - (unsigned long long)&__ImageBase);

int access(const char *pathname, int) {
    // SubstrateVM checks for access to the random device. Just let that
    // succeed
    enclave_trace("access(%s)\n", pathname);
    if (conclave::FileManager::instance().exists(pathname)) {
        return 0;
    }
    else {
        errno = -EPERM;
        return -1;
    }
}

ssize_t read(int fh, void* p, size_t len) {
    conclave::File* file = conclave::FileManager::instance().fromHandle(fh);
    if (file) {
        enclave_trace("read(%s)\n", file->filename().c_str());
        return file->read(1, len, p);
    }
    errno = -EPERM;
    enclave_trace("read()\n");
    return (ssize_t)-1;
}

ssize_t write(int fd, const void *buf, size_t count) {
    conclave::File* file = conclave::FileManager::instance().fromHandle(fd);
    if (file) {
        enclave_trace("write(%s)\n", file->filename().c_str());
        return file->write(1, count, buf);
    } 
    enclave_trace("write(%d)\n", fd);
    return (ssize_t)-1;
}

int close(int handle) {
    enclave_trace("close()\n");
    conclave::FileManager::instance().close(handle);
    return 0;
}

int dup(int oldfd) {
    enclave_trace("dup\n");
    return -1;
}

int dup2(int fd1, int fd2) {
    enclave_trace("dup2\n");
    return -1;
}

pid_t fork() {
    enclave_trace("fork\n");
    errno = -ENOSYS;
    return -1;
}

char *getcwd(char *buf, size_t size) {
    enclave_trace("getcwd\n");
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

}
