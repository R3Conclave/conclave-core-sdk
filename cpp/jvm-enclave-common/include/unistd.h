#pragma once

#include <sys/types.h>

#ifdef __cplusplus
extern "C" {
#endif

ssize_t read_impl(int fd, void* buf, size_t count);
ssize_t pread_impl(int fd, void* buf, size_t count, off_t offset);
int close_impl(int fildes);
ssize_t write_impl(int fd, const void *buf, size_t count);
ssize_t pwrite_impl(int fd, const void *buf, size_t count, off_t offset);

#ifdef __cplusplus
}
#endif