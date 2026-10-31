package com.pocketmemory.app;
import java.io.IOException;

/** The only inference interface: a scoped job that returns constrained candidate-ID JSON. */
public final class LocalLlama {
    static { System.loadLibrary("pocketmemory"); }
    public static native long newJob();
    public static native void cancel(long ticket);
    public static native String rank(String path,String system,String input,int candidates,int timeoutMs,long ticket)throws IOException;
    private LocalLlama(){}
}
