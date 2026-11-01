package com.pocketmemory.app;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

public final class ChatStore {
    final MemoryStore memory;public ChatStore(MemoryStore memory){this.memory=memory;}SQLiteDatabase db(){return memory.getWritableDatabase();}
    static void create(SQLiteDatabase db){
        db.execSQL("CREATE TABLE chats(id TEXT PRIMARY KEY,title TEXT NOT NULL,created INTEGER NOT NULL,updated INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE messages(id TEXT PRIMARY KEY,chat_id TEXT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,role TEXT NOT NULL,text TEXT NOT NULL,state TEXT NOT NULL,created INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX messages_chat ON messages(chat_id,created)");
    }
    /** A draft has an ID, but is saved only with its first user message. */
    public synchronized String create(){return LifeStore.id();}
    public synchronized List<JSONObject> chats(){return chats(0);}
    public synchronized List<JSONObject> chats(int offset){return LifeStore.query(db(),"SELECT * FROM chats WHERE EXISTS(SELECT 1 FROM messages WHERE chat_id=chats.id) ORDER BY updated DESC,id LIMIT 51 OFFSET ?",""+Math.max(0,offset));}
    public synchronized void pruneEmpty(){db().execSQL("DELETE FROM chats WHERE NOT EXISTS(SELECT 1 FROM messages WHERE chat_id=chats.id)");}
    public synchronized boolean exists(String id){return !LifeStore.query(db(),"SELECT id FROM chats WHERE id=?",id).isEmpty();}
    public synchronized String message(String chat,String role,String text,String state){
        if(!Set.of("user","assistant").contains(role)||text.length()>50000||!Set.of("complete","interrupted","error").contains(state))throw new IllegalArgumentException("Invalid chat message.");if(role.equals("user"))InputLimits.PROMPT.require(text);String id=LifeStore.id();long now=System.currentTimeMillis();
        db().beginTransaction();try{
            if(role.equals("user"))db().execSQL("INSERT OR IGNORE INTO chats(id,title,created,updated) VALUES(?,?,?,?)",new Object[]{chat,"New conversation",now,now});
            // An assistant callback must never recreate a deleted conversation.
            if(!exists(chat))throw new IllegalStateException("This conversation no longer exists.");
            db().execSQL("INSERT INTO messages(id,chat_id,role,text,state,created) VALUES(?,?,?,?,?,?)",new Object[]{id,chat,role,text,state,now});
            if(role.equals("user"))db().execSQL("UPDATE chats SET title=CASE WHEN title='New conversation' THEN ? ELSE title END,updated=? WHERE id=?",new Object[]{text.substring(0,Math.min(60,text.length())),now,chat});else db().execSQL("UPDATE chats SET updated=? WHERE id=?",new Object[]{now,chat});db().setTransactionSuccessful();return id;}finally{db().endTransaction();}
    }
    public synchronized List<JSONObject> messages(String chat,int limit){List<JSONObject> list=LifeStore.query(db(),"SELECT * FROM messages WHERE chat_id=? ORDER BY created DESC,rowid DESC LIMIT ?",chat,""+Math.max(1,Math.min(1000,limit)));Collections.reverse(list);return list;}
    public synchronized void delete(String chat){db().delete("chats","id=?",new String[]{chat});}
    public synchronized void delete(Collection<String> chats){db().beginTransaction();try{for(String chat:chats)delete(chat);db().setTransactionSuccessful();}finally{db().endTransaction();}}
    public synchronized void deleteAll(){db().delete("chats",null,null);}
    synchronized JSONObject exportData() throws JSONException {return new JSONObject().put("chats",new JSONArray(LifeStore.query(db(),"SELECT * FROM chats"))).put("messages",new JSONArray(LifeStore.query(db(),"SELECT * FROM messages ORDER BY created,rowid")));}
    synchronized void restoreData(JSONObject data) throws JSONException {
        if(data==null)return;JSONArray threads=data.optJSONArray("chats"),messages=data.optJSONArray("messages");
        if(threads!=null)for(int i=0;i<threads.length();i++){JSONObject j=threads.getJSONObject(i);ContentValues v=new ContentValues();for(String key:new String[]{"id","title"})v.put(key,j.getString(key));for(String key:new String[]{"created","updated"})v.put(key,j.getLong(key));db().insertWithOnConflict("chats",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
        if(messages!=null)for(int i=0;i<messages.length();i++){JSONObject j=messages.getJSONObject(i);if(!exists(j.getString("chat_id"))||!Set.of("user","assistant").contains(j.getString("role"))||j.getString("text").length()>50000||!Set.of("complete","interrupted","error").contains(j.getString("state")))throw new IllegalArgumentException("Invalid conversation in backup.");ContentValues v=new ContentValues();for(String key:new String[]{"id","chat_id","role","text","state"})v.put(key,j.getString(key));v.put("created",j.getLong("created"));db().insertWithOnConflict("messages",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
        pruneEmpty();
    }
}
