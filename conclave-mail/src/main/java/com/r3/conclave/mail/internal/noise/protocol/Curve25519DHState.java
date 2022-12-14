/*
 * Copyright (C) 2016 Southern Storm Software, Pty Ltd.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.r3.conclave.mail.internal.noise.protocol;

import java.util.Arrays;

import com.r3.conclave.mail.internal.noise.crypto.Curve25519;

/**
 * Implementation of the Curve25519 algorithm for the Noise protocol.
 */
class Curve25519DHState implements DHState {

	private final byte[] publicKey;
	private final byte[] privateKey;
	private int mode;

	/**
	 * Constructs a new Diffie-Hellman object for Curve25519.
	 */
	public Curve25519DHState()
	{
		publicKey = new byte [32];
		privateKey = new byte [32];
		mode = 0;
	}

	@Override
	public void destroy() {
		clearKey();
	}

	@Override
	public String getDHName() {
		return "25519";
	}

	@Override
	public int getPublicKeyLength() {
		return 32;
	}

	@Override
	public int getPrivateKeyLength() {
		return 32;
	}

	@Override
	public int getSharedKeyLength() {
		return 32;
	}

	@Override
	public void generateKeyPair() {
		Noise.random(privateKey);
		// The key here is 256 random bits. Curve25519 requires secret keys to be in a very specific form, with
		// particular bits flipped in order to ensure the key lies in the right cyclic subgroups and to eliminate
		// side channel attacks. The "clamping" procedure isn't done here but rather is integrated into the
		// curve evaluation itself, which is why we can pretend that a secret key is any random bit string both
		// at generation time and for storage.
		//
		// Note: this does imply that there are a few private keys that are equivalent to each other, but this is
		// not a problem in practice due to the huge keyspace.
		Curve25519.eval(publicKey, 0, privateKey, null);
		mode = 0x03;
	}

	@Override
	public void getPublicKey(byte[] key, int offset) {
		System.arraycopy(publicKey, 0, key, offset, 32);
	}

	@Override
	public void setPublicKey(byte[] key, int offset) {
		System.arraycopy(key, offset, publicKey, 0, 32);
		Arrays.fill(privateKey, (byte)0);
		mode = 0x01;
	}

    @Override
	public void setPrivateKey(byte[] key, int offset) {
		System.arraycopy(key, offset, privateKey, 0, 32);
		// The key here is 256 random bits. Curve25519 requires secret keys to be in a very specific form, with
		// particular bits flipped in order to ensure the key lies in the right cyclic subgroups and to eliminate
		// side channel attacks. The "clamping" procedure isn't done here but rather is integrated into the
		// curve evaluation itself, which is why we can pretend that a secret key is any random bit string both
		// at generation time and for storage.
		//
		// Note: this does imply that there are a few private keys that are equivalent to each other, but this is
		// not a problem in practice due to the huge keyspace.
		Curve25519.eval(publicKey, 0, privateKey, null);
		mode = 0x03;
	}

	@Override
	public void clearKey() {
		Noise.destroy(publicKey);
		Noise.destroy(privateKey);
		mode = 0;
	}

	@Override
	public boolean hasPublicKey() {
		return (mode & 0x01) != 0;
	}

	@Override
	public boolean hasPrivateKey() {
		return (mode & 0x02) != 0;
	}

	@Override
	public boolean isNullPublicKey() {
		if ((mode & 0x01) == 0)
			return false;
		int temp = 0;
		for (int index = 0; index < 32; ++index)
			temp |= publicKey[index];
		return temp == 0;
	}

	@Override
	public void calculate(byte[] sharedKey, int offset, DHState publicDH) {
		if (!(publicDH instanceof Curve25519DHState))
			throw new IllegalArgumentException("Incompatible DH algorithms");
		Curve25519.eval(sharedKey, offset, privateKey, ((Curve25519DHState)publicDH).publicKey);
	}

	@Override
	public void copyFrom(DHState other) {
		if (!(other instanceof Curve25519DHState))
			throw new IllegalStateException("Mismatched DH key objects");
		if (other == this)
			return;
		Curve25519DHState dh = (Curve25519DHState)other;
		System.arraycopy(dh.privateKey, 0, privateKey, 0, 32);
		System.arraycopy(dh.publicKey, 0, publicKey, 0, 32);
		mode = dh.mode;
	}
}
