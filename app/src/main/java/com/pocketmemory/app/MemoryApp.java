package com.pocketmemory.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;

public final class MemoryApp extends Application {
    volatile java.util.concurrent.atomic.AtomicBoolean webCancelled=new java.util.concurrent.atomic.AtomicBoolean();
    volatile long webTicket=0;volatile McpClient webClient;volatile WebResearch.Result webResult;
    volatile boolean companionRunning=false;
    public Catalog catalog;
    ModelAccess access;
    PlayPurchases billing;
    public MemoryStore store;
    public AppEngine engine;
    public LifeStore life;public ChatStore chats;
    public volatile long chatVersion=0;
    final java.util.concurrent.atomic.AtomicLong taskChanges=new java.util.concurrent.atomic.AtomicLong();
    public volatile String chatId="",chatReply="",chatRunningId="";
    public int visibleActivities=0;
    public final ExecutorService worker=Executors.newSingleThreadExecutor();
    public final ExecutorService files=Executors.newSingleThreadExecutor();
    public final ExecutorService alarms=Executors.newSingleThreadExecutor(r->new Thread(r,"reminder-delivery"));
    public final Handler main=new Handler(Looper.getMainLooper());
    public final CopyOnWriteArrayList<Runnable> listeners=new CopyOnWriteArrayList<>();
    public volatile String workStatus="";
    public volatile boolean busy=false,ocrBusy=false,maintenance=false;
    public volatile String downloadId="", downloadMessage="";
    public volatile long downloadBytes=0;
    public volatile String question="", answer="", answerPrompt="", writingInput="", writingOutput="",writingPrompt="";
    public volatile boolean writingRunning=false,writingFailed=false;
    public String writingUndo="";public boolean writingUndoAvailable=false;
    private volatile String persistedWriting="";
    private volatile long writingDraftEpoch=0;
    private final Runnable writeDraft=()->persistWritingDraft();
    public void updateWritingDraft(String value){writingInput=value;main.removeCallbacks(writeDraft);main.postDelayed(writeDraft,500);}
    public java.util.concurrent.Future<Boolean> persistWritingDraft(){
        main.removeCallbacks(writeDraft);String snapshot=writingInput;long epoch=writingDraftEpoch;
        return files.submit(()->{if(maintenance||epoch!=writingDraftEpoch)return false;if(snapshot.equals(persistedWriting))return true;
            boolean saved=getSharedPreferences("writing_draft",MODE_PRIVATE).edit().putString("text",snapshot).commit();
            if(saved)persistedWriting=snapshot;else{workStatus="Writing draft could not be saved. Free storage and keep a copy.";changed();}return saved;});
    }
    public void eraseWritingDraft(){writingDraftEpoch++;main.removeCallbacks(writeDraft);persistedWriting="";getSharedPreferences("writing_draft",MODE_PRIVATE).edit().clear().commit();}
    public volatile List<MemoryStore.Passage> sources=new ArrayList<>();
    public volatile boolean taskFailed=false,historicalAnswer=false;
    @Override public void onCreate() {
        super.onCreate();writingInput=getSharedPreferences("writing_draft",MODE_PRIVATE).getString("text","");persistedWriting=writingInput; webResult=WebResearch.load(this);catalog=new Catalog(this);access=new ModelAccess(this);billing=new PlayPurchases(this); store=new MemoryStore(this);life=new LifeStore(store);chats=new ChatStore(store);cleanupEmptyChats();selectFreeIfLocked();engine=new AppEngine(this);
    }
    void selectFreeIfLocked(){
        Catalog.Model current=catalog.get(activeId());if(current!=null&&access.allowed(current))return;
        active("llama-3.2-3b");
    }
    void cleanupEmptyChats(){
        android.content.SharedPreferences settings=getSharedPreferences("settings",MODE_PRIVATE);String selected=settings.getString("chat","");
        // Older builds persisted blank chats. Keep the selected draft addressable during upgrade.
        if(chats.exists(selected)&&chats.messages(selected,1).isEmpty())settings.edit().putString("new_chat",selected).apply();
        chats.pruneEmpty();
    }
    private final java.util.concurrent.atomic.AtomicBoolean changeQueued=new java.util.concurrent.atomic.AtomicBoolean();
    public void changed() { if(changeQueued.compareAndSet(false,true))main.post(()->{changeQueued.set(false);for(Runnable r:listeners)r.run();}); }
    public String activeId() { return getSharedPreferences("settings",MODE_PRIVATE).getString("active","llama-3.2-3b"); }
    public void active(String id) { getSharedPreferences("settings",MODE_PRIVATE).edit().putString("active",id).putBoolean("model_selected",true).apply(); changed(); }
}
