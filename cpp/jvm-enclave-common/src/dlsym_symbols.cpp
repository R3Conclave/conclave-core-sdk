#include <dlsym_symbols.h>
#include <map>
#include <string>

namespace {
    // The singleton holding dlsym symbols.
    std::map<std::string, const void *>& getSymbols() {
        static std::map<std::string, const void *> staticSymbols;
        return staticSymbols;
    }
}

void DlsymSymbols::add(const char *name, void *symbol) {
    getSymbols()[name] = symbol;
}

const void *DlsymSymbols::lookup(const char *name) {
    auto iterator = getSymbols().find(name);
    if (iterator == getSymbols().end()) {
        return nullptr;
    } else {
        return iterator->second;
    }
}

extern "C" const void *dlsym(void *, const char *name) {
    return DlsymSymbols::lookup(name);
}
