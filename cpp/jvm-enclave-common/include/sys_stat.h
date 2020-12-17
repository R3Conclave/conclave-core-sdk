#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef uint32_t mode_t;

int __fxstat64_impl(int ver, int fildes, struct stat64 * stat_buf);

#ifdef __cplusplus
}
#endif
