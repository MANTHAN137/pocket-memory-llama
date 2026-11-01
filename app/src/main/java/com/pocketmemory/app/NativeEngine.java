package com.pocketmemory.app;

import java.io.IOException;

public final class NativeEngine {
    static { System.loadLibrary("pocketmemory"); }
    public interface TokenListener { void onToken(byte[] utf8); }
    public static native void load(String path) throws IOException;
    public static native String generate(String system, String user, int maxTokens, TokenListener listener) throws IOException;
    public static native int promptTokens(String system,String user) throws IOException;
    public static native void configure(int timeoutMs,float temperature,float repeatPenalty);
    public static native float[] embed(String path, String text) throws IOException;
    public static native void cancel();
    public static native void unload();
    private NativeEngine() {}
}
