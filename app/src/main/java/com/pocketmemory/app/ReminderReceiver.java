package com.pocketmemory.app;

import android.content.*;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        // AlarmManager delivery and explicit notification actions only. Never handle boot here.
        String action=intent==null?null:intent.getAction();
        if(action==null||!java.util.Set.of(Reminders.FIRE,Reminders.DISMISS,Reminders.SNOOZE,Reminders.WATER).contains(action))return;
        PendingResult pending=goAsync();MemoryApp app=Reminders.app(context);
        app.alarms.execute(()->{try{
            if(Reminders.FIRE.equals(action))Reminders.fire(context,intent.getStringExtra("id"),intent.getIntExtra("revision",-1));
            else Reminders.act(context,intent.getStringExtra("event"),action);
            TaskAssist.sync(context);
        }finally{pending.finish();}});
    }
}
