package com.pocketmemory.app;

import android.app.Application;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlanApp extends Application {
    PlanStore store;ModelFiles model;
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    final Handler main=new Handler(Looper.getMainLooper());
    volatile AtomicBoolean cancelled=new AtomicBoolean();
    volatile long ticket=0;volatile boolean busy=false;volatile String stage="Review",message="",goal=ExampleData.GOAL,day="2026-11-02";volatile Runnable listener;
    @Override public void onCreate(){super.onCreate();store=new PlanStore(this);model=new ModelFiles(this);PlanStore.Plan previous=store.latest();if(previous!=null){goal=previous.goal;day=previous.day;}}
    void changed(){main.post(()->{Runnable target=listener;if(target!=null)target.run();});}
    synchronized void build(){
        if(busy)return;busy=true;AtomicBoolean stop=new AtomicBoolean();cancelled=stop;ticket=LocalLlama.newJob();long job=ticket;String request=goal,date=day;message="";stage="Read";changed();
        ScheduledFuture<?> watchdog=timer.schedule(()->{stop.set(true);LocalLlama.cancel(job);},150,TimeUnit.SECONDS);
        worker.execute(()->{try{String id=LlamaPlanner.build(store,model,request,date,stop,job,(name,detail)->{stage=name;message=detail;changed();});PlanStore.Plan p=store.plan(id);stage="Review";message=store.fresh(p)?"Your plan is ready. Review it before adding tasks.":"Your notes changed during planning. Build a fresh plan.";}catch(Exception e){stage="Review";message=e.getMessage()==null?"The plan could not be built.":e.getMessage();}finally{watchdog.cancel(false);busy=false;changed();}});
    }
    void stop(){cancelled.set(true);LocalLlama.cancel(ticket);message="Stopping planning…";changed();}
}
