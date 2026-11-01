package com.pocketmemory.app;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;
import android.os.StatFs;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModelDownloadService extends Service {
    private final AtomicBoolean paused=new AtomicBoolean(false);
    private MemoryApp app;
    private volatile HttpURLConnection connection;
    @Override public void onCreate() { super.onCreate(); app=(MemoryApp)getApplication(); }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null) { stopSelf(); return START_NOT_STICKY; }
        if("pause".equals(intent.getAction())) { paused.set(true);app.downloadMessage="Pausing…";app.changed(); if(connection!=null)connection.disconnect(); return START_NOT_STICKY; }
        String id=intent.getStringExtra("id"); Catalog.Model model=app.catalog.get(id);
        if(model==null) { stopSelf(); return START_NOT_STICKY; }
        if(app.maintenance||!PrivacyUi.accepted(this)||!ModelTerms.accepted(this,model)){app.downloadMessage="Review setup and the selected model’s terms before downloading.";app.changed();stopSelf();return START_NOT_STICKY;}
        if(!app.access.allowed(model)){app.downloadMessage=model.available()?"Unlock this model before downloading it.":model.unavailableReason;app.changed();stopSelf();return START_NOT_STICKY;}
        if(!app.downloadId.isEmpty()) return START_NOT_STICKY;
        app.downloadId=id; app.downloadMessage="Connecting to Hugging Face…"; paused.set(false);app.changed();
        var nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("models","Model downloads",NotificationManager.IMPORTANCE_LOW));
        startForeground(7,notification(model,0,"Starting download"));
        new Thread(()->download(model),"model-download").start();
        return START_NOT_STICKY;
    }
    private Notification notification(Catalog.Model model,int progress,String message) {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).putExtra("screen","Models"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pause=PendingIntent.getService(this,1,new Intent(this,ModelDownloadService.class).setAction("pause"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"models").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(model.name).setContentText(message).setContentIntent(open).setOngoing(true)
            .setProgress(100,progress,false).addAction(new Notification.Action.Builder(null,"Pause",pause).build()).build();
    }
    private void progress(Catalog.Model model,long bytes,String status) {
        app.downloadBytes=bytes; app.downloadMessage=status; app.changed();
        getSystemService(NotificationManager.class).notify(7,notification(model,(int)(100*bytes/model.bytes),status));
    }
    private HttpURLConnection open(String url,long offset) throws IOException {
        for(int redirects=0;redirects<8;redirects++) {
            URL u=new URL(url); if(!"https".equalsIgnoreCase(u.getProtocol()))throw new IOException("Only HTTPS model downloads are allowed.");
            HttpURLConnection c=(HttpURLConnection)u.openConnection(); connection=c;
            c.setConnectTimeout(20000); c.setReadTimeout(30000); c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","PocketMemory/0.1"); c.setRequestProperty("Accept-Encoding","identity");
            if(offset>0)c.setRequestProperty("Range","bytes="+offset+"-");
            int code=c.getResponseCode();
            if(code==301||code==302||code==303||code==307||code==308) {
                String next=c.getHeaderField("Location"); c.disconnect(); if(next==null)throw new IOException("Missing download redirect.");
                url=new URL(u,next).toString(); continue;
            }
            if(code!=200&&code!=206) { c.disconnect();throw new IOException("Hugging Face returned HTTP "+code+". Tap Resume to retry."); }
            return c;
        }
        throw new IOException("Too many download redirects.");
    }
    private void download(Catalog.Model model) {
        File part=model.part(this);
        try {
            long offset=part.exists()?part.length():0;
            if(offset>model.bytes) { Files.delete(part.toPath()); offset=0; }
            if(new StatFs(Catalog.directory(this).getPath()).getAvailableBytes()<model.bytes-offset+100_000_000L)
                throw new IOException("Free up storage before downloading this model.");
            if(offset<model.bytes) {
                HttpURLConnection c=open(model.url,offset);
                if(c.getResponseCode()==200) offset=0;
                if(c.getResponseCode()==206) {
                    String range=c.getHeaderField("Content-Range");
                    if(range==null||!range.startsWith("bytes "+offset+"-"))throw new IOException("Server returned an invalid resume position.");
                }
                long length=c.getContentLengthLong();
                if(length>=0&&length!=model.bytes-offset)throw new IOException("The server returned an unexpected file size.");
                try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(part,offset>0)) {
                    byte[] buffer=new byte[131072]; long done=offset,last=0; int n;
                    while((n=in.read(buffer))!=-1) {
                        if(paused.get())throw new InterruptedIOException("Paused");
                        if(app.maintenance||!app.access.allowed(model))throw new IOException("Model access changed. Restore or unlock it before resuming.");
                        if(done+n>model.bytes)throw new IOException("Download exceeds expected size.");
                        out.write(buffer,0,n);done+=n;
                        if(System.currentTimeMillis()-last>500) { progress(model,done,"Downloading · "+(100*done/model.bytes)+"%"); last=System.currentTimeMillis(); }
                    }
                    out.getFD().sync();
                } finally { c.disconnect(); }
            }
            if(paused.get())throw new InterruptedIOException("Paused");
            progress(model,part.length(),"Verifying downloaded file…");
            verify(part,model);
            if(app.maintenance||!app.access.allowed(model))throw new IOException("Model access changed. Restore or unlock it before resuming.");
            Files.move(part.toPath(),model.file(this).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            ModelTerms.installNotices(this,model);Files.write(model.marker(this).toPath(),model.sha256.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            model.invalidateDisplay();
            app.downloadMessage=model.name+" is ready to use.";
            if(model.kind.equals("embedding")) app.worker.execute(()->app.engine.indexAll());
        } catch(Exception e) {
            app.downloadMessage=paused.get()?"Download paused. Tap Resume to continue.":(e.getMessage()==null?"Download interrupted. Tap Resume to retry.":e.getMessage());
        } finally {
            connection=null;app.main.post(()->{app.downloadId="";app.downloadBytes=0;app.changed();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();});
        }
    }
    public static void verify(File f,Catalog.Model model) throws Exception {
        if(f.length()!=model.bytes)throw new IOException("Incomplete model file. Resume the download.");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(f)) { byte[] buf=new byte[131072];int n;while((n=in.read(buf))!=-1)digest.update(buf,0,n); }
        StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.US,"%02x",b&255));
        if(!model.sha256.contentEquals(hex)) { Files.deleteIfExists(f.toPath());throw new IOException("File verification failed. Download it again."); }
    }
    @Override public void onTimeout(int startId,int type) { paused.set(true);if(connection!=null)connection.disconnect();stopSelf(); }
    @Override public void onDestroy() { paused.set(true);if(connection!=null)connection.disconnect();super.onDestroy(); }
}
