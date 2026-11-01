package com.pocketmemory.app;

import android.app.*;
import android.content.Intent;
import android.os.*;
import java.util.concurrent.*;

/** An explicit, short-lived, phone-local browser session. */
public final class CompanionService extends Service {
    static volatile CompanionServer server;
    private final ScheduledExecutorService expiry=Executors.newSingleThreadScheduledExecutor();
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){
        super.onCreate();NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("companion","Browser companion",NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,41,new Intent(this,CompanionService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open=PendingIntent.getActivity(this,42,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        startForeground(41,new Notification.Builder(this,"companion").setSmallIcon(android.R.drawable.ic_menu_info_details).setContentTitle("Pocket Memory companion").setContentText("Local browser access is on").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Turn off",stop).build()).build());
        try{server=new CompanionServer((MemoryApp)getApplication());((MemoryApp)getApplication()).companionRunning=true;expiry.schedule(()->{stopSelf();},15,TimeUnit.MINUTES);}catch(Exception e){stopSelf();}
    }
    @Override public int onStartCommand(Intent i,int flags,int id){if(i!=null&&"stop".equals(i.getAction()))stopSelf();return START_NOT_STICKY;}
    @Override public void onDestroy(){CompanionServer current=server;server=null;if(current!=null)current.close();((MemoryApp)getApplication()).companionRunning=false;expiry.shutdownNow();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public void onTimeout(int startId,int type){stopSelf();}
}
