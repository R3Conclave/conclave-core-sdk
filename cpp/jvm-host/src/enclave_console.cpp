#include <clocale>
#include <cstdlib>
#include <string>
#include <vector>
#include <iostream>

#define DEBUG_PREFIX        "Enclave> "
#define DEBUG_COLOUR_START  "\033[1;33m"        // Bold yellow
#define DEBUG_COLOUR_END    "\033[0m";

static bool supportsColour() {
    static bool checked = false;
    static bool supports_colour = false;
    if (!checked) {
        checked = true;
        const char* term = getenv("TERM");
        if (term) {
            std::string check_term(term);
            if ((check_term.find("color") != std::string::npos) ||
                (check_term.find("xterm") != std::string::npos)) {
                supports_colour = true;
            }
        }
    }
    return supports_colour;
}

static void getLines(const char* str, int n, std::vector<std::string>& lines, bool& ends_with_newline) {
    // The string we are given should be UTF-8. The language is not important for our
    // use of locale as we are only calculating codepoint lengths. We need to be tolerant
    // of non-compliant strings though, printing the output even if it means we cannot
    // prepend 'Enclave> ' correctly if the string is malformed.
    std::string utf8(str, str + n);
    std::setlocale(LC_ALL, "en_US.utf8");
    const char* c_str = utf8.c_str();

    ends_with_newline = true;

    // Find all the CRs in the resulting string and create a list of lines
    // based on this
    size_t pos = 0;
    size_t str_start = 0;

    while (pos < (size_t)n) {
        const int cp_len = std::mblen(&c_str[pos], n - pos);
        // -1 is returned on an error. The string is not valid UTF-8 so we should discard
        // any CRs we've already found and just print the string as is.
        if (cp_len == -1) {
            lines.clear();
            lines.push_back(std::string(c_str, &c_str[n]));
            ends_with_newline = true;
            return;
        }
        // Check for CR/LF. Any cp value <= 0x7f is a single byte long so we can check the
        // next character for a CR/LF too.
        if ((cp_len == 1) && ((c_str[pos] == '\r') || (c_str[pos] == '\n'))) {
            // See if there is a matching LF with CR or CR with LF.
            ++pos;
            if ((pos < (size_t)n) && 
                ((c_str[pos] == '\r') || (c_str[pos] == '\n')) && 
                (c_str[pos] != c_str[pos-1])) {
                ++pos;
            }

            // Save this portion of the string including the line terminator
            lines.push_back(std::string(&c_str[str_start], &c_str[pos]));
            str_start = pos;
        }
        pos += cp_len;
    }

    // Handle the final part if it wasn't terminated with a CR
    if (str_start < (size_t)n) {
        lines.push_back(std::string(&c_str[str_start], &c_str[n]));
        ends_with_newline = false;
    }
}

void enclave_console(const char *str, int n) {
    // Keep track of whether each debug_print terminates with a newline. If it does not
    // then we don't want to print our prefix at the start of this buffer
    static bool show_prefix = true;

    std::vector<std::string> lines;
    bool ends_with_newline = true;
    getLines(str, n, lines, ends_with_newline);

    if (supportsColour()) {
        std::cout << DEBUG_COLOUR_START;
    }
    for (auto line : lines) {
        if (show_prefix) {
            std::cout << DEBUG_PREFIX;
         }
         show_prefix = true;
         std::cout << line;
    }
    if (supportsColour()) {
        std::cout << DEBUG_COLOUR_END;
    }
    std::cout.flush();

    // Keep track for next time whether we ended with a newline
    show_prefix = ends_with_newline;
}
