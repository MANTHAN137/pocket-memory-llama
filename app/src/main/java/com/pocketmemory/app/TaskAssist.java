package com.pocketmemory.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** One bounded, pageable task list. No model, polling, wake lock or notification per task. */
final class TaskAssist {
    static final String OFF="off",NOTIFICATION="notification",FLOATING="floating";
    static final String DONE="com.pocketmemory.app.TASK_DONE",HIDE="com.pocketmemory.app.TASK_HIDE",PAGE="com.pocketmemory.app.TASK_PAGE",STOP="com.pocketmemory.app.TASK_STOP";
    static final String CHANGED="com.pocketmemory.app.TASKS_CHANGED",CHANNEL="pending_tasks";
    static final int LIST_ID=910,FLOAT_ID=911,PERMISSION=42;
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("task_assist",0);}
    static String mode(Context c){return prefs(c).getString("mode",OFF);}
    static boolean overlayAllowed(Context c){return Settings.canDrawOverlays(c);}
    static String label(Context c){String mode=mode(c);return mode.equals(OFF)?"Off":mode.equals(FLOATING)?(!overlayAllowed(c)?"Floating · needs permission":!Reminders.notifications(c)?"Floating · notifications off":"Floating button"):prefs(c).getBoolean("hidden",false)?"Notification · hidden":Reminders.notifications(c)?"Notification list":"Notification · needs permission";}
    static void choose(MainActivity a){
        AlertDialog dialog=new ThemedDialog.Builder(a).setTitle("Keep tasks in view").setItems(new String[]{"Off","Notification list · check off here","Floating button · over other apps"},(d,i)->configure(a,new String[]{OFF,NOTIFICATION,FLOATING}[i])).setNegativeButton("Close",null).show();a.featureDialog=dialog;
    }
    static void configure(MainActivity a,String selected){
        if(!Set.of(OFF,NOTIFICATION,FLOATING).contains(selected))return;
        prefs(a).edit().putString("mode",selected).putBoolean("hidden",false).putInt("page",0).putBoolean("ask_notifications",!selected.equals(OFF)).apply();
        if(!selected.equals(FLOATING))a.stopService(new Intent(a,TaskBubbleService.class));
        if(selected.equals(FLOATING)&&!overlayAllowed(a)){
            a.featureDialog=new ThemedDialog.Builder(a).setTitle("Floating tasks").setMessage("Show a movable task button above other apps. Tap it to check off tasks. Android also shows a notification with a Stop button.")
                .setPositiveButton("Allow floating",(d,w)->{try{a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+a.getPackageName())));}catch(ActivityNotFoundException e){a.toast("Open Android Settings → Display over other apps → Pocket Memory.");}})
                .setNegativeButton("Not now",null).show();
        }else onForeground(a);
        changed(a);if(a.screen.equals("Today"))a.render();
    }
    static void onForeground(MainActivity a){
        if(a.app.maintenance||!PrivacyUi.accepted(a))return;String mode=mode(a);
        if(mode.equals(OFF)){sync(a);return;}if(mode.equals(FLOATING)&&!overlayAllowed(a)){sync(a);return;}
        if(!Reminders.notifications(a)&&prefs(a).getBoolean("ask_notifications",false)){
            boolean asked=prefs(a).getBoolean("asked_notifications",false);prefs(a).edit().putBoolean("ask_notifications",false).putBoolean("asked_notifications",true).apply();
            if(asked&&!a.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))a.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.getPackageName()));
            else a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},PERMISSION);return;
        }
        prefs(a).edit().putBoolean("ask_notifications",false).apply();
        if(mode.equals(FLOATING)&&Reminders.notifications(a)&&a.app.life.pendingCount()>0){try{a.startForegroundService(new Intent(a,TaskBubbleService.class));}catch(RuntimeException e){a.toast("Floating tasks could not start. Try Notification list instead.");}}
        a.app.alarms.execute(()->sync(a));
    }
    static void changed(Context c){MemoryApp app=Reminders.app(c);app.taskChanges.incrementAndGet();app.alarms.execute(()->{sync(c);c.sendBroadcast(new Intent(CHANGED).setPackage(c.getPackageName()));app.changed();});}
    static void disable(Context c){prefs(c).edit().putString("mode",OFF).putBoolean("hidden",false).putBoolean("ask_notifications",false).apply();c.stopService(new Intent(c,TaskBubbleService.class));c.getSystemService(NotificationManager.class).cancel(LIST_ID);c.getSystemService(NotificationManager.class).cancel(FLOAT_ID);}
    static void sync(Context c){
        MemoryApp app=Reminders.app(c);String selected=mode(c);NotificationManager manager=c.getSystemService(NotificationManager.class);
        if(app.maintenance||selected.equals(OFF)){manager.cancel(LIST_ID);c.stopService(new Intent(c,TaskBubbleService.class));return;}
        if(selected.equals(FLOATING)){manager.cancel(LIST_ID);TaskBubbleService.refreshIfRunning();return;}
        if(!Reminders.notifications(c)||prefs(c).getBoolean("hidden",false)||app.life.pendingCount()==0){manager.cancel(LIST_ID);return;}
        try{manager.notify(LIST_ID,notification(c,false));}catch(SecurityException ignored){} // Permission can be revoked between the check and notify.
    }
    static PendingIntent action(Context c,String action,String id,String token,int page){
        Intent intent=new Intent(c,TaskActionReceiver.class).setAction(action).setData(Uri.parse("pocketmemory://task-action/"+action+"/"+Uri.encode(id)+"/"+token+"/"+page)).putExtra("task",id).putExtra("token",token).putExtra("page",page);
        return PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static String token(JSONObject task){
        try{byte[] digest=MessageDigest.getInstance("SHA-256").digest((task.optString("id")+"|"+task.optLong("updated")+"|"+task.optString("title")+"|"+task.optString("day")).getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}
    }
    static int pageSize(Context c){return c.getResources().getConfiguration().fontScale>=1.3f?2:3;}
    static Notification notification(Context c,boolean floating){
        MemoryApp app=Reminders.app(c);NotificationManager manager=c.getSystemService(NotificationManager.class);NotificationChannel channel=new NotificationChannel(CHANNEL,"Pending task list",NotificationManager.IMPORTANCE_LOW);channel.setDescription("A quiet list of tasks you choose to keep visible");channel.setSound(null,null);channel.enableVibration(false);manager.createNotificationChannel(channel);
        int count=app.life.pendingCount(),size=pageSize(c),pages=Math.max(1,(count+size-1)/size),page=Math.min(Math.max(0,prefs(c).getInt("page",0)),pages-1);prefs(c).edit().putInt("page",page).apply();List<JSONObject> rows=app.life.pendingTasks(size,page*size);
        RemoteViews list=new RemoteViews(c.getPackageName(),R.layout.task_notification);int[] containers={R.id.task_notify_row1,R.id.task_notify_row2,R.id.task_notify_row3},titles={R.id.task_notify_title1,R.id.task_notify_title2,R.id.task_notify_title3},dates={R.id.task_notify_date1,R.id.task_notify_date2,R.id.task_notify_date3},buttons={R.id.task_notify_done1,R.id.task_notify_done2,R.id.task_notify_done3};
        for(int i=0;i<3;i++){list.setViewVisibility(containers[i],i<rows.size()?View.VISIBLE:View.GONE);if(i>=rows.size())continue;JSONObject task=rows.get(i);list.setTextViewText(titles[i],task.optString("title"));String day=task.optString("day");list.setTextViewText(dates[i],day);list.setViewVisibility(dates[i],day.isEmpty()?View.GONE:View.VISIBLE);list.setContentDescription(buttons[i],"Mark "+task.optString("title")+" done");list.setOnClickPendingIntent(buttons[i],action(c,DONE,task.optString("id"),token(task),0));}
        Intent open=new Intent(c,MainActivity.class).putExtra("screen","Today").putExtra("todaySection","Tasks").setData(Uri.parse("pocketmemory://tasks/open")).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_task_status).setColor(Y2k.AQUA).setContentTitle(count+" task"+(count==1?"":"s")+" left")
            .setContentText("Expand to check off tasks · "+(page+1)+"/"+pages).setStyle(new Notification.DecoratedCustomViewStyle()).setCustomBigContentView(list)
            .setContentIntent(content).setOnlyAlertOnce(true).setAutoCancel(false).setOngoing(floating).setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_task_status).setContentTitle("Pocket Memory tasks").setContentText("Unlock to view your task list.").build());
        if(page>0)builder.addAction(new Notification.Action.Builder(null,"Previous",action(c,PAGE,"","",page-1)).build());
        if(page+1<pages)builder.addAction(new Notification.Action.Builder(null,"Next",action(c,PAGE,"","",page+1)).build());
        builder.addAction(new Notification.Action.Builder(null,floating?"Stop floating":"Hide list",action(c,floating?STOP:HIDE,"","",0)).build());
        builder.setDeleteIntent(action(c,floating?STOP:HIDE,"","",0));if(floating)builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);return builder.build();
    }
    /** Fresh identity/content prevents an old notification from completing an edited task. */
    static boolean complete(Context c,String id,String expected){
        MemoryApp app=Reminders.app(c);if(app.maintenance)return false;
        synchronized(app.life){JSONObject task=app.life.task(id);if(task==null||task.optInt("done")!=0||!token(task).equals(expected))return false;app.life.complete(id,true);}
        for(JSONObject reminder:LifeStore.query(app.life.db(),"SELECT * FROM reminders WHERE task_id=?",id))Reminders.cancel(c,reminder.optString("id"));
        for(JSONObject event:LifeStore.query(app.life.db(),"SELECT e.id FROM alarm_events e JOIN reminders r ON e.reminder_id=r.id WHERE r.task_id=? AND e.state IN ('pending','ringing')",id))Reminders.act(c,event.optString("id"),Reminders.DISMISS);
        return true;
    }
    private TaskAssist(){}
}
