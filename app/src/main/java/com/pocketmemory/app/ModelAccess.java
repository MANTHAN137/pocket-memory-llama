package com.pocketmemory.app;

import android.content.SharedPreferences;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Free models, verified Play purchases and explicit complimentary unlocks stay separate. */
final class ModelAccess {
    private final MemoryApp app;
    private volatile Set<String> purchased=Set.of();
    ModelAccess(MemoryApp app){this.app=app;}
    boolean allowed(Catalog.Model model){
        return model!=null&&model.available()&&(!model.premium||purchased.contains(model.id)||prefs().getStringSet("gift_models",Set.of()).contains(model.id));
    }
    SharedPreferences prefs(){return app.getSharedPreferences("model_access",0);}
    synchronized void purchases(Set<String> verified){purchased=Set.copyOf(verified);app.changed();}
    synchronized void redeem(String input,long now)throws IOException{
        if(app.maintenance)throw new IOException("Personal data is being erased. Try the code afterward.");
        if(!DailyUnlockCode.configured(BuildConfig.UNLOCK_MULTIPLIER,BuildConfig.UNLOCK_OFFSET)&&!ReviewerUnlockCode.configured(BuildConfig.REVIEWER_UNLOCK_SHA256))throw new IOException("Complimentary codes are not configured in this build.");
        long blocked=prefs().getLong("blocked_until",0);
        if(now<blocked)throw new IOException("Too many incorrect codes. Wait a minute and try again.");
        String entered=input==null?"":input.trim();
        boolean correct=DailyUnlockCode.matches(entered,Instant.ofEpochMilli(now).atZone(DailyUnlockCode.ZONE).toLocalDate(),BuildConfig.UNLOCK_MULTIPLIER,BuildConfig.UNLOCK_OFFSET)||ReviewerUnlockCode.matches(entered,BuildConfig.REVIEWER_UNLOCK_SHA256);
        if(!correct){
            int count=prefs().getInt("failed_attempts",0)+1;
            if(!prefs().edit().putInt("failed_attempts",count>=5?0:count).putLong("blocked_until",count>=5?now+60_000:0).commit())throw new IOException("Could not save code state. Free storage and retry.");
            throw new IOException("Incorrect or expired code. Check with the publisher.");
        }
        Set<String> gifts=new HashSet<>(prefs().getStringSet("gift_models",Set.of()));
        for(Catalog.Model model:app.catalog.models)if(model.premium&&model.available())gifts.add(model.id);
        if(!prefs().edit().putStringSet("gift_models",gifts).remove("failed_attempts").remove("blocked_until").commit())throw new IOException("Unlock could not be saved. Free storage and retry.");
        app.changed();
    }
    void erase()throws IOException{if(!prefs().edit().clear().commit())throw new IOException("Could not clear model access.");purchased=Set.of();}
}
