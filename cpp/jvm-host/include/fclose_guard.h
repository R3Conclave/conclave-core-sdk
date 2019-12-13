#pragma once

#include <cstdio>

struct FcloseGuard {
    FILE * const descriptor;
    FcloseGuard() = delete;
    FcloseGuard(const FcloseGuard&) = delete;
    explicit FcloseGuard(FILE *descriptor) : descriptor(descriptor) {}
    ~FcloseGuard() {
        fclose(descriptor);
    }
};
