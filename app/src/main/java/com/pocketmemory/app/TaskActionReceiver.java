package com.pocketmemory.app;

import android.content.*;
import android.widget.Toast;

/** Only immutable, explicit app PendingIntents can reach this non-exported receiver. */
public final class TaskActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String action=intent.getAction();if(action==null||!java.util.Set.of(TaskAssist.DONE,TaskAssist.HIDE,TaskAssist.PAGE,TaskAssist.STOP).contains(action))return;
        PendingResult pending=goAsync();MemoryApp app=Reminders.app(context);app.alarms.execute(()->{try{
            if(app.maintenance)return;
            if(TaskAssist.STOP.equals(action))TaskAssist.disable(context);
            else if(TaskAssist.HIDE.equals(action))TaskAssist.prefs(context).edit().putBoolean("hidden",true).apply();
            else if(TaskAssist.PAGE.equals(action))TaskAssist.prefs(context).edit().putInt("page",Math.max(0,intent.getIntExtra("page",0))).apply();
            else if(!TaskAssist.mode(context).equals(TaskAssist.OFF)){
                boolean completed=TaskAssist.complete(context,intent.getStringExtra("task"),intent.getStringExtra("token"));
                if(completed)app.main.post(()->Toast.makeText(context,app.life.pendingCount()==0?"🎉 All done. Nice work!":"✓ Nice work! Task complete.",Toast.LENGTH_SHORT).show());
            }
            TaskAssist.sync(context);app.taskChanges.incrementAndGet();context.sendBroadcast(new Intent(TaskAssist.CHANGED).setPackage(context.getPackageName()));app.changed();
        }catch(RuntimeException failure){app.main.post(()->Toast.makeText(context,"Task could not be saved. Please try again.",Toast.LENGTH_SHORT).show());}finally{pending.finish();}});
    }
}
