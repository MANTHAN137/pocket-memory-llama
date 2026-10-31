package com.pocketmemory.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Source, proposal and task storage for the Llama planning workflow. */
public final class PlanStore extends SQLiteOpenHelper {
    public static final class Note {
        public final long id; public final int version; public final String title,body;
        Note(long id,int version,String title,String body){this.id=id;this.version=version;this.title=title;this.body=body;}
        JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("version",version).put("title",title).put("body",body);}
    }
    public static final class Plan {
        public final String id,goal,day,inputHash,status;public final JSONArray sources,actions,links;
        Plan(Cursor c)throws JSONException{id=c.getString(0);goal=c.getString(1);day=c.getString(2);inputHash=c.getString(3);status=c.getString(4);sources=new JSONArray(c.getString(5));actions=new JSONArray(c.getString(6));links=new JSONArray(c.getString(7));}
    }
    static final class Snapshot {final List<Note> notes;final String hash;Snapshot(List<Note> notes,String hash){this.notes=notes;this.hash=hash;}}
    public PlanStore(Context context){super(context,"llama-plans.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,body TEXT NOT NULL,version INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE plans(id TEXT PRIMARY KEY,goal TEXT NOT NULL,day TEXT NOT NULL,input_hash TEXT NOT NULL,status TEXT NOT NULL,sources TEXT NOT NULL,actions TEXT NOT NULL,links TEXT NOT NULL,created INTEGER NOT NULL,model_output TEXT NOT NULL)");
        db.execSQL("CREATE TABLE tasks(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,day TEXT NOT NULL,source_id INTEGER NOT NULL,source_version INTEGER NOT NULL,source_title TEXT NOT NULL,source_quote TEXT NOT NULL,done INTEGER NOT NULL DEFAULT 0,dedupe TEXT UNIQUE NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new IllegalStateException("Unsupported preview database version");}
    public synchronized List<Note> notes(){List<Note> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,version,title,body FROM notes ORDER BY id",null)){while(c.moveToNext())out.add(new Note(c.getLong(0),c.getInt(1),c.getString(2),c.getString(3)));}return out;}
    public synchronized Note note(long id){for(Note n:notes())if(n.id==id)return n;return null;}
    public synchronized long saveNote(long id,String title,String body){
        if(title==null||title.trim().isEmpty()||title.length()>120||body==null||body.trim().isEmpty()||body.length()>4000)throw new IllegalArgumentException("Use a title up to 120 characters and a note up to 4,000 characters.");
        if(id==0&&notes().size()>=50)throw new IllegalArgumentException("This preview holds up to 50 source notes.");
        ContentValues v=new ContentValues();v.put("title",title.trim());v.put("body",body.trim());
        if(id==0)return getWritableDatabase().insertOrThrow("notes",null,v);
        Note old=note(id);if(old==null)throw new IllegalArgumentException("This source was removed.");v.put("version",old.version+1);getWritableDatabase().update("notes",v,"id=?",new String[]{""+id});return id;
    }
    public synchronized void deleteNote(long id){getWritableDatabase().delete("notes","id=?",new String[]{""+id});}
    static String digest(String text){try{byte[] data=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:data)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private static String hashNotes(List<Note> notes){try{JSONArray rows=new JSONArray();for(Note n:notes)rows.put(n.json());return digest(rows.toString());}catch(Exception e){throw new IllegalStateException(e);}}
    public synchronized String sourceHash(){return hashNotes(notes());}
    synchronized Snapshot snapshot(){List<Note> values=notes();return new Snapshot(values,hashNotes(values));}
    public synchronized String savePlan(String goal,String day,String hash,List<Note> sources,JSONArray actions,String raw)throws Exception{
        JSONArray rows=new JSONArray();for(Note n:sources)rows.put(n.json());String id=UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("id",id);v.put("goal",goal);v.put("day",day);v.put("input_hash",hash);v.put("status","review");v.put("sources",rows.toString());v.put("actions",actions.toString());v.put("links","[]");v.put("created",System.currentTimeMillis());v.put("model_output",raw);getWritableDatabase().insertOrThrow("plans",null,v);return id;
    }
    public synchronized Plan plan(String id){if(id==null)return null;try(Cursor c=getReadableDatabase().rawQuery("SELECT id,goal,day,input_hash,status,sources,actions,links FROM plans WHERE id=?",new String[]{id})){return c.moveToFirst()?new Plan(c):null;}catch(JSONException e){throw new IllegalStateException(e);}}
    public synchronized Plan latest(){try(Cursor c=getReadableDatabase().rawQuery("SELECT id,goal,day,input_hash,status,sources,actions,links FROM plans ORDER BY created DESC,rowid DESC LIMIT 1",null)){return c.moveToFirst()?new Plan(c):null;}catch(JSONException e){throw new IllegalStateException(e);}}
    public synchronized boolean fresh(Plan p){return p!=null&&p.inputHash.equals(sourceHash());}
    public synchronized void select(String id,int action,boolean selected)throws Exception{Plan p=plan(id);if(p==null||p.status.equals("applied"))return;p.actions.getJSONObject(action).put("selected",selected);ContentValues v=new ContentValues();v.put("actions",p.actions.toString());getWritableDatabase().update("plans",v,"id=?",new String[]{id});}
    /** Source check, idempotency and all task writes share one SQLite transaction. */
    public synchronized JSONArray apply(String id)throws Exception{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            Plan p=plan(id);if(p==null)throw new IllegalStateException("Build a plan first.");
            if(p.status.equals("applied")){db.setTransactionSuccessful();return p.links;}
            if(!fresh(p))throw new IllegalStateException("Your source notes changed. Build a fresh plan before adding tasks.");
            JSONArray links=new JSONArray();int selected=0;
            for(int i=0;i<p.actions.length();i++){
                JSONObject a=p.actions.getJSONObject(i);if(!a.optBoolean("selected",true))continue;selected++;
                String dedupe=digest(a.getLong("source_id")+"|"+a.getInt("source_version")+"|"+a.getString("text")+"|"+p.day);
                long taskId=0;boolean created=false;
                try(Cursor c=db.rawQuery("SELECT id FROM tasks WHERE dedupe=?",new String[]{dedupe})){if(c.moveToFirst())taskId=c.getLong(0);}
                if(taskId==0){ContentValues v=new ContentValues();v.put("title",a.getString("text"));v.put("day",p.day);v.put("source_id",a.getLong("source_id"));v.put("source_version",a.getInt("source_version"));v.put("source_title",a.getString("source_title"));v.put("source_quote",a.getString("source_quote"));v.put("dedupe",dedupe);taskId=db.insertOrThrow("tasks",null,v);created=true;}
                links.put(new JSONObject().put("task_id",taskId).put("created",created).put("title",a.getString("text")).put("day",p.day));
            }
            if(selected==0)throw new IllegalStateException("Choose at least one task.");ContentValues v=new ContentValues();v.put("status","applied");v.put("links",links.toString());db.update("plans",v,"id=?",new String[]{id});db.setTransactionSuccessful();return links;
        }finally{db.endTransaction();}
    }
    public synchronized void undo(String id)throws Exception{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            Plan p=plan(id);if(p==null||!p.status.equals("applied"))throw new IllegalStateException("This plan has no task batch to undo.");
            for(int i=0;i<p.links.length();i++){JSONObject link=p.links.getJSONObject(i);if(!link.getBoolean("created"))continue;
                try(Cursor c=db.rawQuery("SELECT title,day,done FROM tasks WHERE id=?",new String[]{""+link.getLong("task_id")})){
                    if(!c.moveToFirst()||c.getInt(2)!=0||!c.getString(0).equals(link.getString("title"))||!c.getString(1).equals(link.getString("day")))throw new IllegalStateException("A created task has changed or been completed. Review it in Tasks before undoing the batch.");
                }
            }
            for(int i=0;i<p.links.length();i++){JSONObject link=p.links.getJSONObject(i);if(link.getBoolean("created"))db.delete("tasks","id=?",new String[]{""+link.getLong("task_id")});}
            ContentValues v=new ContentValues();v.put("status","undone");v.put("links","[]");db.update("plans",v,"id=?",new String[]{id});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public synchronized List<JSONObject> tasks(){List<JSONObject> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title,day,source_title,source_quote,done FROM tasks ORDER BY day,id",null)){while(c.moveToNext())try{out.add(new JSONObject().put("id",c.getLong(0)).put("title",c.getString(1)).put("day",c.getString(2)).put("source_title",c.getString(3)).put("source_quote",c.getString(4)).put("done",c.getInt(5)));}catch(JSONException e){throw new IllegalStateException(e);}}return out;}
    public synchronized void complete(long id,boolean done){ContentValues v=new ContentValues();v.put("done",done?1:0);getWritableDatabase().update("tasks",v,"id=?",new String[]{""+id});}
}
