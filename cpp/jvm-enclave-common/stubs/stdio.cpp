//
// OS Stubs for functions declared in stdio.h
//
#include "vm_enclave_layer.h"
#include "file_manager.h"

//////////////////////////////////////////////////////////////////////////////
// Stub functions to satisfy the linker
STUB(fgets);
STUB(remove);
STUB(rename);

extern "C" {

int printf(const char *s, ...) {
    va_list va;
    va_start(va, s);
    int res = vfprintf(stdout, s, va);
    va_end(va);
    return res;
}

int vfprintf(FILE *stream, const char *s, va_list va) {
    return __vfprintf_chk(stream, 0, s, va);
}

int __printf_chk(int, const char *s, ...) {
    va_list va;
    va_start(va, s);
    int res = vfprintf(stdout, s, va);
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

int sprintf(char *str, const char *format, ...) {
    // Unsafe version of snprintf.
    va_list  va;
    va_start(va, format);
    int res = vsnprintf(str, 1024*1024, format, va);
    va_end(va);
    return res;
}

FILE *fopen(const char *path, const char *mode) {
    conclave::File* file = conclave::FileManager::instance().open(path);
    if (file) {
        return (FILE*)file;
    }
    return nullptr;
}

int fclose(FILE* fp) {
    conclave::File* file = conclave::FileManager::instance().fromFILE(fp);
    if (file) {
        conclave::FileManager::instance().close(file);
    }
    return 0;
}

int fflush(FILE*) {
    return 0;
}
//__asm__(".symver fflush,fflush@@GLIBC_2.2.5");

size_t fread(void* buf, size_t size, size_t count, FILE* fp) {
    conclave::File* file = conclave::FileManager::instance().fromFILE(fp);
    if (file) {
        return file->read(size, count, buf);
    }
    errno = -EPERM;
    return (size_t)0;
}

size_t fwrite(const void* buf, size_t size, size_t count, FILE* fp) {
    conclave::File* file = conclave::FileManager::instance().fromFILE(fp);
    if (file) {
        return file->write(size, count, buf);
    }
    errno = -EPERM;
    return (size_t)0;
}

FILE *fdopen(int fd, const char *mode) {
    enclave_trace("fdopen\n");

    conclave::File* file = conclave::FileManager::instance().fromHandle(fd);
    if (file) {
        return (FILE*)file;
    }
    return nullptr;
}

int fscanf ( FILE * stream, const char * format, ... ) {
    enclave_trace("fscanf\n");
    return 0;
}

}
