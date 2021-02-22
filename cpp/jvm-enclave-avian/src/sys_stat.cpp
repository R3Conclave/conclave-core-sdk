#include "sys_stat.h"

int __fxstat64_impl(int, int, struct stat64 *) {
    return -1;
}

int __xstat64_impl(int, const char *, struct stat64 *) {
    return -1;
}