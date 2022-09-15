package net.fornwall.jelf;

interface BackingFile {
    int pos();

    void seek(long offset);
    void skip(int bytesToSkip);
    short readUnsignedByte();
    int read(byte[] data);

}
