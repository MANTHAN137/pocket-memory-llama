package com.pocketmemory.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.time.*;
import java.util.*;

/** Small organizer tables in the same private SQLite database as memories. No polling or network. */
public final class LifeStore {
    final MemoryStore memory;
    public LifeStore(MemoryStore memory){this.memory=memory;}
    SQLiteDatabase db(){return memory.getWritableDatabase();}
    static String id(){return UUID.randomUUID().toString();}
    public static String today(){return LocalDate.now(ZoneId.systemDefault()).toString();}
    static void create(SQLiteDatabase db){
        db.execSQL("CREATE TABLE todos(id TEXT PRIMARY KEY,title TEXT NOT NULL,day TEXT NOT NULL,done INTEGER NOT NULL DEFAULT 0,created INTEGER NOT NULL,updated INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX todos_day ON todos(day,done)");
        db.execSQL("CREATE TABLE journals(day TEXT PRIMARY KEY,note_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE water_log(id TEXT PRIMARY KEY,day TEXT NOT NULL,ml INTEGER NOT NULL CHECK(ml>0),created INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX water_day ON water_log(day)");
        db.execSQL("CREATE TABLE life_settings(k TEXT PRIMARY KEY,v TEXT NOT NULL)");
        db.execSQL("CREATE TABLE reminders(id TEXT PRIMARY KEY,title TEXT NOT NULL,body TEXT NOT NULL,kind TEXT NOT NULL,repeat_rule TEXT NOT NULL,mode TEXT NOT NULL,task_id TEXT NOT NULL DEFAULT '',next_at INTEGER NOT NULL,minute INTEGER NOT NULL,interval_min INTEGER NOT NULL,start_min INTEGER NOT NULL,end_min INTEGER NOT NULL,enabled INTEGER NOT NULL,revision INTEGER NOT NULL,created INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX reminder_due ON reminders(enabled,next_at)");
        db.execSQL("CREATE TABLE alarm_events(id TEXT PRIMARY KEY,reminder_id TEXT NOT NULL,title TEXT NOT NULL,body TEXT NOT NULL,kind TEXT NOT NULL,mode TEXT NOT NULL,state TEXT NOT NULL,created INTEGER NOT NULL)");
    }
    static List<JSONObject> query(SQLiteDatabase db,String sql,String... args){
        List<JSONObject> out=new ArrayList<>();try(Cursor c=db.rawQuery(sql,args.length==0?null:args)){String[] cols=c.getColumnNames();while(c.moveToNext()){JSONObject row=new JSONObject();for(int i=0;i<cols.length;i++)try{if(c.getType(i)==Cursor.FIELD_TYPE_INTEGER)row.put(cols[i],c.getLong(i));else row.put(cols[i],c.isNull(i)?JSONObject.NULL:c.getString(i));}catch(JSONException e){throw new IllegalStateException(e);}out.add(row);}}return out;
    }
    public synchronized List<JSONObject> tasks(String day,boolean all){return query(db(),all?"SELECT * FROM todos ORDER BY done,day,created LIMIT 300":"SELECT * FROM todos WHERE done=0 OR day=? ORDER BY done,CASE WHEN day='' THEN 1 ELSE 0 END,day,created LIMIT 100",all?new String[]{}:new String[]{day});}
    public synchronized JSONObject task(String id){List<JSONObject> rows=query(db(),"SELECT * FROM todos WHERE id=?",id);return rows.isEmpty()?null:rows.get(0);}
    public synchronized int pendingCount(){return query(db(),"SELECT COUNT(*) AS n FROM todos WHERE done=0").get(0).optInt("n");}
    public synchronized List<JSONObject> pendingTasks(int limit,int offset){return query(db(),"SELECT * FROM todos WHERE done=0 ORDER BY CASE WHEN day='' THEN 1 ELSE 0 END,day,created,id LIMIT ? OFFSET ?",""+Math.max(1,Math.min(50,limit)),""+Math.max(0,offset));}
    public synchronized String task(String id,String title,String day){
        InputLimits.TASK.require(title);if(day==null)day="";if(!day.isBlank())LocalDate.parse(day);
        ContentValues v=new ContentValues();v.put("title",title.trim());v.put("day",day);v.put("updated",System.currentTimeMillis());if(id==null||id.isBlank()){id=id();v.put("id",id);v.put("created",System.currentTimeMillis());db().insertOrThrow("todos",null,v);}else db().update("todos",v,"id=?",new String[]{id});return id;
    }
    public synchronized void complete(String id,boolean done){db().execSQL("UPDATE todos SET done=?,updated=? WHERE id=?",new Object[]{done?1:0,System.currentTimeMillis(),id});if(done)db().execSQL("UPDATE reminders SET enabled=0,revision=revision+1 WHERE task_id=?",new Object[]{id});}
    public synchronized void deleteTask(String id){db().beginTransaction();try{db().delete("todos","id=?",new String[]{id});db().execSQL("UPDATE reminders SET enabled=0,revision=revision+1 WHERE task_id=?",new Object[]{id});db().setTransactionSuccessful();}finally{db().endTransaction();}}
    public synchronized MemoryStore.Note journal(String day){LocalDate.parse(day);List<JSONObject> rows=query(db(),"SELECT note_id FROM journals WHERE day=?",day);return rows.isEmpty()?null:memory.note(rows.get(0).optLong("note_id"));}
    public synchronized long saveJournal(String day,String text){
        LocalDate.parse(day);InputLimits.NOTE.require(text);MemoryStore.Note old=journal(day);db().beginTransaction();
        try{long note=memory.save(old==null?0:old.id,(old==null?"Daily note · "+day:old.title),text,"Journal entry for "+day);db().execSQL("INSERT OR REPLACE INTO journals(day,note_id) VALUES(?,?)",new Object[]{day,note});db().setTransactionSuccessful();return note;}finally{db().endTransaction();}
    }
    synchronized void attachJournal(String day,long note){LocalDate.parse(day);if(memory.note(note)==null)throw new IllegalArgumentException("Missing journal part.");db().execSQL("INSERT OR REPLACE INTO journals(day,note_id) VALUES(?,?)",new Object[]{day,note});}
    public synchronized List<JSONObject> journals(){return query(db(),"SELECT j.day,n.id,n.body,n.updated FROM journals j JOIN notes n ON n.id=j.note_id ORDER BY j.day DESC LIMIT 90");}
    public synchronized String water(int ml){if(ml<1||ml>3000)throw new IllegalArgumentException("Enter an amount between 1 and 3,000 ml.");String id=id();db().execSQL("INSERT INTO water_log(id,day,ml,created) VALUES(?,?,?,?)",new Object[]{id,today(),ml,System.currentTimeMillis()});return id;}
    public synchronized void undoWater(){db().execSQL("DELETE FROM water_log WHERE id=(SELECT id FROM water_log WHERE day=? ORDER BY created DESC,rowid DESC LIMIT 1)",new Object[]{today()});}
    public synchronized long totalWater(String day){try(Cursor c=db().rawQuery("SELECT COALESCE(SUM(ml),0) FROM water_log WHERE day=?",new String[]{day})){c.moveToFirst();return c.getLong(0);}}
    public synchronized List<JSONObject> waterHistory(){return query(db(),"SELECT day,SUM(ml) AS total FROM water_log GROUP BY day ORDER BY day DESC LIMIT 30");}
    public synchronized int setting(String key,int fallback){List<JSONObject> rows=query(db(),"SELECT v FROM life_settings WHERE k=?",key);if(rows.isEmpty())return fallback;try{int value=Integer.parseInt(rows.get(0).optString("v"));return value>=50&&value<=(key.equals("water_cup")?3000:10000)?value:fallback;}catch(NumberFormatException e){return fallback;}}
    public synchronized void putSetting(String key,int value){if(!Set.of("water_goal","water_cup").contains(key))throw new IllegalArgumentException("Unknown setting.");int maximum=key.equals("water_cup")?3000:10000;if(value<50||value>maximum)throw new IllegalArgumentException("Choose a value between 50 and "+maximum+" ml.");db().execSQL("INSERT OR REPLACE INTO life_settings(k,v) VALUES(?,?)",new Object[]{key,""+value});}
    public synchronized JSONObject reminder(String id){List<JSONObject> rows=query(db(),"SELECT * FROM reminders WHERE id=?",id);return rows.isEmpty()?null:rows.get(0);}
    public synchronized List<JSONObject> reminders(){return query(db(),"SELECT * FROM reminders ORDER BY enabled DESC,next_at LIMIT 200");}
    public synchronized List<JSONObject> activeReminders(){return query(db(),"SELECT * FROM reminders WHERE enabled=1 ORDER BY next_at");}
    public synchronized String saveReminder(String id,String title,String body,String kind,String repeat,String mode,long at,int minute,int interval,int start,int end,String task){
        InputLimits.REMINDER.require(title);InputLimits.DETAIL.require(body);
        if(!Set.of("general","water","journal").contains(kind)||!Set.of("none","daily","interval").contains(repeat)||!Set.of("gentle","ring").contains(mode))throw new IllegalArgumentException("Invalid reminder options.");
        if(at<=System.currentTimeMillis())throw new IllegalArgumentException("Choose a reminder time in the future.");if(minute<0||minute>1439)throw new IllegalArgumentException("Choose a valid time.");if(repeat.equals("interval"))AlarmSchedule.next(repeat,minute,interval,start,end,System.currentTimeMillis(),ZoneId.systemDefault());
        JSONObject old=id==null?null:reminder(id);if(old==null)id=id();ContentValues v=new ContentValues();v.put("id",id);v.put("title",title.trim());v.put("body",body.trim());v.put("kind",kind);v.put("repeat_rule",repeat);v.put("mode",mode);v.put("task_id",task==null?"":task);v.put("next_at",at);v.put("minute",minute);v.put("interval_min",interval);v.put("start_min",start);v.put("end_min",end);v.put("enabled",1);v.put("revision",old==null?1:old.optInt("revision")+1);v.put("created",old==null?System.currentTimeMillis():old.optLong("created"));db().insertWithOnConflict("reminders",null,v,SQLiteDatabase.CONFLICT_REPLACE);return id;
    }
    public synchronized void enable(String id,boolean enabled){JSONObject r=reminder(id);if(r==null)return;long at=r.optLong("next_at");if(enabled&&at<=System.currentTimeMillis()){if(r.optString("repeat_rule").equals("none"))throw new IllegalArgumentException("Edit this reminder to choose a future time.");at=next(r,System.currentTimeMillis());}db().execSQL("UPDATE reminders SET enabled=?,next_at=?,revision=revision+1 WHERE id=?",new Object[]{enabled?1:0,at,id});}
    public synchronized void deleteReminder(String id){db().delete("reminders","id=?",new String[]{id});}
    public synchronized long next(JSONObject r,long after){return AlarmSchedule.next(r.optString("repeat_rule"),r.optInt("minute"),r.optInt("interval_min"),r.optInt("start_min"),r.optInt("end_min"),after,ZoneId.systemDefault());}
    /** Compare-and-update revision makes duplicate and stale alarm broadcasts harmless. */
    public synchronized JSONObject fire(String id,int revision,long now){
        db().beginTransaction();try{JSONObject r=reminder(id);if(r==null||r.optInt("enabled")==0||r.optInt("revision")!=revision||r.optLong("next_at")>now+1500)return null;
            String event=id();long next=0;boolean repeat=!r.optString("repeat_rule").equals("none");if(repeat)next=next(r,now);
            db().execSQL("UPDATE reminders SET enabled=?,next_at=?,revision=revision+1 WHERE id=?",new Object[]{repeat?1:0,next,id});
            db().execSQL("INSERT INTO alarm_events(id,reminder_id,title,body,kind,mode,state,created) VALUES(?,?,?,?,?,?,?,?)",new Object[]{event,id,r.optString("title"),r.optString("body"),r.optString("kind"),r.optString("mode"),"pending",now});db().setTransactionSuccessful();return event(event);
        }finally{db().endTransaction();}
    }
    public synchronized JSONObject event(String id){List<JSONObject> rows=query(db(),"SELECT * FROM alarm_events WHERE id=?",id);return rows.isEmpty()?null:rows.get(0);}
    public synchronized void eventState(String id,String state){if(!Set.of("pending","ringing","dismissed","snoozed","missed").contains(state))throw new IllegalArgumentException("Invalid event state.");db().execSQL("UPDATE alarm_events SET state=? WHERE id=?",new Object[]{state,id});}
    public synchronized void snooze(String event){JSONObject e=event(event);if(e==null)return;JSONObject r=reminder(e.optString("reminder_id"));if(r!=null){db().execSQL("UPDATE reminders SET enabled=1,next_at=?,revision=revision+1 WHERE id=?",new Object[]{System.currentTimeMillis()+600000,r.optString("id")});}eventState(event,"snoozed");}
    public synchronized List<JSONObject> recentEvents(){return query(db(),"SELECT * FROM alarm_events ORDER BY created DESC LIMIT 20");}
    /** Calendar boundaries use the phone's zone, including 23/25-hour daylight-saving days. */
    static long[] dayBounds(LocalDate day,ZoneId zone){return new long[]{day.atStartOfDay(zone).toInstant().toEpochMilli(),day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()};}
    public synchronized List<JSONObject> remindersToday(){long[] range=dayBounds(LocalDate.now(),ZoneId.systemDefault());return query(db(),"SELECT * FROM reminders WHERE enabled=1 AND next_at>=? AND next_at<? ORDER BY next_at LIMIT 200",""+range[0],""+range[1]);}
    public synchronized List<JSONObject> eventsToday(int offset){return eventsOn(LocalDate.now(),ZoneId.systemDefault(),offset);}
    synchronized List<JSONObject> eventsOn(LocalDate day,ZoneId zone,int offset){long[] range=dayBounds(day,zone);return query(db(),"SELECT * FROM alarm_events WHERE created>=? AND created<? ORDER BY created DESC,rowid DESC LIMIT 21 OFFSET ?",""+range[0],""+range[1],""+Math.max(0,offset));}
    synchronized JSONObject exportData() throws JSONException {
        JSONObject out=new JSONObject();for(String table:new String[]{"todos","journals","water_log","life_settings","reminders","alarm_events"}){JSONArray rows=new JSONArray();for(JSONObject row:query(db(),"SELECT * FROM "+table))rows.put(row);out.put(table,rows);}return out;
    }
    static void legacyText(JSONObject row,String key,int maximum,boolean required)throws JSONException{
        String value=row.getString(key);InputLimits.Count count=InputLimits.count(value);
        if(value.length()>maximum||count.invalid||(required&&!count.visible))throw new IllegalArgumentException("Invalid "+key+" in organizer backup.");
    }
    static void backupId(JSONObject row,String key)throws JSONException{UUID.fromString(row.getString(key));}
    static void backupDay(JSONObject row,boolean optional)throws JSONException{String day=row.getString("day");if(!optional||!day.isEmpty())LocalDate.parse(day);}
    static void validateRestored(String table,JSONObject row)throws JSONException{
        switch(table){
            case "todos":backupId(row,"id");legacyText(row,"title",500,true);backupDay(row,true);if(row.getInt("done")<0||row.getInt("done")>1)throw new IllegalArgumentException("Invalid task state in backup.");break;
            case "journals":backupDay(row,false);break;
            case "water_log":backupId(row,"id");backupDay(row,false);break;
            case "reminders":
                backupId(row,"id");if(!row.getString("task_id").isEmpty())backupId(row,"task_id");
                legacyText(row,"title",200,true);legacyText(row,"body",2000,false);
                if(!Set.of("general","water","journal").contains(row.getString("kind"))||!Set.of("gentle","ring").contains(row.getString("mode"))||!Set.of("none","daily","interval").contains(row.getString("repeat_rule"))||row.getInt("minute")<0||row.getInt("minute")>1439||row.getLong("revision")<1||row.getLong("revision")>=Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid reminder in backup.");
                if(row.getString("repeat_rule").equals("interval"))AlarmSchedule.next("interval",row.getInt("minute"),row.getInt("interval_min"),row.getInt("start_min"),row.getInt("end_min"),System.currentTimeMillis(),ZoneId.systemDefault());break;
            case "alarm_events":backupId(row,"id");backupId(row,"reminder_id");legacyText(row,"title",200,true);legacyText(row,"body",2000,false);if(!Set.of("pending","ringing","dismissed","snoozed","missed").contains(row.getString("state"))||!Set.of("general","water","journal").contains(row.getString("kind"))||!Set.of("gentle","ring").contains(row.getString("mode")))throw new IllegalArgumentException("Invalid alarm event in backup.");break;
            default:break;
        }
    }
    synchronized void restoreData(JSONObject data,Map<Long,Long> notes) throws JSONException {
        if(data==null)return;
        for(String table:new String[]{"todos","journals","water_log","life_settings","reminders","alarm_events"}){
            JSONArray rows=data.optJSONArray(table);if(rows==null)continue;
            for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);validateRestored(table,row);ContentValues v=new ContentValues();
                switch(table){
                    case "todos":for(String key:new String[]{"id","title","day"})v.put(key,row.getString(key));v.put("done",row.optInt("done")==0?0:1);v.put("created",row.getLong("created"));v.put("updated",row.getLong("updated"));break;
                    case "journals":Long mapped=notes.get(row.getLong("note_id"));if(mapped==null)continue;v.put("day",row.getString("day"));v.put("note_id",mapped);break;
                    case "water_log":if(row.getInt("ml")<1||row.getInt("ml")>3000)throw new IllegalArgumentException("Invalid water entry in backup.");for(String key:new String[]{"id","day"})v.put(key,row.getString(key));v.put("ml",row.getInt("ml"));v.put("created",row.getLong("created"));break;
                    case "life_settings":String key=row.getString("k");if(!Set.of("water_goal","water_cup").contains(key))continue;int value=Integer.parseInt(row.getString("v"));if(value<50||value>(key.equals("water_cup")?3000:10000))throw new IllegalArgumentException("Invalid water setting in backup.");v.put("k",key);v.put("v",""+value);break;
                    case "reminders":for(String field:new String[]{"id","title","body","kind","repeat_rule","mode","task_id"})v.put(field,row.getString(field));for(String field:new String[]{"next_at","minute","interval_min","start_min","end_min","revision","created"})v.put(field,row.getLong(field));v.put("enabled",0);break; // restored alarms need explicit re-enabling
                    default:for(String field:new String[]{"id","reminder_id","title","body","kind","mode"})v.put(field,row.getString(field));v.put("created",row.getLong("created"));v.put("state",Set.of("pending","ringing").contains(row.optString("state"))?"missed":row.optString("state"));
                }
                if(db().insertWithOnConflict(table,null,v,SQLiteDatabase.CONFLICT_IGNORE)==-1){/* Existing local rows win on a repeated import. */}
            }
        }
    }
}
