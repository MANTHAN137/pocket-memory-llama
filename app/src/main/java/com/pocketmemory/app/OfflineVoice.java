package com.pocketmemory.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.*;
import java.io.IOException;
import java.util.Arrays;

public final class OfflineVoice {
    static { System.loadLibrary("pocketvoice"); }
    public static native String transcribe(String model,float[] samples,String language) throws IOException;
    public static native void cancel();
    public interface RecordingResult { void ready(float[] samples); void failed(String error); }
    private volatile boolean recording;
    private AudioRecord recorder;
    public boolean recording() {return recording;}
    public void start(Context context,RecordingResult result) {
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){result.failed("Microphone permission is required to record.");return;}
        int buffer=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
        if(buffer<=0){result.failed("This microphone does not support the recording format.");return;}
        try {
            recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(buffer,8192));
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Microphone is unavailable.");
            recorder.startRecording();recording=true;
        }catch(Exception e){release();result.failed("Could not start the microphone.");return;}
        new Thread(()->{
            float[] data=new float[16000*30];int count=0;short[] block=new short[2048];String error=null;
            try {
                while(recording&&count<data.length){int n=recorder.read(block,0,Math.min(block.length,data.length-count));if(n<0){if(recording)error="Microphone recording failed.";break;}for(int i=0;i<n;i++)data[count++]=block[i]/32768f;}
            }catch(Exception e){error="Microphone recording failed.";}
            finally{recording=false;release();}
            if(error!=null)result.failed(error);else if(count<4000)result.failed("Recording was too short. Please try again.");else result.ready(Arrays.copyOf(data,count));
        },"offline-microphone").start();
    }
    public void stop(){recording=false;}
    private void release(){if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}recorder.release();recorder=null;}}
}
