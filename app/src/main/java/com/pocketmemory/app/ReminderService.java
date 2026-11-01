package com.pocketmemory.app;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import org.json.JSONObject;
import java.util.*;

/** Audio exists only while an explicit alarm is ringing; every ring has a two-minute stop limit. */
public final class ReminderService extends Service {
    private static volatile ReminderService instance;
    final Handler handler=new Handler(Looper.getMainLooper());final LinkedHashSet<String> queue=new LinkedHashSet<>();
    String current="";MediaPlayer player;Vibrator vibrator;PowerManager.WakeLock wake;AudioManager audio;AudioFocusRequest focus;
    final Runnable timeout=()->{if(!current.isEmpty()){Reminders.app(this).life.eventState(current,"missed");sendBroadcast(new Intent(Reminders.CHANGED).setPackage(getPackageName()));finishCurrent();}};
    static void ack(String event){ReminderService service=instance;if(service!=null)service.handler.post(()->{if(service.current.equals(event))service.finishCurrent();else service.queue.remove(event);});}
    public static boolean ringing(String event){ReminderService service=instance;return service!=null&&service.current.equals(event);}
    static boolean audioPlaying(String event){ReminderService service=instance;try{return service!=null&&service.current.equals(event)&&service.player!=null&&service.player.isPlaying();}catch(IllegalStateException e){return false;}}
    @Override public void onCreate(){super.onCreate();instance=this;}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(Reminders.app(this).maintenance){stopSelf();return START_NOT_STICKY;}String id=intent==null?null:intent.getStringExtra("event");JSONObject event=id==null?null:Reminders.app(this).life.event(id);if(event==null||!Set.of("pending","ringing").contains(event.optString("state"))){if(current.isEmpty())stopSelf();return START_NOT_STICKY;}
        if(current.isEmpty())start(event);else if(!current.equals(id)&&queue.size()<20)queue.add(id);return START_NOT_STICKY;
    }
    void start(JSONObject event){
        current=event.optString("id");Reminders.app(this).life.eventState(current,"ringing");startForeground(Reminders.RING_NOTIFICATION,Reminders.notification(this,event,true));
        wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":ring");wake.acquire(125000);
        audio=getSystemService(AudioManager.class);AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).setOnAudioFocusChangeListener(change->{}).build();audio.requestAudioFocus(focus);
        try{player=new MediaPlayer();player.setAudioAttributes(attrs);android.net.Uri sound=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);if(sound==null)sound=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);player.setDataSource(this,sound);player.setLooping(true);player.prepare();player.start();}catch(Exception ignored){if(player!=null){player.release();player=null;}}
        vibrator=getSystemService(VibratorManager.class).getDefaultVibrator();if(vibrator.hasVibrator())vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,350,700,350,1200},0),attrs);
        handler.postDelayed(timeout,120000);
        if(Reminders.app(this).visibleActivities>0)try{startActivity(new Intent(this,RingingActivity.class).putExtra("event",current).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));}catch(RuntimeException ignored){}
    }
    void releaseRing(){handler.removeCallbacks(timeout);if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}if(vibrator!=null)vibrator.cancel();if(wake!=null&&wake.isHeld())wake.release();if(audio!=null&&focus!=null)audio.abandonAudioFocusRequest(focus);}
    void finishCurrent(){releaseRing();current="";Iterator<String> iterator=queue.iterator();while(iterator.hasNext()){String next=iterator.next();iterator.remove();JSONObject e=Reminders.app(this).life.event(next);if(e!=null&&e.optString("state").equals("pending")){start(e);return;}}stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onDestroy(){releaseRing();if(!current.isEmpty()){JSONObject e=Reminders.app(this).life.event(current);if(e!=null&&e.optString("state").equals("ringing"))Reminders.app(this).life.eventState(current,"missed");}if(instance==this)instance=null;super.onDestroy();}
}
