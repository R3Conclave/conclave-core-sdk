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
    public static final int KEY_LENGTH = 32;
    public static final int MAC_LENGTH = 16;
    private final Cipher cipher;
    private SecretKeySpec keySpec;
    private long nonce;

    AESGCMCipherState() {
        try {
            cipher = Cipher.getInstance("AES/GCM/NoPadding");
        } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);  // Should never happen.
        }
        nonce = 0;
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
        return KEY_LENGTH;
    }

    /**
     * Gets the length of the MAC values for this cipher.
     *
     * @return The length of MAC values in bytes, or zero if the
     * key has not yet been initialized.
     */
    @Override
    public int getMACLength() {
        return hasKey() ? MAC_LENGTH : 0;
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

    // Called to encrypt a new packet.
    private void initCipher(int mode, byte[] authenticatedData) throws InvalidKeyException, InvalidAlgorithmParameterException {
        // We expect the nonce to overflow and wrap, we'll happily use the negative numbers because it's only
        // non-repetition that matters, the actual value is unimportant. If we reach -1 then we'd have to
        // encrypt again with a nonce of zero, which would reveal valuable hints to cryptanalysts (see below) so we
        // refuse to proceed. This should never happen in any real program as 2^64 Noise packets is way larger than
        // any normal session would need.
        if (nonce == -1L)
            throw new IllegalStateException("Cannot encrypt more than 2^64 - 1 times");

        // We use a 96 bit IV but only fill out 64 bits of it. This is the most efficient and correct way to use
        // AES/GCM but it's not obvious why. A full explanation is here:
        //
        // https://crypto.stackexchange.com/questions/41601/aes-gcm-recommended-iv-size-why-12-bytes
        //
        // The gist of it is:
        //
        // 1. AES/GCM fundamentally requires a 96 bit IV and will have to internally 'resize' whatever it's given to
        //    this size if it doesn't match. This is because it's embedding CTR mode which requires a 128 bit IV and
        //    then uses the last 4 bytes as the CTR counter, leaving the other 96 bits for the exposed IV.
        //
        // 2. This is fine because the only requirement on the IV is that you don't reuse them with the same AES key.
        //    A simple way to satisfy that is use a counter as we do here. In practice 2^64 is so large that the user
        //    will never exceed that number of encryptions so there's no point going beyond that, and using the
        //    native 'long' type means we can increment the nonce using a standard incl opcode which is fast. Otherwise
        //    we'd need to use BigInteger as Java doesn't expose 96 bit integers.
        //
        // We allocate a new array and new GCMParameterSpec each time because GCMParameterSpec is immutable and copies
        // the iv array into itself. This is unfortunate, but hopefully C2 can inline and scalar replace the array
        // + object allocation to avoid creating unnecessary garbage.
        //
        // Note that the nonce here must only be unique within the scope of a single Noise session. That's because
        // each Noise handshake establishes a new random AES session key, and it's the combination of AES key plus IV
        // that should be unique.
        //
        // What happens if it's not unique? Then the same input plaintext would encrypt the same output ciphertext.
        // Repetition of ciphertext blocks can leak hints about the plaintext to cryptanalysts.
        final byte[] iv = new byte[96 / 8];
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
        if (authenticatedData != null)
            cipher.updateAAD(authenticatedData);
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

        // Need space to store the authentication tag.
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
        CipherState cipher = new AESGCMCipherState();
        cipher.initializeKey(key, offset);
        return cipher;
    }

    /**
     * Sets the nonce value.
     * <p>
     * This function is intended for testing purposes only.  If the nonce
     * value goes backwards then security may be compromised.
     *
     * @param nonce The new nonce value.
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
        // There doesn't seem to be a standard API to clean out a Cipher.
        // So we instead set the key and IV to all-zeroes to hopefully
        // destroy the sensitive data in the cipher instance.
        keySpec = new SecretKeySpec(new byte[32], "AES");
        GCMParameterSpec params = new GCMParameterSpec(128, new byte[96 / 8]);
        try {
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, params);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            // Shouldn't happen.
            throw new IllegalStateException(e);
        }
    }
}
