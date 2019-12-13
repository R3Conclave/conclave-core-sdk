#pragma once

#include <string>
#include <map>

class DlsymSymbols {
public:
    static void add(const char *name, void *symbol);
    static const void * lookup(const char *name);

    template <class T>
    static inline void* voidPointer(T function)
    {
        void* p;
        memcpy(&p, &function, sizeof(void*));
        return p;
    }
};

#define DLSYM_CONCATENATE(s1, s2) s1##s2
#define DLSYM_EXPAND_THEN_CONCATENATE(s1, s2) DLSYM_CONCATENATE(s1, s2)
#define DLSYM_UNIQUE_IDENTIFIER(prefix) DLSYM_EXPAND_THEN_CONCATENATE(prefix, __LINE__)
#define DLSYM_STATIC_BLOCK_IMPL1(prefix) \
    DLSYM_STATIC_BLOCK_IMPL2(DLSYM_CONCATENATE(prefix,_fn),DLSYM_CONCATENATE(prefix,_var))
#define DLSYM_STATIC_BLOCK_IMPL2(function_name, var_name) \
static void function_name(); \
static int var_name __attribute((unused)) = (function_name(), 0) ; \
static void function_name()

#define DLSYM_ADD(SYMBOL) DlsymSymbols::add(#SYMBOL, DlsymSymbols::voidPointer(SYMBOL))
#define DLSYM_STATIC DLSYM_STATIC_BLOCK_IMPL1(DLSYM_UNIQUE_IDENTIFIER(_dlsym_))
