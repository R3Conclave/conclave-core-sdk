#include "sys_stat.h"

int __fxstat64_impl(int, int, struct stat64 *) {
    return -1;
}