#pragma once

extern "C" {
struct _IO_FILE;
typedef struct _IO_FILE FILE;
int printf(const char *s, ...);
FILE *fopen(const char *path, const char *mode);
}
