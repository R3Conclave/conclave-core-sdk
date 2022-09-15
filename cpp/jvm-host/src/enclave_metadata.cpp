#include <iostream>
#include <cstddef>
#include <cstring>

#include <enclave_metadata.h>
#include <elf_types.h>
#include <sgx.h>
#include <internal/util.h>
#include <internal/metadata.h>
#include <fclose_guard.h>

static sgx_status_t read_header(FILE *fp, Elf64_Ehdr *header) {
    if (0 != fseek(fp, 0, SEEK_SET)) {
        return SGX_ERROR_FILE_NOT_SGX_FILE;
    }

    size_t read = fread(header, sizeof(Elf64_Ehdr), 1, fp);
    if (0 == read) {
        return SGX_ERROR_FILE_NOT_SGX_FILE;
    }

    if (header->e_ident[0] != ELFMAG0
            || header->e_ident[1] != ELFMAG1
            || header->e_ident[2] != ELFMAG2
            || header->e_ident[3] != ELFMAG3) {
        return SGX_ERROR_FILE_NOT_SGX_FILE;
    }

    if (header->e_ident[EI_CLASS] != ELFCLASS64) {
        return SGX_ERROR_FILE_NOT_SGX_FILE;
    }

    return SGX_SUCCESS;
}

static sgx_status_t find_section(FILE *fp, Elf64_Ehdr *header, const char *name, Elf64_Shdr *metadata_section) {
    fseek(fp, header->e_shoff, SEEK_SET);
    auto *sections = static_cast<Elf64_Shdr*>(alloca(header->e_shnum * sizeof(Elf64_Shdr)));
    if (header->e_shnum != fread(sections, sizeof(Elf64_Shdr), header->e_shnum, fp)) {
        free(sections);
        return SGX_ERROR_FILE_NOT_SGX_FILE;
    }

    Elf64_Shdr *name_section = &sections[header->e_shstrndx];
    for (int i = 1; i < header->e_shnum; i++) { // Skip index 0, always empty
        char sectionName[16];
        fseek(fp, name_section->sh_offset + sections[i].sh_name, SEEK_SET);
        size_t bytesRead = fread(sectionName, 1, sizeof(sectionName), fp);
        if ((bytesRead == sizeof(sectionName)) && 0 == strncmp(sectionName, name, 16)) {
            memcpy(metadata_section, &sections[i], sizeof(Elf64_Shdr));
            return SGX_SUCCESS;
        }
    }

    return SGX_ERROR_FILE_NOT_SGX_FILE;
}

sgx_status_t retrieve_enclave_metadata(const char *path, metadata_t *metadata) {
    FILE *fp = fopen(path, "rb");
    if (!fp) {
        return SGX_ERROR_ENCLAVE_FILE_ACCESS;
    }
    FcloseGuard close(fp);

    Elf64_Ehdr header;
    sgx_status_t read_header_result = read_header(fp, &header);
    if (SGX_SUCCESS != read_header_result) {
        return read_header_result;
    }

    Elf64_Shdr section;
    sgx_status_t find_section_result = find_section(fp, &header, ".note.sgxmeta", &section);
    if (SGX_SUCCESS != find_section_result) {
        return find_section_result;
    }

    Elf64_Note note;
    fseek(fp, section.sh_offset, SEEK_SET);
    if (fread(&note, 1, sizeof(note), fp) != sizeof(note)) {
        return SGX_ERROR_FILE_NOT_SGX_FILE;
    }

    if (section.sh_size != ROUND_TO(sizeof(Elf64_Note) + note.namesz + note.descsz, section.sh_addralign)) {
        return SGX_ERROR_FILE_NOT_SGX_FILE;
    }

    const char *meta_name = "sgx_metadata";
    const size_t meta_name_len = strlen(meta_name);
    char meta_name_buffer[16] = { 0 };

    fseek(fp, section.sh_offset + sizeof(Elf64_Note), SEEK_SET);
    if (sizeof(meta_name_buffer) != fread(meta_name_buffer, 1, sizeof(meta_name_buffer), fp)) {
        return SGX_ERROR_FILE_NOT_SGX_FILE;
    }

    if (meta_name_len + 1 != note.namesz || 0 != strncmp(meta_name, meta_name_buffer, meta_name_len)) {
        return SGX_ERROR_FILE_NOT_SGX_FILE;
    }

    size_t meta_data_offset = section.sh_offset + sizeof(Elf64_Note) + note.namesz;

    printf("C SGX Metadata starts at this position in the file: %lu\n", meta_data_offset);
    fseek(fp, meta_data_offset, SEEK_SET);
    if (1 != fread(metadata, sizeof(metadata_t), 1, fp)) {
        return SGX_ERROR_FILE_NOT_SGX_FILE;
    }

    /*
    size_t arrlen = sizeof(metadata_t);
    printf("Metadata in C, size %lu\n", arrlen);

    unsigned char* p = (unsigned char*)metadata;

    for (size_t i = 0; i < arrlen; i++) {
        printf("%.2x", p[i]);
    }
    printf("\n");
    */    
    return SGX_SUCCESS;
}
