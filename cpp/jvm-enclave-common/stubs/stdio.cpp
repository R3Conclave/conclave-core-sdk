//
// OS Stubs for functions declared in stdio.h
//
#include "vm_enclave_layer.h"
#include "file_manager.h"

extern "C" {

    FILE *stdin = nullptr;
    
    int remove(const char* pathname) {
        enclave_trace("remove(%s)\n", pathname);
        int err = 0;
        const int res = remove_impl(pathname, err);
        errno = err;
        return res;
    }

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
        int err = 0;
        FILE* res = fopen_impl(path, mode, err);
        errno = err;
        return res;
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
            return file->read((unsigned char*)buf, size * count) / size;
        }
        errno = -EPERM;
        return (size_t)0;
    }

    size_t fwrite(const void* buf, size_t size, size_t count, FILE* fp) {
        conclave::File* file = conclave::FileManager::instance().fromFILE(fp);
        if (file) {
            return file->write((const unsigned char*)buf, size * count) / size;
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

    int sscanf(const char *str, const char *format, ...) {
        enclave_trace("sscanf");
        return -1;
    }

    int fileno(FILE *stream) {
        enclave_trace("fileno");
        errno = EBADF;
        return -1;
    }
    
    ssize_t __getdelim (char **__lineptr, size_t *__n, int __delimiter, FILE *__stream) {
        enclave_trace("__getdelim\n");
        return -1;
    }

    int rename(const char* oldpath, const char* newpath) {
        int err = 0;
        const int res = rename_impl(oldpath, newpath, err);

        if (res != 0) {
            errno = err;
        }
        return res;   
    }    

    char *fgets(char *s, int size, FILE *stream) {
        enclave_trace("fgets\n");
        return nullptr;
    }
}
