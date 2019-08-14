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

package com.southernstorm.noise.protocol;

/**
 * Interface for objects that can wipe their in-memory state by
 * zeroing out arrays.
 * 
 * Applications that use the Noise protocol can inadvertently leave
 * sensitive data in the heap if steps are not taken to clean them up.
 *
 * Please note that as most JVMs use copying garbage collectors,
 * there is no way to completely remove all traces of a destroyable
 * from memory. Additionally this step only matters if you believe
 * an adversary may obtain read access to your address space, in
 * which case you may be leaking keys and other valuable secrets
 * more directly.
 * 
 * The {@link Noise#destroy(byte[])} function can help with destroying byte arrays
 * that hold sensitive values.
 */
public interface Destroyable {
	/**
	 * Destroys all sensitive state in the current object.
	 */
	void destroy();
}
