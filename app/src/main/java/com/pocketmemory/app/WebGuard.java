package com.pocketmemory.app;

import android.os.SystemClock;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Authority and budgets are application-owned, never supplied by retrieved text. */
final class WebGuard {
    final AtomicBoolean cancelled;
    final long deadline;
    final Set<String> calls=new HashSet<>();
    int requests=0,toolCalls=0,bytes=0;
    WebGuard(AtomicBoolean stop,long duration){cancelled=stop;deadline=SystemClock.elapsedRealtime()+Math.min(180_000,Math.max(1,duration));}
    void check()throws IOException{if(cancelled.get())throw new IOException("Search stopped.");if(SystemClock.elapsedRealtime()>=deadline)throw new IOException("Search took too long. Try a shorter question.");}
    synchronized void request()throws IOException{check();if(++requests>4)throw new IOException("The connection reached its request limit.");}
    synchronized void tool(String name,String query)throws IOException{
        check();if(!name.equals("web_search_exa"))throw new IOException("That web tool is not allowed.");
        String key=name+":"+query.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
        if(!calls.add(key)||++toolCalls>1)throw new IOException("A repeated search was stopped.");
    }
    synchronized void bytes(int amount)throws IOException{check();bytes+=amount;if(bytes>196608)throw new IOException("The web response was too large.");}
    int remaining(){return (int)Math.max(1,Math.min(120_000,deadline-SystemClock.elapsedRealtime()));}
    static boolean publicHttps(String value){
        try{URI u=new URI(value);String h=u.getHost();if(!"https".equalsIgnoreCase(u.getScheme())||h==null||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))return false;
            h=h.toLowerCase(Locale.ROOT);return h.contains(".")&&!h.contains(":")&&!h.matches("[0-9.]+")&&!h.endsWith(".")&&!h.endsWith(".localhost")&&!h.endsWith(".local")&&!h.endsWith(".internal")&&!h.endsWith(".test")&&!h.equals("localhost")&&!h.contains("%")&&value.length()<=2048;
        }catch(Exception e){return false;}
    }
    static boolean injection(String value){String s=value.toLowerCase(Locale.ROOT);return s.matches("(?s).*ignore (all |any |the |previous )?(previous |prior |system )?instructions.*")||s.contains("system prompt")||s.contains("exfiltrat")||s.matches("(?s).*reveal.{0,40}(secret|password|token).*")||s.contains("call tools/call");}
}
