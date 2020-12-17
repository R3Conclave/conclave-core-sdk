#include "fcntl.h"

extern "C" {

int open_impl(const char*, int, int) {
    return -1;
}

}