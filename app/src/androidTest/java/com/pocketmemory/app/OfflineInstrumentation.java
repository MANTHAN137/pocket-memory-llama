package com.pocketmemory.app;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Real new-workflow checks, fictional data, real Meta inference, emulator only. */
public final class OfflineInstrumentation extends Instrumentation {
    PlanApp app;MainActivity a;Bundle args;StringBuilder log=new StringBuilder();
    @Override public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    void report(String value){log.append(value).append('\n');Bundle b=new Bundle();b.putString("stream",value+"\n");sendStatus(0,b);}
    void check(boolean value,String message){if(!value)throw new AssertionError(message);report("PASS: "+message);}
    void main(Runnable run){runOnMainSync(run);waitForIdleSync();}
    void bottom(){waitForIdleSync();SystemClock.sleep(400);main(()->a.scroll.fullScroll(View.FOCUS_DOWN));}
    void shot(String name)throws Exception{waitForIdleSync();SystemClock.sleep(450);Bitmap image=getUiAutomation().takeScreenshot();check(image!=null,"capture "+name);try(FileOutputStream out=new FileOutputStream(new File(app.getFilesDir(),"plan-"+name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    PlanStore.Plan build()throws Exception{
        main(()->{a.go("Plan");app.build();});long end=SystemClock.elapsedRealtime()+170_000;
        while(app.busy&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);
        check(!app.busy,"the bounded Llama planning job finishes");waitForIdleSync();PlanStore.Plan plan=app.store.latest();check(plan!=null&&app.store.fresh(plan),"a source-current proposal is persisted for review");
        try(android.database.Cursor c=app.store.getReadableDatabase().rawQuery("SELECT model_output FROM plans WHERE id=?",new String[]{plan.id})){c.moveToFirst();report("ACTUAL LLAMA OUTPUT: "+c.getString(0));}
        report("ACTUAL ACTIONS: "+plan.actions);return plan;
    }
    String actions(PlanStore.Plan p){StringBuilder text=new StringBuilder();for(int i=0;i<p.actions.length();i++)text.append(p.actions.optJSONObject(i).optString("text")).append('\n');return text.toString();}
    void rejected(String raw,List<LlamaPlanner.Candidate> allowed){boolean refused=false;try{LlamaPlanner.parse(raw,allowed);}catch(Exception e){refused=true;}check(refused,"untrusted model output is rejected: "+raw);}
    void flow()throws Exception{
        app.store.close();getTargetContext().deleteDatabase("llama-plans.db");app.store=new PlanStore(app);ExampleData.add(app.store);app.goal=ExampleData.GOAL;app.day="2026-11-02";
        List<LlamaPlanner.Candidate> allowed=LlamaPlanner.candidates(app.goal,app.store.notes());check(allowed.size()>=5,"source action candidates include a separate event for relevance selection");
        rejected("{\"steps\":[999]}",allowed);rejected("{\"steps\":[1,1]}",allowed);rejected("{\"steps\":[1.5]}",allowed);rejected("{\"steps\":[1],\"execute\":\"shell\"}",allowed);rejected("Here is a plan: {\"steps\":[1]}",allowed);
        main(()->a.go("Sources"));shot("01-sources");
        PlanStore.Plan first=build();String firstText=actions(first);
        check(firstText.contains("prototype tablet")&&firstText.contains("Cedar")&&firstText.contains("09:45")&&firstText.contains("send Mira"),"Llama selects the complete client-demo workflow from allowed source actions");
        check(firstText.indexOf("prototype tablet")<firstText.indexOf("09:45")&&firstText.indexOf("09:45")<firstText.indexOf("Cedar")&&firstText.indexOf("Cedar")<firstText.indexOf("send Mira"),"application phase rules order preparation before arrival, venue access and follow-up");
        check(!firstText.contains("balloons"),"Llama excludes the separate party rather than selecting every retrieved action");
        check(app.store.tasks().isEmpty(),"model planning alone creates no tasks");main(()->{a.go("Plan");a.scroll.scrollTo(0,0);});shot("02-review");
        main(()->a.evidence(first.actions.optJSONObject(0)));shot("03-evidence");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
        PlanStore.Note venue=app.store.notes().stream().filter(n->n.title.contains("venue")).findFirst().orElseThrow();
        app.store.saveNote(venue.id,venue.title,"Use the west entrance to reach room Orchid for the Lakeview client demo.");app.message="A venue source changed. The earlier proposal must be rebuilt.";main(()->{a.go("Plan");a.scroll.scrollTo(0,0);});shot("04-stale");
        boolean stale=false;try{app.store.apply(first.id);}catch(IllegalStateException expected){stale=true;}
        check(stale&&app.store.tasks().isEmpty()&&app.store.plan(first.id).status.equals("review"),"stale source versions reject the whole apply transaction without partial tasks");
        PlanStore.Plan fresh=build();String freshText=actions(fresh);check(freshText.contains("Orchid")&&!freshText.contains("Cedar")&&!freshText.contains("balloons"),"the rebuilt Llama proposal uses the current source and excludes unrelated actions");
        main(()->a.go("Plan"));bottom();shot("05-ready-to-apply");
        main(()->{if(a.applyButton==null||!a.applyButton.isEnabled())throw new AssertionError("Review apply action is unavailable");a.applyButton.performClick();});int count=app.store.tasks().size();check(count==fresh.actions.length()&&count>0,"the real review button commits the selected source actions to local tasks");
        app.store.apply(fresh.id);check(app.store.tasks().size()==count,"repeating apply is idempotent and cannot duplicate the task batch");
        long task=app.store.tasks().get(0).getLong("id");app.store.complete(task,true);boolean changed=false;try{app.store.undo(fresh.id);}catch(IllegalStateException expected){changed=true;}
        check(changed&&app.store.tasks().size()==count,"undo preserves all tasks if a created task has been completed");app.store.complete(task,false);app.store.undo(fresh.id);check(app.store.tasks().isEmpty(),"undo removes only this batch's unchanged newly created tasks");
        app.message="The batch was undone. The reviewed proposal is still available.";main(()->a.go("Plan"));bottom();shot("06-undo");
        main(()->{if(a.applyButton==null||!a.applyButton.isEnabled())throw new AssertionError("Re-apply requires explicit review");a.applyButton.performClick();});
        check(app.store.tasks().size()==count,"explicit review can reapply an undone, source-current batch");main(()->a.go("Tasks"));shot("07-created-tasks");main(()->a.go("Model"));shot("08-llama-model");
        check(app.model.name().contains("Llama")&&app.model.installed(),"the preview exposes the actual verified Meta Llama model");report("All example names, dates, venues and tasks are fictional. Model output selects IDs; the app copies action text from sources. No external communication was performed.");
    }
    void restart()throws Exception{PlanStore.Plan p=app.store.latest();check(p!=null&&p.status.equals("applied"),"the applied plan survives an actual Android process restart");check(app.store.tasks().size()==p.links.length()&&p.links.length()>0,"task identities and batch links survive restart without duplicate execution");check(!app.busy,"reopening the app does not automatically rerun Llama or apply a plan");main(()->a.go("Tasks"));shot("09-restarted-tasks");}
    @Override public void onStart(){Bundle result=new Bundle();try{
        if(!android.os.Build.FINGERPRINT.contains("generic")&&!android.os.Build.MODEL.contains("sdk"))throw new IllegalStateException("Fictional fixtures require an isolated emulator.");
        waitForIdleSync();app=(PlanApp)getTargetContext().getApplicationContext();app.model.accept();
        check(app.getPackageName().equals("com.pocketmemory.llama"),"the hackathon package identity is correct");
        check(app.checkSelfPermission(android.Manifest.permission.INTERNET)==PackageManager.PERMISSION_DENIED,"the app has no internet permission");
        check(Settings.Global.getInt(app.getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0)==1,"the planner walkthrough runs in airplane mode");
        check(app.model.installed(),"the pinned Meta Llama 3B model is installed and verified");
        a=(MainActivity)startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));main(()->a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        if(args!=null&&args.getString("stage","").equals("restart"))restart();else flow();result.putString("stream",log+"\nALL CHECKS PASSED\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){StringWriter out=new StringWriter();e.printStackTrace(new PrintWriter(out));result.putString("stream",log+"\nFAIL: "+out);finish(Activity.RESULT_CANCELED,result);}}
}
