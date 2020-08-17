#pragma once

/*
 * Print the given output to the console.
 * This function expects UTF-8 and will search for CR/LF codepoints in the 
 * string, prepending each line with an enclave prefix. If the string is not
 * valid UTF-8 then the entire string is prepended with the prefix and copied
 * to the console directly with no further processing.
 *
 * ANSI terminals are detected by the presence of the "TERM" environment variable. If
 * present, escape sequences are output to the console to colour the enclave output.
 */
void enclave_console(const char *str, int n);
