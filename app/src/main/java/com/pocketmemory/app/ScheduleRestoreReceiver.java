package com.pocketmemory.app;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.Set;

/** System recovery only restores future AlarmManager schedules; it never delivers alarm audio. */
public final class ScheduleRestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String action=intent==null?null:intent.getAction();
        if(action==null||!Set.of(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED).contains(action))return;
        PendingResult pending=goAsync();MemoryApp app=Reminders.app(context);
        app.alarms.execute(()->{try{
            Reminders.restoreSchedules(context,Intent.ACTION_TIMEZONE_CHANGED.equals(action)||Intent.ACTION_TIME_CHANGED.equals(action));
        }finally{pending.finish();}});
    }
}
