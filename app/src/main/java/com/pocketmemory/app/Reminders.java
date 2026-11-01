package com.pocketmemory.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONObject;

/** AlarmManager owns wakeups; the model and application process never run a reminder polling loop. */
public final class Reminders {
    static final String FIRE="com.pocketmemory.app.FIRE",DISMISS="com.pocketmemory.app.DISMISS",SNOOZE="com.pocketmemory.app.SNOOZE",WATER="com.pocketmemory.app.WATER",CHANGED="com.pocketmemory.app.ALARM_CHANGED";
    static final int RING_NOTIFICATION=901;
    static MemoryApp app(Context c){return (MemoryApp)c.getApplicationContext();}
    public static boolean notifications(Context c){return c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED&&c.getSystemService(NotificationManager.class).areNotificationsEnabled();}
    public static boolean exact(Context c){return c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    public static boolean fullscreen(Context c){return Build.VERSION.SDK_INT<34||c.getSystemService(NotificationManager.class).canUseFullScreenIntent();}
    static PendingIntent fireIntent(Context c,JSONObject r){Intent i=new Intent(c,ReminderReceiver.class).setAction(FIRE).setData(Uri.parse("pocketmemory://reminder/"+r.optString("id"))).putExtra("id",r.optString("id")).putExtra("revision",r.optInt("revision"));return PendingIntent.getBroadcast(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static void cancel(Context c,String id){Intent i=new Intent(c,ReminderReceiver.class).setAction(FIRE).setData(Uri.parse("pocketmemory://reminder/"+id));PendingIntent p=PendingIntent.getBroadcast(c,0,i,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);if(p!=null){c.getSystemService(AlarmManager.class).cancel(p);p.cancel();}}
    public static void schedule(Context c,String id){JSONObject r=app(c).life.reminder(id);if(r==null||r.optInt("enabled")==0){cancel(c,id);return;}AlarmManager alarms=c.getSystemService(AlarmManager.class);PendingIntent fire=fireIntent(c,r);long at=Math.max(System.currentTimeMillis()+500,r.optLong("next_at"));
        try{if(r.optString("mode").equals("ring")&&exact(c))alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(at,PendingIntent.getActivity(c,id.hashCode(),new Intent(c,MainActivity.class).putExtra("screen","Today"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)),fire);else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,fire);}catch(SecurityException revoked){alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,fire);}
    }
    public static void reschedule(Context c){reschedule(c,false);}
    public static void reschedule(Context c,boolean clockChanged){reschedule(c,clockChanged,false);}
    static void restoreSchedules(Context c,boolean clockChanged){reschedule(c,clockChanged,true);}
    private static void reschedule(Context c,boolean clockChanged,boolean recovery){MemoryApp app=app(c);if(app.maintenance)return;long now=System.currentTimeMillis();for(JSONObject r:app.life.activeReminders()){
        try{
            // Recovery must not turn a missed alarm into an immediate post-boot service start.
            if((recovery&&r.optLong("next_at")<=now)||(r.optString("repeat_rule").equals("none")&&now-r.optLong("next_at")>900000)){
                JSONObject missed=app.life.fire(r.optString("id"),r.optInt("revision"),now);
                if(missed!=null)app.life.eventState(missed.optString("id"),"missed");
                schedule(c,r.optString("id"));continue;
            }
            if((r.optLong("next_at")<now||clockChanged)&&!r.optString("repeat_rule").equals("none"))app.life.db().execSQL("UPDATE reminders SET next_at=?,revision=revision+1 WHERE id=?",new Object[]{app.life.next(r,now),r.optString("id")});
            schedule(c,r.optString("id"));
        }catch(Exception e){app.life.enable(r.optString("id"),false);}
    }}
    static void channels(Context c){NotificationManager manager=c.getSystemService(NotificationManager.class);NotificationChannel gentle=new NotificationChannel("daily_reminders","Daily reminders",NotificationManager.IMPORTANCE_DEFAULT);gentle.setDescription("Water and gentle personal reminders");manager.createNotificationChannel(gentle);NotificationChannel ring=new NotificationChannel("wake_alarms","Wake-up alarms",NotificationManager.IMPORTANCE_HIGH);ring.setDescription("Ringing reminders that you explicitly enable");ring.setSound(null,null);ring.enableVibration(false);manager.createNotificationChannel(ring);}
    static PendingIntent action(Context c,String event,String action){return PendingIntent.getBroadcast(c,0,new Intent(c,ReminderReceiver.class).setAction(action).setData(Uri.parse("pocketmemory://event/"+event+"/"+action)).putExtra("event",event),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static int notificationId(String event){return 1000+(event.hashCode()&0x3fffffff);}
    static Notification notification(Context c,JSONObject event,boolean ringing){
        channels(c);String id=event.optString("id");Intent open=new Intent(c,RingingActivity.class).putExtra("event",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content=PendingIntent.getActivity(c,0,open.setData(Uri.parse("pocketmemory://alarm/"+id)),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=new Notification.Builder(c,ringing?"wake_alarms":"daily_reminders").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setColor(Y2k.PINK).setContentTitle(Y2k.reminderLabel(event.optString("kind"),event.optString("title"))).setContentText(event.optString("body").isBlank()?"Time for your reminder":event.optString("body")).setStyle(new Notification.BigTextStyle().bigText(event.optString("body"))).setCategory(ringing?Notification.CATEGORY_ALARM:Notification.CATEGORY_REMINDER).setContentIntent(content).setVisibility(Notification.VISIBILITY_PRIVATE).setOngoing(ringing).setAutoCancel(!ringing).setDeleteIntent(action(c,id,DISMISS));
        if(ringing&&fullscreen(c))b.setFullScreenIntent(content,true);
        if(event.optString("kind").equals("water"))b.addAction(new Notification.Action.Builder(null,"Log water",action(c,id,WATER)).build());
        b.addAction(new Notification.Action.Builder(null,"Snooze 10 min",action(c,id,SNOOZE)).build()).addAction(new Notification.Action.Builder(null,"Dismiss",action(c,id,DISMISS)).build());return b.build();
    }
    static void fire(Context c,String id,int revision){MemoryApp app=app(c);if(app.maintenance)return;JSONObject event=app.life.fire(id,revision,System.currentTimeMillis());if(event==null)return;schedule(c,id);
        if(!notifications(c)){app.life.eventState(event.optString("id"),"missed");c.sendBroadcast(new Intent(CHANGED).setPackage(c.getPackageName()));app.changed();return;}
        if(event.optString("mode").equals("ring")){try{c.startForegroundService(new Intent(c,ReminderService.class).putExtra("event",event.optString("id")));}catch(RuntimeException denied){app.life.eventState(event.optString("id"),"missed");c.getSystemService(NotificationManager.class).notify(notificationId(event.optString("id")),notification(c,event,false));}}
        else c.getSystemService(NotificationManager.class).notify(notificationId(event.optString("id")),notification(c,event,false));c.sendBroadcast(new Intent(CHANGED).setPackage(c.getPackageName()));app.changed();
    }
    public static void act(Context c,String eventId,String action){MemoryApp app=app(c);if(app.maintenance)return;JSONObject event=app.life.event(eventId);if(event==null||!java.util.Set.of("pending","ringing").contains(event.optString("state")))return;
        if(action.equals(WATER)){app.life.water(app.life.setting("water_cup",250));app.life.eventState(eventId,"dismissed");}
        else if(action.equals(SNOOZE)){app.life.snooze(eventId);schedule(c,event.optString("reminder_id"));}
        else app.life.eventState(eventId,"dismissed");
        c.getSystemService(NotificationManager.class).cancel(notificationId(eventId));ReminderService.ack(eventId);c.sendBroadcast(new Intent(CHANGED).setPackage(c.getPackageName()));app.changed();
    }
    public static String readiness(Context c){return (notifications(c)?"Notifications on":"Notifications off")+" · "+(exact(c)?"Precise alarms on":"Precise alarms off")+" · "+(fullscreen(c)?"Lock-screen alerts on":"Lock-screen alerts off");}
    public static void setup(MainActivity a){
        if(!notifications(a)){boolean asked=a.getPreferences(0).getBoolean("asked_notifications",false);if(asked&&!a.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))a.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.getPackageName()));else{a.getPreferences(0).edit().putBoolean("asked_notifications",true).apply();a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},MainActivity.NOTIFICATIONS);}return;}
        if(!exact(a)){a.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+a.getPackageName())));return;}
        if(!fullscreen(a)&&Build.VERSION.SDK_INT>=34){a.startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+a.getPackageName())));return;}
        a.toast("Reminders are ready. Alarm volume is controlled in Android Sound settings.");
    }
}
