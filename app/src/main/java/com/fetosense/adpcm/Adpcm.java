package com.fetosense.adpcm;

public class Adpcm {

    static {
        System.loadLibrary("fetosenseadpcm");
    }

    public static native int decode(byte[] inArr, byte[] outArr, int ratio, float opRate);

    public static native int decodec(byte[] inArr, byte[] outArr, int ratio, float opRate);

}
