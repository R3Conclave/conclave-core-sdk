package com.southernstorm.noise.protocol;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Implements the AES/GCM cipher. This is the most modern mode of the AES cipher,
 * which allows for authenticated and additional data.
 */
class AESGCMCipherState implements CipherState {
    private final Cipher cipher;
    private SecretKeySpec keySpec;
    private byte[] iv;
    private long nonce;

    AESGCMCipherState() throws NoSuchAlgorithmException {
        try {
            cipher = Cipher.getInstance("AES/GCM/NoPadding");
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException(e);  // Should never happen.
        }
        nonce = 0;
        iv = new byte[96 / 8];  // 96 bit IV is the most efficient for AES/GCM.
    }

    /**
     * Gets the Noise protocol name for this cipher.
     *
     * @return The cipher name.
     */
    @Override
    public String getCipherName() {
        return "AESGCM";
    }

    /**
     * Gets the length of the key values for this cipher.
     *
     * @return The length of the key in bytes; usually 32.
     */
    @Override
    public int getKeyLength() {
        return 32;
    }

    /**
     * Gets the length of the MAC values for this cipher.
     *
     * @return The length of MAC values in bytes, or zero if the
     * key has not yet been initialized.
     */
    @Override
    public int getMACLength() {
        return hasKey() ? 16 : 0;
    }

    /**
     * Initializes the key on this cipher object.
     * <p>
     * The key buffer must contain at least {@link #getKeyLength()} bytes
     * starting at offset.
     *
     * @param key    Points to a buffer that contains the key.
     * @param offset The offset of the key in the key buffer.
     * @see #hasKey()
     */
    @Override
    public void initializeKey(byte[] key, int offset) {
        keySpec = new SecretKeySpec(key, offset, 32, "AES");
        nonce = 0;
    }

    /**
     * Determine if this cipher object has been configured with a key.
     *
     * @return true if this cipher object has a key; false if the
     * key has not yet been set with initializeKey().
     * @see #initializeKey(byte[], int)
     */
    @Override
    public boolean hasKey() {
        return keySpec != null;
    }

    private void initCipher(int mode, byte[] ad) throws InvalidKeyException, InvalidAlgorithmParameterException {
        // We expect the nonce to overflow and wrap, we'll happily use the negative numbers because it's only
        // non-repetition that matters, the actual value is unimportant. If we reach -1 then we'd have to
        // encrypt again with a nonce of zero, which would reveal valuable hints to cryptanalysts.
        if (nonce == -1L)
            throw new IllegalStateException("Cannot encrypt more than 2^64 - 1 times");
        iv[4] = (byte)(nonce >> 56);
        iv[5] = (byte)(nonce >> 48);
        iv[6] = (byte)(nonce >> 40);
        iv[7] = (byte)(nonce >> 32);
        iv[8] = (byte)(nonce >> 24);
        iv[9] = (byte)(nonce >> 16);
        iv[10] = (byte)(nonce >> 8);
        iv[11] = (byte)nonce;
        ++nonce;
        cipher.init(mode, keySpec, new GCMParameterSpec(128, iv));
        if (ad != null)
            cipher.updateAAD(ad);
    }

    /**
     * Encrypts a plaintext buffer using the cipher and a block of associated data.
     * The associated data is not included but is taken into account during
     * the authentication checks, so it must be provided at decryption time and
     * not have been tampered with.
     * <p>
     * The plaintext and ciphertext buffers can be the same for in-place
     * encryption.  In that case, plaintextOffset must be identical to
     * ciphertextOffset.
     * <p>
     * There must be enough space in the ciphertext buffer to accommodate
     * length + getMACLength() bytes of data starting at ciphertextOffset.
     *
     * @param ad               The associated data, or null if there is none.
     * @param plaintext        The buffer containing the plaintext to encrypt.
     * @param plaintextOffset  The offset within the plaintext buffer of the
     *                         first byte or plaintext data.
     * @param ciphertext       The buffer to place the ciphertext in.  This can
     *                         be the same as the plaintext buffer.
     * @param ciphertextOffset The first offset within the ciphertext buffer
     *                         to place the ciphertext and the MAC tag.
     * @param length           The length of the plaintext.
     * @return The length of the ciphertext plus the MAC tag.
     * @throws ShortBufferException  The ciphertext buffer does not have
     *                               enough space to hold the ciphertext plus MAC.
     * @throws IllegalStateException The nonce has wrapped around.
     */
    @Override
    public int encryptWithAd(byte[] ad, byte[] plaintext, int plaintextOffset, byte[] ciphertext, int ciphertextOffset, int length) throws ShortBufferException {
        int space = ciphertextOffset <= ciphertext.length ? ciphertext.length - ciphertextOffset : 0;
        if (keySpec == null) {
            // The key is not set yet - return the plaintext as-is.
            if (length > space)
                throw new ShortBufferException();
            if (plaintext != ciphertext || plaintextOffset != ciphertextOffset)
                System.arraycopy(plaintext, plaintextOffset, ciphertext, ciphertextOffset, length);
            return length;
        }

        if (space < 16 || length > (space - 16))
            throw new ShortBufferException();

        try {
            initCipher(Cipher.ENCRYPT_MODE, ad);
            int result = cipher.update(plaintext, plaintextOffset, length, ciphertext, ciphertextOffset);
            result += cipher.doFinal(ciphertext, ciphertextOffset + result);
            return result;
        } catch (InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            // Should never happen.
            throw new RuntimeException(e);
        }
    }

    /**
     * Decrypts a ciphertext buffer using the cipher and a block of associated data.
     * The associated data must match the data provided at encryption time, otherwise
     * {@link BadPaddingException} will be thrown.
     * <p>
     * The plaintext and ciphertext buffers can be the same for in-place
     * decryption.  In that case, ciphertextOffset must be identical to
     * plaintextOffset.
     *
     * @param ad               The associated data, or null if there is none.
     * @param ciphertext       The buffer containing the ciphertext to decrypt.
     * @param ciphertextOffset The offset within the ciphertext buffer of
     *                         the first byte of ciphertext data.
     * @param plaintext        The buffer to place the plaintext in.  This can be
     *                         the same as the ciphertext buffer.
     * @param plaintextOffset  The first offset within the plaintext buffer
     *                         to place the plaintext.
     * @param length           The length of the incoming ciphertext plus the MAC tag.
     * @return The length of the plaintext with the MAC tag stripped off.
     * @throws ShortBufferException  The plaintext buffer does not have
     *                               enough space to store the decrypted data.
     * @throws BadPaddingException   The MAC value failed to verify.
     * @throws IllegalStateException The nonce has wrapped around.
     */
    @Override
    public int decryptWithAd(byte[] ad, byte[] ciphertext, int ciphertextOffset, byte[] plaintext, int plaintextOffset, int length) throws ShortBufferException, BadPaddingException {
        int space = ciphertextOffset <= ciphertext.length ? ciphertext.length - ciphertextOffset : 0;
        if (length > space)
            throw new ShortBufferException();
        space = plaintextOffset > plaintext.length ? 0 : plaintext.length - plaintextOffset;
        if (keySpec == null) {
            // The key is not set yet - return the ciphertext as-is.
            if (length > space)
                throw new ShortBufferException();
            if (plaintext != ciphertext || plaintextOffset != ciphertextOffset)
                System.arraycopy(ciphertext, ciphertextOffset, plaintext, plaintextOffset, length);
            return length;
        }
        if (length < 16)
            Noise.throwBadTagException();
        int dataLen = length - 16;
        if (dataLen > space)
            throw new ShortBufferException();
        try {
            initCipher(Cipher.DECRYPT_MODE, ad);
            return cipher.doFinal(ciphertext, ciphertextOffset, length, plaintext, plaintextOffset);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a new instance of this cipher and initializes it with a key.
     *
     * @param key    The buffer containing the key.
     * @param offset The offset into the key buffer of the first key byte.
     * @return A new CipherState of the same class as this one.
     */
    @Override
    public CipherState fork(byte[] key, int offset) {
        try {
            CipherState cipher = new AESGCMCipherState();
            cipher.initializeKey(key, offset);
            return cipher;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the nonce value.
     * <p>
     * This function is intended for testing purposes only.  If the nonce
     * value goes backwards then security may be compromised.
     *
     * @param nonce The new nonce value, which must be greater than or equal
     *              to the current value.
     */
    @Override
    public void setNonce(long nonce) {
        this.nonce = nonce;
    }

    /**
     * Destroys all sensitive state in the current object.
     */
    @Override
    public void destroy() {
        Noise.destroy(iv);
        // There doesn't seem to be a standard API to clean out a Cipher.
        // So we instead set the key and IV to all-zeroes to hopefully
        // destroy the sensitive data in the cipher instance.
        keySpec = new SecretKeySpec(new byte[32], "AES");
        IvParameterSpec params = new IvParameterSpec(iv);
        try {
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, params);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            // Shouldn't happen.
        }
    }
}
