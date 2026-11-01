package com.pocketmemory.app;

import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** One bounded native reply. Emit the first visible text immediately, then at most 25 updates/s. */
final class ChatStream {
    private static final String[] MARKERS={"<think>","</think>","<|im_end|>","<|eot_id|>"};
    private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    private final Consumer<String> reply;
    private final LongSupplier clock;
    private String visible="",emitted="";
    private long lastEmission;
    private boolean blocked;

    ChatStream(Consumer<String> reply){this(reply,SystemClock::elapsedRealtime);}
    ChatStream(Consumer<String> reply,LongSupplier clock){this.reply=reply;this.clock=clock;}

    /** Returns true when the local output rules have replaced this reply with a refusal. */
    boolean accept(byte[] token){
        if(blocked)return true;
        if(token.length>AppEngine.PARTIAL_BYTES-bytes.size())throw new IllegalStateException("TOKEN_LIMIT: Reply buffer is full.");
        bytes.write(token,0,token.length);
        String clean=visibleText(decode(bytes.toByteArray())),checked=SafetyGate.output(clean);
        blocked=!checked.equals(clean);visible=checked;
        emit(blocked);return blocked;
    }

    private static String visibleText(String raw){
        // Tokens can split a UTF-8 character or a control marker. Neither should flash in chat.
        for(String marker:MARKERS)for(int length=1;length<marker.length();length++){
            if(raw.endsWith(marker.substring(0,length))){raw=raw.substring(0,raw.length()-length);break;}
        }
        return AppEngine.clean(raw).replace("</think>","");
    }

    boolean blocked(){return blocked;}
    String flush(){emit(true);return visible;}
    String finish(String raw){
        if(!blocked){String clean=visibleText(raw);visible=SafetyGate.output(clean);blocked=!visible.equals(clean);}
        emit(true);return visible;
    }
    private void emit(boolean force){
        long now=clock.getAsLong();
        if(!visible.equals(emitted)&&(force||emitted.isEmpty()||now-lastEmission>=40)){
            emitted=visible;lastEmission=now;if(reply!=null)reply.accept(visible);
        }
    }
    static String decode(byte[] value){
        // Strict decoding never invents replacement characters. An incomplete trailing code point waits.
        for(int trim=0;trim<=Math.min(3,value.length);trim++)try{
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(value,0,value.length-trim)).toString();
        }catch(CharacterCodingException ignored){}
        return "";
    }
}
