package com.pocketmemory.app;

import android.os.SystemClock;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Loopback only; one-use bootstrap, expiring bearer session, fixed tools and bounded jobs. */
final class CompanionServer implements AutoCloseable {
    final MemoryApp app;
    final ServerSocket socket;
    final String origin;
    final long expires=SystemClock.elapsedRealtime()+900000;
    final ThreadPoolExecutor http=new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4));
    final Map<String,Job> jobs=new LinkedHashMap<>();
    final Map<String,Job> fingerprints=new HashMap<>();
    final SecureRandom random=new SecureRandom();
    String bootstrap="",session="",chat="";
    long bootstrapExpires=0;
    int operations=0,generations=0;
    final AtomicInteger requests=new AtomicInteger();
    volatile boolean closed=false;
    CompanionServer(MemoryApp app)throws Exception{
        this.app=app;socket=new ServerSocket();socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),8);origin="http://127.0.0.1:"+socket.getLocalPort();
        Thread listener=new Thread(()->{while(!closed){try{Socket peer=socket.accept();try{http.execute(()->handle(peer));}catch(RejectedExecutionException e){peer.close();}}catch(IOException e){if(!closed)close();}}},"pocket-companion");listener.setDaemon(true);listener.start();
    }
    String token(){byte[] b=new byte[32];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    synchronized String openLink(){bootstrap=token();bootstrapExpires=SystemClock.elapsedRealtime()+60000;return origin+"/#"+bootstrap;}
    static boolean equal(String a,String b){return !a.isEmpty()&&MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
    synchronized void authorize(Map<String,String> headers)throws Exception{if(closed||SystemClock.elapsedRealtime()>expires||session.isEmpty()||!equal("Bearer "+session,headers.getOrDefault("authorization","")))throw new Failure(401,"Session expired. Open the companion from Pocket Memory again.");}
    static final class Failure extends Exception {final int status;Failure(int status,String message){super(message);this.status=status;}}
    static final class Job {
        final String id,kind,input;volatile String state="running",error="";volatile JSONObject result;
        Job(String id,String kind,String input){this.id=id;this.kind=kind;this.input=input;}
    }
    synchronized Job begin(String kind,String input)throws Exception{
        if(!Set.of("ask_memory","chat_with_llama","search_web").contains(kind))throw new Failure(400,"Unknown operation.");
        if(input.isBlank()||input.length()>600)throw new Failure(400,"Use a question up to 600 characters.");
        if(kind.equals("search_web")&&input.length()>300)throw new Failure(400,"Web questions can have up to 300 characters.");
        String fingerprint=kind+":"+input.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();
        Job previous=fingerprints.get(fingerprint);if(previous!=null)return previous;
        if(generations>=6||operations>=24)throw new Failure(429,"This session reached its request limit. Open a new session in the app.");
        if(app.busy)throw new Failure(409,"Llama is busy. Wait or stop the current reply.");
        app.busy=true;generations++;operations++;Job job=new Job(token(),kind,input);jobs.put(job.id,job);fingerprints.put(fingerprint,job);
        app.worker.execute(()->run(job));return job;
    }
    void run(Job job){
        try{
            if(closed||SystemClock.elapsedRealtime()>expires)throw new IOException("Session expired.");
            if(chat.isEmpty())chat=app.chats.create();
            final List<MemoryStore.Passage> evidence=new ArrayList<>();
            AppEngine.Reply reply=new AppEngine.Reply(){public void text(String value){}public void sources(List<MemoryStore.Passage> values){evidence.clear();evidence.addAll(values);}};
            String answer=job.kind.equals("ask_memory")?app.engine.ask(job.input,reply):job.kind.equals("search_web")?app.engine.webChat(chat,job.input,reply):app.engine.chat(chat,job.input,reply);
            JSONArray sources=new JSONArray();for(int i=0;i<Math.min(6,evidence.size());i++){MemoryStore.Passage p=evidence.get(i);sources.put(new JSONObject().put("title",p.title).put("text",p.text.substring(0,Math.min(1800,p.text.length()))));}
            job.result=new JSONObject().put("answer",answer).put("sources",sources);job.state="complete";
        }catch(Exception|UnsatisfiedLinkError e){job.error=MainActivity.friendly(e);job.state="error";}
        finally{app.busy=false;app.workStatus="";app.changed();}
    }
    synchronized void stop(String id)throws Exception{Job job=jobs.get(id);if(job==null)throw new Failure(404,"Reply not found.");if(job.state.equals("running"))app.engine.cancel();}
    void handle(Socket peer){
        try{peer.setSoTimeout(5000);InputStream in=peer.getInputStream();long deadline=SystemClock.elapsedRealtime()+10000;
            String first=line(in,deadline);String[] request=first.split(" ");if(request.length!=3||!request[2].equals("HTTP/1.1"))throw new Failure(400,"Invalid request.");String method=request[0],path=request[1];
            if(!Set.of("GET","POST").contains(method)||path.length()>2048)throw new Failure(405,"Method not allowed.");
            Map<String,String> headers=new HashMap<>();int headerBytes=first.length();
            for(int n=0;n<40;n++){String value=line(in,deadline);headerBytes+=value.length();if(headerBytes>8192)throw new Failure(431,"Headers too large.");if(value.isEmpty())break;int colon=value.indexOf(':');if(colon<1)throw new Failure(400,"Invalid header.");String key=value.substring(0,colon).toLowerCase(Locale.ROOT);if(headers.put(key,value.substring(colon+1).trim())!=null)throw new Failure(400,"Duplicate header.");if(n==39)throw new Failure(431,"Too many headers.");}
            if(!headers.getOrDefault("host","").equals("127.0.0.1:"+socket.getLocalPort())||headers.containsKey("transfer-encoding"))throw new Failure(403,"Invalid origin.");
            String source=headers.get("origin");if(source!=null&&!source.equals(origin))throw new Failure(403,"Cross-origin access is blocked.");
            if(!path.equals("/api/stop")&&requests.incrementAndGet()>512)throw new Failure(429,"Session request limit reached.");
            int length=Integer.parseInt(headers.getOrDefault("content-length","0"));if(length<0||length>8192)throw new Failure(413,"Request too large.");
            byte[] body=new byte[length];int used=0;while(used<length){if(SystemClock.elapsedRealtime()>deadline)throw new Failure(408,"Request timed out.");int count=in.read(body,used,length-used);if(count<0)throw new Failure(400,"Incomplete request.");used+=count;}
            if(method.equals("POST")&&!origin.equals(source))throw new Failure(403,"A same-origin request is required.");
            if(method.equals("GET")&&Set.of("/","/companion.js","/companion.css").contains(path)){
                String asset=path.equals("/")?"companion/index.html":"companion"+path;try(InputStream data=app.getAssets().open(asset)){send(peer,200,path.endsWith(".js")?"text/javascript":path.endsWith(".css")?"text/css":"text/html",data.readAllBytes());}return;
            }
            JSONObject payload=length==0?new JSONObject():new JSONObject(new String(body,StandardCharsets.UTF_8));
            if(method.equals("POST")&&path.equals("/session")){
                synchronized(this){if(SystemClock.elapsedRealtime()>bootstrapExpires||!equal(bootstrap,payload.optString("code")))throw new Failure(401,"Open the companion from the app again.");bootstrap="";bootstrapExpires=0;if(session.isEmpty())session=token();}
                json(peer,200,new JSONObject().put("session",session));return;
            }
            authorize(headers);
            if(method.equals("GET")&&path.equals("/api/model")){
                Catalog.Model m=app.catalog.get(app.activeId());json(peer,200,new JSONObject().put("name",m==null?"Llama":m.name).put("ready",m!=null&&m.installed(app)).put("sessionMinutes",Math.max(0,(expires-SystemClock.elapsedRealtime())/60000)));return;
            }
            if(method.equals("POST")&&path.equals("/api/memories")){
                if(payload.length()!=1||!payload.has("query"))throw new Failure(400,"A search query is required.");String query=payload.getString("query").trim();if(query.length()>120)throw new Failure(400,"Use a shorter search.");synchronized(this){if(++operations>24)throw new Failure(429,"Session operation limit reached.");}
                JSONArray rows=new JSONArray();for(MemoryStore.Note note:app.store.notes(query)){if(rows.length()==5)break;rows.put(new JSONObject().put("title",note.title).put("text",note.body.substring(0,Math.min(2000,note.body.length()))));}json(peer,200,new JSONObject().put("memories",rows));return;
            }
            if(method.equals("POST")&&path.equals("/api/jobs")){
                if(payload.length()!=2||!payload.has("kind")||!payload.has("input"))throw new Failure(400,"Invalid operation fields.");Job job=begin(payload.getString("kind"),payload.getString("input").trim());json(peer,202,new JSONObject().put("id",job.id));return;
            }
            if(method.equals("POST")&&path.equals("/api/stop")){stop(payload.getString("id"));json(peer,200,new JSONObject().put("stopped",true));return;}
            if(method.equals("GET")&&path.startsWith("/api/jobs/")){Job job; synchronized(this){job=jobs.get(path.substring(10));}if(job==null)throw new Failure(404,"Reply not found.");JSONObject result=new JSONObject().put("state",job.state);if(job.result!=null)result.put("result",job.result);if(!job.error.isEmpty())result.put("error",job.error);json(peer,200,result);return;}
            throw new Failure(404,"Not found.");
        }catch(Exception error){try{int status=error instanceof Failure?((Failure)error).status:400;json(peer,status,new JSONObject().put("error",error instanceof Failure?error.getMessage():"Request could not be processed."));}catch(Exception ignored){}}finally{try{peer.close();}catch(IOException ignored){}}
    }
    static String line(InputStream in,long deadline)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();while(out.size()<8192){if(SystemClock.elapsedRealtime()>deadline)throw new Failure(408,"Request timed out.");int b=in.read();if(b<0)throw new EOFException();if(b=='\n')return out.toString(StandardCharsets.US_ASCII).replace("\r","");out.write(b);}throw new Failure(431,"Line too large.");}
    void json(Socket peer,int status,JSONObject body)throws IOException{send(peer,status,"application/json",body.toString().getBytes(StandardCharsets.UTF_8));}
    void send(Socket peer,int status,String type,byte[] bytes)throws IOException{
        String reason=status==200?"OK":status==202?"Accepted":"Error";
        String headers="HTTP/1.1 "+status+" "+reason+"\r\nContent-Type: "+type+"; charset=utf-8\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'\r\n\r\n";
        OutputStream out=peer.getOutputStream();out.write(headers.getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
    }
    @Override public synchronized void close(){if(closed)return;closed=true;for(Job job:jobs.values())if(job.state.equals("running"))app.engine.cancel();bootstrap="";session="";try{socket.close();}catch(IOException ignored){}http.shutdownNow();}
}
