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
 * Information about all supported handshake patterns.
 */
class Pattern {
	
	private Pattern() {}

	// Token codes.
	public static final short S = 1;
	public static final short E = 2;
	public static final short EE = 3;
	public static final short ES = 4;
	public static final short SE = 5;
	public static final short SS = 6;
	public static final short F = 7;
	public static final short FF = 8;
	public static final short FLIP_DIR = 255;
	
	// Pattern flag bits.
	public static final short FLAG_LOCAL_STATIC = 0x0001;
	public static final short FLAG_LOCAL_EPHEMERAL = 0x0002;
	public static final short FLAG_LOCAL_REQUIRED = 0x0004;
	public static final short FLAG_LOCAL_EPHEM_REQ = 0x0008;
	public static final short FLAG_LOCAL_HYBRID = 0x0010;
	public static final short FLAG_LOCAL_HYBRID_REQ = 0x0020;
	public static final short FLAG_REMOTE_STATIC = 0x0100;
	public static final short FLAG_REMOTE_EPHEMERAL = 0x0200;
	public static final short FLAG_REMOTE_REQUIRED = 0x0400;
	public static final short FLAG_REMOTE_EPHEM_REQ = 0x0800;
	public static final short FLAG_REMOTE_HYBRID = 0x1000;
	public static final short FLAG_REMOTE_HYBRID_REQ = 0x2000;

	private static final short[] noise_pattern_N = {
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_REQUIRED,

	    E,
	    ES
	};

	private static final short[] noise_pattern_K = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_REQUIRED |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_REQUIRED,

	    E,
	    ES,
	    SS
	};

	private static final short[] noise_pattern_X = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_REQUIRED,

	    E,
	    ES,
	    S,
	    SS
	};

	private static final short[] noise_pattern_NN = {
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_REMOTE_EPHEMERAL,

	    E,
	    FLIP_DIR,
	    E,
	    EE
	};

	private static final short[] noise_pattern_NK = {
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_REQUIRED,

	    E,
	    ES,
	    FLIP_DIR,
	    E,
	    EE
	};

	private static final short[] noise_pattern_NX = {
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL,

	    E,
	    FLIP_DIR,
	    E,
	    EE,
	    S,
	    ES
	};

	private static final short[] noise_pattern_XN = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_REMOTE_EPHEMERAL,

	    E,
	    FLIP_DIR,
	    E,
	    EE,
	    FLIP_DIR,
	    S,
	    SE
	};

	private static final short[] noise_pattern_XK = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_REQUIRED,

	    E,
	    ES,
	    FLIP_DIR,
	    E,
	    EE,
	    FLIP_DIR,
	    S,
	    SE
	};

	private static final short[] noise_pattern_XX = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL,

	    E,
	    FLIP_DIR,
	    E,
	    EE,
	    S,
	    ES,
	    FLIP_DIR,
	    S,
	    SE
	};

	private static final short[] noise_pattern_KN = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_REQUIRED |
	    FLAG_REMOTE_EPHEMERAL,

	    E,
	    FLIP_DIR,
	    E,
	    EE,
	    SE
	};

	private static final short[] noise_pattern_KK = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_REQUIRED |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_REQUIRED,

	    E,
	    ES,
	    SS,
	    FLIP_DIR,
	    E,
	    EE,
	    SE
	};

	private static final short[] noise_pattern_KX = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_REQUIRED |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL,

	    E,
	    FLIP_DIR,
	    E,
	    EE,
	    SE,
	    S,
	    ES
	};

	private static final short[] noise_pattern_IN = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_REMOTE_EPHEMERAL,

	    E,
	    S,
	    FLIP_DIR,
	    E,
	    EE,
	    SE
	};

	private static final short[] noise_pattern_IK = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_REQUIRED,

	    E,
	    ES,
	    S,
	    SS,
	    FLIP_DIR,
	    E,
	    EE,
	    SE
	};

	private static final short[] noise_pattern_IX = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL,

	    E,
	    S,
	    FLIP_DIR,
	    E,
	    EE,
	    SE,
	    S,
	    ES
	};

	private static final short[] noise_pattern_XXfallback = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_EPHEM_REQ,

	    E,
	    EE,
	    S,
	    SE,
	    FLIP_DIR,
	    S,
	    ES
	};

	private static final short[] noise_pattern_Xnoidh = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_REQUIRED,

	    E,
	    S,
	    ES,
	    SS
	};

	private static final short[] noise_pattern_NXnoidh = {
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL,

	    E,
	    FLIP_DIR,
	    E,
	    S,
	    EE,
	    ES
	};

	private static final short[] noise_pattern_XXnoidh = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL,

	    E,
	    FLIP_DIR,
	    E,
	    S,
	    EE,
	    ES,
	    FLIP_DIR,
	    S,
	    SE
	};

	private static final short[] noise_pattern_KXnoidh = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_REQUIRED |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL,

	    E,
	    FLIP_DIR,
	    E,
	    S,
	    EE,
	    SE,
	    ES
	};

	private static final short[] noise_pattern_IKnoidh = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_REQUIRED,

	    E,
	    S,
	    ES,
	    SS,
	    FLIP_DIR,
	    E,
	    EE,
	    SE
	};

	private static final short[] noise_pattern_IXnoidh = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL,

	    E,
	    S,
	    FLIP_DIR,
	    E,
	    S,
	    EE,
	    SE,
	    ES
	};

	private static final short[] noise_pattern_NNhfs = {
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_HYBRID |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_HYBRID,

	    E,
	    F,
	    FLIP_DIR,
	    E,
	    F,
	    EE,
	    FF
	};

	private static final short[] noise_pattern_NKhfs = {
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_HYBRID |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_HYBRID |
	    FLAG_REMOTE_REQUIRED,

	    E,
	    F,
	    ES,
	    FLIP_DIR,
	    E,
	    F,
	    EE,
	    FF
	};

	private static final short[] noise_pattern_NXhfs = {
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_HYBRID |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_HYBRID,

	    E,
	    F,
	    FLIP_DIR,
	    E,
	    F,
	    EE,
	    FF,
	    S,
	    ES
	};

	private static final short[] noise_pattern_XNhfs = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_HYBRID |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_HYBRID,

	    E,
	    F,
	    FLIP_DIR,
	    E,
	    F,
	    EE,
	    FF,
	    FLIP_DIR,
	    S,
	    SE
	};

	private static final short[] noise_pattern_XKhfs = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_HYBRID |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_HYBRID |
	    FLAG_REMOTE_REQUIRED,

	    E,
	    F,
	    ES,
	    FLIP_DIR,
	    E,
	    F,
	    EE,
	    FF,
	    FLIP_DIR,
	    S,
	    SE
	};

	private static final short[] noise_pattern_XXhfs = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_HYBRID |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_HYBRID,

	    E,
	    F,
	    FLIP_DIR,
	    E,
	    F,
	    EE,
	    FF,
	    S,
	    ES,
	    FLIP_DIR,
	    S,
	    SE
	};

	private static final short[] noise_pattern_KNhfs = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_REQUIRED |
	    FLAG_LOCAL_HYBRID |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_HYBRID,

	    E,
	    F,
	    FLIP_DIR,
	    E,
	    F,
	    EE,
	    FF,
	    SE
	};

	private static final short[] noise_pattern_KKhfs = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_REQUIRED |
	    FLAG_LOCAL_HYBRID |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_HYBRID |
	    FLAG_REMOTE_REQUIRED,

	    E,
	    F,
	    ES,
	    SS,
	    FLIP_DIR,
	    E,
	    F,
	    EE,
	    FF,
	    SE
	};

	private static final short[] noise_pattern_KXhfs = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_REQUIRED |
	    FLAG_LOCAL_HYBRID |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_HYBRID,

	    E,
	    F,
	    FLIP_DIR,
	    E,
	    F,
	    EE,
	    FF,
	    SE,
	    S,
	    ES
	};

	private static final short[] noise_pattern_INhfs = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_HYBRID |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_HYBRID,

	    E,
	    F,
	    S,
	    FLIP_DIR,
	    E,
	    F,
	    EE,
	    FF,
	    SE
	};

	private static final short[] noise_pattern_IKhfs = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_HYBRID |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_HYBRID |
	    FLAG_REMOTE_REQUIRED,

	    E,
	    F,
	    ES,
	    S,
	    SS,
	    FLIP_DIR,
	    E,
	    F,
	    EE,
	    FF,
	    SE
	};

	private static final short[] noise_pattern_IXhfs = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_HYBRID |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_HYBRID,

	    E,
	    F,
	    S,
	    FLIP_DIR,
	    E,
	    F,
	    EE,
	    FF,
	    SE,
	    S,
	    ES
	};

	private static final short[] noise_pattern_XXfallback_hfs = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_HYBRID |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_EPHEM_REQ |
	    FLAG_REMOTE_HYBRID |
	    FLAG_REMOTE_HYBRID_REQ,

	    E,
	    F,
	    EE,
	    FF,
	    S,
	    SE,
	    FLIP_DIR,
	    S,
	    ES
	};

	private static final short[] noise_pattern_NXnoidh_hfs = {
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_HYBRID |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_HYBRID,

	    E,
	    F,
	    FLIP_DIR,
	    E,
	    F,
	    S,
	    EE,
	    FF,
	    ES
	};

	private static final short[] noise_pattern_XXnoidh_hfs = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_HYBRID |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_HYBRID,

	    E,
	    F,
	    FLIP_DIR,
	    E,
	    F,
	    S,
	    EE,
	    FF,
	    ES,
	    FLIP_DIR,
	    S,
	    SE
	};

	private static final short[] noise_pattern_KXnoidh_hfs = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_REQUIRED |
	    FLAG_LOCAL_HYBRID |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_HYBRID,

	    E,
	    F,
	    FLIP_DIR,
	    E,
	    F,
	    S,
	    EE,
	    FF,
	    SE,
	    ES
	};

	private static final short[] noise_pattern_IKnoidh_hfs = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_HYBRID |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_HYBRID,

	    E,
	    F,
	    S,
	    ES,
	    SS,
	    FLIP_DIR,
	    E,
	    F,
	    EE,
	    FF,
	    SE
	};

	private static final short[] noise_pattern_IXnoidh_hfs = {
	    FLAG_LOCAL_STATIC |
	    FLAG_LOCAL_EPHEMERAL |
	    FLAG_LOCAL_HYBRID |
	    FLAG_REMOTE_STATIC |
	    FLAG_REMOTE_EPHEMERAL |
	    FLAG_REMOTE_HYBRID,

	    E,
	    F,
	    S,
	    FLIP_DIR,
	    E,
	    F,
	    S,
	    EE,
	    FF,
	    SE,
	    ES
	};

	/**
	 * Look up the description information for a pattern.
	 * 
	 * @param name The name of the pattern.
	 * @return The pattern description or null.
	 */
	public static short[] lookup(String name)
	{
		switch (name) {
			case "N":
				return noise_pattern_N;
			case "K":
				return noise_pattern_K;
			case "X":
				return noise_pattern_X;
			case "NN":
				return noise_pattern_NN;
			case "NK":
				return noise_pattern_NK;
			case "NX":
				return noise_pattern_NX;
			case "XN":
				return noise_pattern_XN;
			case "XK":
				return noise_pattern_XK;
			case "XX":
				return noise_pattern_XX;
			case "KN":
				return noise_pattern_KN;
			case "KK":
				return noise_pattern_KK;
			case "KX":
				return noise_pattern_KX;
			case "IN":
				return noise_pattern_IN;
			case "IK":
				return noise_pattern_IK;
			case "IX":
				return noise_pattern_IX;
			case "XXfallback":
				return noise_pattern_XXfallback;
			case "Xnoidh":
				return noise_pattern_Xnoidh;
			case "NXnoidh":
				return noise_pattern_NXnoidh;
			case "XXnoidh":
				return noise_pattern_XXnoidh;
			case "KXnoidh":
				return noise_pattern_KXnoidh;
			case "IKnoidh":
				return noise_pattern_IKnoidh;
			case "IXnoidh":
				return noise_pattern_IXnoidh;
			case "NNhfs":
				return noise_pattern_NNhfs;
			case "NKhfs":
				return noise_pattern_NKhfs;
			case "NXhfs":
				return noise_pattern_NXhfs;
			case "XNhfs":
				return noise_pattern_XNhfs;
			case "XKhfs":
				return noise_pattern_XKhfs;
			case "XXhfs":
				return noise_pattern_XXhfs;
			case "KNhfs":
				return noise_pattern_KNhfs;
			case "KKhfs":
				return noise_pattern_KKhfs;
			case "KXhfs":
				return noise_pattern_KXhfs;
			case "INhfs":
				return noise_pattern_INhfs;
			case "IKhfs":
				return noise_pattern_IKhfs;
			case "IXhfs":
				return noise_pattern_IXhfs;
			case "XXfallback+hfs":
				return noise_pattern_XXfallback_hfs;
			case "NXnoidh+hfs":
				return noise_pattern_NXnoidh_hfs;
			case "XXnoidh+hfs":
				return noise_pattern_XXnoidh_hfs;
			case "KXnoidh+hfs":
				return noise_pattern_KXnoidh_hfs;
			case "IKnoidh+hfs":
				return noise_pattern_IKnoidh_hfs;
			case "IXnoidh+hfs":
				return noise_pattern_IXnoidh_hfs;
		}
		return null;
	}

	/**
	 * Reverses the local and remote flags for a pattern.
	 * 
	 * @param flags The flags, assuming that the initiator is "local".
	 * @return The reversed flags, with the responder now being "local".
	 */
	public static short reverseFlags(short flags)
	{
		return (short)(((flags >> 8) & 0x00FF) | ((flags << 8) & 0xFF00));
	}
}
