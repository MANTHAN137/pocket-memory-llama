package com.pocketmemory.app;

import android.os.SystemClock;
import org.json.*;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.regex.*;

/** Owns bounded execution policy. Models never choose tools, paths, retries or budgets. */
public final class ModelHarness {
    public enum Task { ANSWER, CHAT, WEB, WRITE, INDEX, VOICE }
    public enum Stage { IDLE, RETRIEVING, INDEXING, LOADING, GENERATING, REPAIRING, VALIDATING, DONE, FALLBACK, CANCELLED, TIMED_OUT, FAILED }
    private final MemoryApp app;
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"model-watchdog");t.setDaemon(true);return t;});
    private volatile Job current;
    private volatile Stage stage=Stage.IDLE;
    public ModelHarness(MemoryApp app){this.app=app;}
    public Stage stage(){return stage;}
    public boolean active(){return current!=null;}
    public synchronized Job begin(Task task) throws IOException {
        if(app.maintenance)throw new IOException("Personal data is being erased. Please wait.");
        if(current!=null)throw new IOException("Another model task is already running.");
        long budget=task==Task.WEB?180_000:(task==Task.ANSWER||task==Task.CHAT)?120_000:task==Task.WRITE?240_000:task==Task.VOICE?60_000:300_000;
        Job job=new Job(task,budget);current=job;app.busy=true;stage=Stage.IDLE;
        job.watchdog=timer.schedule(()->interrupt(job,true),budget,TimeUnit.MILLISECONDS);return job;
    }
    public void stage(Stage next) throws IOException {check();stage=next;}
    public void check() throws IOException {
        Job j=current;if(j==null)throw new IOException("No active task.");
        if(j.cancelled)throw new IOException(j.timedOut?"This task reached its time limit. Select less text and try again.":"Task stopped.");
        if(SystemClock.elapsedRealtime()>j.deadline){interrupt(true);throw new IOException("This task reached its time limit. Select less text and try again.");}
    }
    public boolean interrupted(){Job j=current;return j!=null&&j.cancelled;}
    public void interrupt(boolean timeout){interrupt(current,timeout);}
    private synchronized void interrupt(Job j,boolean timeout){
        if(j==null||current!=j)return;j.cancelled=true;j.timedOut=j.timedOut||timeout;stage=timeout?Stage.TIMED_OUT:Stage.CANCELLED;
        if(j.task==Task.WEB){app.webCancelled.set(true);LocalLlama.cancel(app.webTicket);McpClient network=app.webClient;if(network!=null)network.cancel();}
        NativeEngine.cancel();OfflineVoice.cancel();app.changed();
    }
    public void beforeGeneration() throws IOException {
        check();Job j=current;int maximum=(j.task==Task.CHAT||j.task==Task.WEB)?1:j.task==Task.ANSWER?2:12;
        if(++j.calls>maximum)throw new IOException("The task reached its step limit. Select a smaller task.");
        int stepBudget=(j.task==Task.CHAT||(j.task==Task.ANSWER&&app.activeId().equals("llama-3.2-3b")))?90000:45000;
        int remaining=(int)Math.max(100,Math.min(stepBudget,j.deadline-SystemClock.elapsedRealtime()));
        boolean liquid=app.activeId().startsWith("lfm")||app.activeId().startsWith("llama-3.2-3b");
        NativeEngine.configure(remaining,liquid?0.1f:0.5f,liquid?1.05f:1.08f);
        if(stage!=Stage.REPAIRING)stage=Stage.GENERATING;
    }
    public void fallback(){stage=Stage.FALLBACK;Job j=current;if(j!=null)j.outcome="fallback";}
    public static boolean validGroundedAnswer(String answer,List<MemoryStore.Passage> hits){
        String lower=answer.toLowerCase(Locale.ROOT);
        if(answer.isBlank()||lower.contains("[no answer]")||lower.contains("cannot determine")||lower.contains("don't have access")||lower.contains("do not have access")||lower.contains("cannot find")||lower.contains("not enough information")||lower.contains("don't have enough")||lower.contains("does not contain")||lower.contains("no information")||lower.contains("not mentioned")||lower.contains("not provided")||lower.contains("cannot answer")||lower.contains("no relevant")||lower.contains("no mention"))return false;
        Matcher citations=Pattern.compile("\\[(\\d+)\\]").matcher(answer);boolean cited=false;
        while(citations.find()){int id;try{id=Integer.parseInt(citations.group(1));}catch(NumberFormatException e){return false;}if(id<1||id>hits.size())return false;cited=true;}
        if(!cited)return false;
        StringBuilder evidence=new StringBuilder();for(MemoryStore.Passage p:hits)evidence.append(p.text).append(' ');
        String clean=answer.replaceAll("\\[\\d+\\]","");
        Pattern numberPattern=Pattern.compile("[\\p{L}]*\\d[\\p{L}\\d.,:/-]*");
        java.util.Set<String> sourceNumbers=new java.util.HashSet<>();
        Matcher sourceMatcher=numberPattern.matcher(evidence.toString().toLowerCase(Locale.ROOT));
        while(sourceMatcher.find())sourceNumbers.add(sourceMatcher.group().replaceAll("[.,:]+$",""));
        Matcher numbers=numberPattern.matcher(clean.toLowerCase(Locale.ROOT));
        while(numbers.find())if(!sourceNumbers.contains(numbers.group().replaceAll("[.,:]+$","")))return false;
        List<String> words=MemoryStore.terms(clean);int supported=0;String saved=evidence.toString().toLowerCase(Locale.ROOT);
        for(String word:words)if(saved.contains(word))supported++;
        return !words.isEmpty()&&supported*2>=words.size();
    }
    public String diagnostics(){return app.getSharedPreferences("harness",0).getString("recent","[]");}
    public static boolean validGrammarEdit(String original,String result){
        if(result.isBlank()||result.length()>original.length()*2L+64)return false;
        String source=original.toLowerCase(Locale.ROOT);List<String> words=MemoryStore.terms(result);int matching=0;for(String word:words)if(source.contains(word))matching++;
        if(!words.isEmpty()&&matching*2<words.size())return false;
        return WritingTools.numbersPreserved(original,result);
    }
    public final class Job implements AutoCloseable {
        final Task task;final long started,deadline;volatile boolean cancelled,timedOut;int calls;String outcome="failed";ScheduledFuture<?> watchdog;
        Job(Task task,long budget){this.task=task;started=SystemClock.elapsedRealtime();deadline=started+budget;}
        public void success(){if(!cancelled&&!outcome.equals("fallback")){outcome="completed";stage=Stage.DONE;}}
        @Override public void close(){
            if(watchdog!=null)watchdog.cancel(false);
            if(cancelled)outcome=timedOut?"time_limit":"cancelled";
            try{
                JSONArray previous=new JSONArray(diagnostics()),recent=new JSONArray();
                recent.put(new JSONObject().put("task",task.name()).put("model",app.activeId()).put("elapsed_ms",SystemClock.elapsedRealtime()-started).put("generation_calls",calls).put("outcome",outcome));
                for(int i=0;i<Math.min(19,previous.length());i++)recent.put(previous.get(i));
                app.getSharedPreferences("harness",0).edit().putString("recent",recent.toString()).apply();
            }catch(Exception ignored){}
            if(outcome.equals("failed"))stage=Stage.FAILED;
            if(current==this)current=null;app.busy=false;app.changed();
        }
    }
}
