package com.pocketmemory.app;
import java.io.IOException;

/** Scoped local Llama web-answer generation with constrained JSON output. */
public final class LocalLlama {
    static { System.loadLibrary("pocketmemory"); }
    public static native long newJob();
    public static native void cancel(long ticket);
    public static native String answer(String path,String system,String input,int sources,int timeoutMs,long ticket)throws IOException;
    private LocalLlama(){}
}
