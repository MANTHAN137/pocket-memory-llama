package com.pocketmemory.app;

import android.app.NotificationManager;
import android.content.Intent;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/** Explicit destructive action only, serialized behind file work; downloaded models may be retained. */
public final class PersonalData {
    static void erase(MemoryApp app,boolean models)throws Exception{
        if(!app.maintenance)throw new IllegalStateException("Data deletion must own the maintenance lock.");
        TaskAssist.disable(app);
        app.alarms.submit(()->{
            for(org.json.JSONObject r:app.life.reminders())Reminders.cancel(app,r.optString("id"));
            app.stopService(new Intent(app,ReminderService.class));app.getSystemService(NotificationManager.class).cancelAll();
        }).get(10,TimeUnit.SECONDS);
        app.worker.submit(()->{NativeEngine.unload();app.store.erasePersonalData();}).get(30,TimeUnit.SECONDS);
        for(String name:new String[]{"photos","response-reports.json","response-reports.json.bak","response-reports.json.new"})delete(new File(app.getFilesDir(),name));
        deleteContents(app.getCacheDir());app.eraseWritingDraft();
        app.billing.erase();app.access.erase();
        app.writingUndo="";app.writingUndoAvailable=false;
        for(String prefs:new String[]{"chat_drafts","journal_drafts","photo_draft","photo_drafts","capture","harness","task_assist"})app.getSharedPreferences(prefs,0).edit().clear().commit();
        app.getSharedPreferences("settings",0).edit().remove("chat").remove("new_chat").apply();
        if(models){delete(Catalog.directory(app));for(Catalog.Model model:app.catalog.models)model.invalidateDisplay();}
        app.chatId="";app.chatRunningId="";app.chatReply="";app.chatVersion++;app.question="";app.answer="";app.writingInput="";app.writingOutput="";app.writingPrompt="";app.answerPrompt="";app.writingRunning=false;app.writingFailed=false;app.sources=new java.util.ArrayList<>();app.workStatus="";app.taskFailed=false;app.historicalAnswer=false;
    }
    static void deleteContents(File directory)throws IOException{File[] files=directory.listFiles();if(files!=null)for(File f:files)delete(f);}
    static void delete(File file)throws IOException{if(!file.exists())return;if(file.isDirectory()&&!Files.isSymbolicLink(file.toPath()))deleteContents(file);Files.deleteIfExists(file.toPath());}
    private PersonalData(){}
}
