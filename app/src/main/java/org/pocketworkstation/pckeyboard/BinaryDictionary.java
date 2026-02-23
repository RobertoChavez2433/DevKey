/*
 * JNI Bridge class — DO NOT MODIFY
 *
 * This class exists solely to maintain compatibility with the C++ JNI layer
 * (org_pocketworkstation_pckeyboard_BinaryDictionary.cpp) which registers
 * native methods using the class path "org/pocketworkstation/pckeyboard/BinaryDictionary".
 *
 * The actual BinaryDictionary implementation lives at dev.devkey.keyboard.BinaryDictionary.
 * This bridge class holds the native method declarations and static library loader,
 * and the new BinaryDictionary delegates native calls here.
 */
package org.pocketworkstation.pckeyboard;

import java.nio.ByteBuffer;

/**
 * JNI bridge that receives RegisterNatives bindings from the C++ layer.
 * The native library jni_pckeyboard registers its methods on this class path.
 */
public class BinaryDictionary {

    static {
        try {
            System.loadLibrary("jni_pckeyboard");
        } catch (UnsatisfiedLinkError ule) {
            android.util.Log.e("BinaryDictionary", "Could not load native library jni_pckeyboard");
        }
    }

    // These native methods are registered by JNI_OnLoad in the C++ layer
    public static native long openNative(ByteBuffer bb, int typedLetterMultiplier,
            int fullWordMultiplier, int dictSize);
    public static native void closeNative(long dict);
    public static native boolean isValidWordNative(long nativeData, char[] word, int wordLength);
    public static native int getSuggestionsNative(long dict, int[] inputCodes, int codesSize,
            char[] outputChars, int[] frequencies, int maxWordLength, int maxWords,
            int maxAlternatives, int skipPos, int[] nextLettersFrequencies, int nextLettersSize);
    public static native int getBigramsNative(long dict, char[] prevWord, int prevWordLength,
            int[] inputCodes, int inputCodesLength, char[] outputChars, int[] frequencies,
            int maxWordLength, int maxBigrams, int maxAlternatives);
}
