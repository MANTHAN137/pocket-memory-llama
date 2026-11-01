package com.pocketmemory.app;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/** Only explicit Send/Retry contacts the publisher. No model prompt, notes, files or identifier is attached automatically. */
public final class ReportStore {
    public static final Set<String> REASONS=Set.of("Unsafe content","Hate or harassment","Sexual content","Child safety","Incorrect answer","Privacy concern","Other");
    private final AtomicFile file;
    public ReportStore(Context c){file=new AtomicFile(new File(c.getFilesDir(),"response-reports.json"));}
    synchronized JSONArray list(){try(InputStream in=file.openRead()){return new JSONArray(new String(in.readAllBytes(),StandardCharsets.UTF_8));}catch(Exception e){return new JSONArray();}}
    private synchronized void save(JSONArray rows)throws IOException{FileOutputStream stream=null;try{stream=file.startWrite();stream.write(rows.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(stream);}catch(IOException e){if(stream!=null)file.failWrite(stream);throw e;}}
    synchronized JSONObject create(String response,String prompt,String reason)throws Exception{
        if(!REASONS.contains(reason)||response.isBlank()||response.length()>8000||prompt.length()>4000)throw new IOException("Choose a reason and keep the response under 8,000 characters.");
        JSONArray rows=list();if(rows.length()>=20)throw new IOException("There are 20 saved reports. Remove an older report before adding one.");
        byte[] token=new byte[32];new SecureRandom().nextBytes(token);
        JSONObject row=new JSONObject().put("id",UUID.randomUUID().toString()).put("response",response).put("prompt",prompt).put("reason",reason).put("version",BuildConfig.VERSION_NAME).put("safety_version",SafetyGate.VERSION).put("created",System.currentTimeMillis()).put("delete_token",Base64.getUrlEncoder().withoutPadding().encodeToString(token)).put("state","pending");
        rows.put(row);save(rows);return row;
    }
    synchronized void remove(String id)throws IOException{JSONArray next=new JSONArray(),rows=list();for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(!row.optString("id").equals(id))next.put(row);}save(next);}
    synchronized void clear(){file.delete();}
    private synchronized void state(String id,String state)throws Exception{JSONArray rows=list();for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(id))rows.getJSONObject(i).put("state",state);save(rows);}
    static URL endpoint(String configured)throws IOException{
        try{URI uri=new URI(configured);String host=uri.getHost();if(!"https".equals(uri.getScheme())||host==null||host.equals("localhost")||host.endsWith(".local")||host.equals("127.0.0.1")||uri.getRawUserInfo()!=null||uri.getRawFragment()!=null||uri.getRawQuery()!=null)throw new Exception();return uri.toURL();}
        catch(Exception e){throw new IOException("Report delivery is not configured in this preview. The report stays on this phone.");}
    }
    interface Transport{String request(URL url,String method,byte[] bytes,String token)throws IOException;}
    private static final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"report-deadline");t.setDaemon(true);return t;});
    private static final Transport HTTPS=(url,method,bytes,token)->{
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        ScheduledFuture<?> timeout=deadlines.schedule(c::disconnect,30,TimeUnit.SECONDS);
        try{c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setRequestMethod(method);c.setRequestProperty("Accept","application/json");
            if(!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
            if(bytes!=null){if(bytes.length>48000)throw new IOException("Report is too large. Shorten the response.");c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setFixedLengthStreamingMode(bytes.length);c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
            int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("The report service could not confirm this request (HTTP "+code+"). Nothing will be retried automatically.");
            try(InputStream in=c.getInputStream()){byte[] receipt=in.readNBytes(4097);if(receipt.length>4096)throw new IOException("Invalid report receipt.");return new String(receipt,StandardCharsets.UTF_8);}
        }finally{timeout.cancel(false);c.disconnect();}
    };
    void send(JSONObject report)throws Exception{send(report,BuildConfig.REPORTS_URL,HTTPS);}
    void send(JSONObject report,String configured,Transport transport)throws Exception{
        JSONObject payload=new JSONObject();for(String key:new String[]{"id","response","prompt","reason","version","safety_version","created","delete_token"})payload.put(key,report.get(key));
        URL url=endpoint(configured);state(report.getString("id"),"unconfirmed");JSONObject receipt=new JSONObject(transport.request(url,"POST",payload.toString().getBytes(StandardCharsets.UTF_8),""));if(!receipt.optBoolean("received")||!receipt.optString("id").equals(report.getString("id")))throw new IOException("The report service did not return a valid receipt.");state(report.getString("id"),"sent");
    }
    void deleteRemote(JSONObject report)throws Exception{
        if(!report.optString("state").equals("pending")){URL root=endpoint(BuildConfig.REPORTS_URL);JSONObject receipt=new JSONObject(HTTPS.request(new URL(root.toString()+"/"+report.getString("id")),"DELETE",null,report.getString("delete_token")));if(!receipt.optBoolean("deleted")||!receipt.optString("id").equals(report.getString("id")))throw new IOException("Report deletion was not confirmed.");}
        remove(report.getString("id"));
    }
}
