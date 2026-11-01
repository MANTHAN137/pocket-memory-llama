package com.pocketmemory.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Originals, overlapping passages, FTS4 and normalized vectors share one private database. */
public final class MemoryStore extends SQLiteOpenHelper {
    final Context appContext;
    public static final class Note {
        public long id,created,updated;
        public String title,body,context="",status="active";
        public long supersedes;
        public Note(long id,String title,String body,long created,long updated) { this.id=id;this.title=title;this.body=body;this.created=created;this.updated=updated; }
    }
    public static final class Passage {
        public long id,noteId,updated;
        public String title,text,context="";
        public double score;
        Passage(long id,long noteId,String title,String text,long updated) { this.id=id;this.noteId=noteId;this.title=title;this.text=text;this.updated=updated; }
    }
    private static final Set<String> STOP=new HashSet<>(Arrays.asList("a","an","the","is","are","was","were","i","my","me","we","our","you","your","do","does","did","what","where","when","how","why","can","could","please","tell","about","of","in","on","to","for","and","or","it","that","this","have","has","saved","remember"));
    public MemoryStore(Context c){this(c,"memory.db");}
    MemoryStore(Context c,String name){super(c,name,null,4);appContext=c.getApplicationContext();setWriteAheadLoggingEnabled(true);}
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true);try(Cursor c=db.rawQuery("PRAGMA secure_delete=ON",null)){c.moveToFirst();} }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY,title TEXT NOT NULL,body TEXT NOT NULL,created INTEGER NOT NULL,updated INTEGER NOT NULL,context TEXT NOT NULL DEFAULT '',status TEXT NOT NULL DEFAULT 'active',supersedes INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE passages(id INTEGER PRIMARY KEY,note_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,ordinal INTEGER NOT NULL,text TEXT NOT NULL,vector BLOB,vector_model TEXT)");
        db.execSQL("CREATE INDEX passages_note ON passages(note_id)");
        db.execSQL("CREATE VIRTUAL TABLE search USING fts4(title,text,tokenize=unicode61)");
        db.execSQL("CREATE TABLE conversations(id INTEGER PRIMARY KEY,question TEXT NOT NULL,answer TEXT NOT NULL,sources TEXT NOT NULL,created INTEGER NOT NULL,source_snapshots TEXT NOT NULL DEFAULT '[]')");
        createHistory(db);createFeatures(db);createParts(db);
    }
    private void createHistory(SQLiteDatabase db){
        // Stable IDs prevent a deleted memory being mistaken for an unrelated future memory in history.
        db.execSQL("CREATE TABLE identity_counter(next_note INTEGER NOT NULL)");
        db.execSQL("INSERT INTO identity_counter SELECT MAX(COALESCE(MAX(id),0)+1,?) FROM notes",new Object[]{System.currentTimeMillis()});
        db.execSQL("CREATE TABLE versions(id INTEGER PRIMARY KEY,note_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,revision INTEGER NOT NULL,title TEXT NOT NULL,body TEXT NOT NULL,context TEXT NOT NULL,created INTEGER NOT NULL,kind TEXT NOT NULL,UNIQUE(note_id,revision))");
        db.execSQL("CREATE TABLE relations(left_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,right_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,state TEXT NOT NULL,created INTEGER NOT NULL,PRIMARY KEY(left_id,right_id))");
    }
    private void createParts(SQLiteDatabase db){db.execSQL("CREATE TABLE note_parts(group_id TEXT NOT NULL,note_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,ordinal INTEGER NOT NULL,source_title TEXT NOT NULL,PRIMARY KEY(group_id,ordinal))");db.execSQL("CREATE INDEX parts_note ON note_parts(note_id)");}
    private void createFeatures(SQLiteDatabase db){LifeStore.create(db);ChatStore.create(db);PhotoStore.create(db);}
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion<2){
            db.execSQL("ALTER TABLE notes ADD COLUMN context TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE notes ADD COLUMN status TEXT NOT NULL DEFAULT 'active'");
            db.execSQL("ALTER TABLE notes ADD COLUMN supersedes INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE conversations ADD COLUMN source_snapshots TEXT NOT NULL DEFAULT '[]'");
            createHistory(db);
            db.execSQL("INSERT INTO versions(note_id,revision,title,body,context,created,kind) SELECT id,1,title,body,context,updated,'imported initial version' FROM notes");
        }
        if(oldVersion<3)createFeatures(db);if(oldVersion<4)createParts(db);
    }
    public synchronized Note note(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title,body,created,updated,context,status,supersedes FROM notes WHERE id=?",new String[]{""+id})) {
            return c.moveToFirst()?readNote(c):null;
        }
    }
    private static Note readNote(Cursor c){Note n=new Note(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),c.getLong(4));n.context=c.getString(5);n.status=c.getString(6);n.supersedes=c.getLong(7);return n;}
    public synchronized List<Note> notes(String query) {return listNotes(query,false);}
    public synchronized List<Note> allNotes(String query) {return listNotes(query,true);}
    /** The library draws one page of short previews; exports and retrieval still see full records. */
    public synchronized List<Note> notePreviews(String query,int offset){
        if(query==null)query="";if(query.length()>200)throw new IllegalArgumentException("Keep a library search under 200 characters.");
        String pattern="%"+query.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        List<Note> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title,substr(body,1,180),created,updated,context,status,supersedes FROM notes WHERE status='active' AND (title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\') ORDER BY updated DESC,id DESC LIMIT 51 OFFSET ?",new String[]{pattern,pattern,""+Math.max(0,offset)})){while(c.moveToNext())out.add(readNote(c));}return out;
    }
    private List<Note> listNotes(String query,boolean archived) {
        ArrayList<Note> out=new ArrayList<>();
        String sql="SELECT id,title,body,created,updated,context,status,supersedes FROM notes WHERE "+(archived?"1=1":"status='active'");
        String[] args=null;
        if(query!=null&&!query.trim().isEmpty()) { sql+=" AND (title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\')";String s="%"+query.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";args=new String[]{s,s}; }
        try(Cursor c=getReadableDatabase().rawQuery(sql+" ORDER BY updated DESC",args)) {
            while(c.moveToNext())out.add(readNote(c));
        } return out;
    }
    public synchronized long save(long id,String title,String body) {Note n=id>0?note(id):null;return save(id,title,body,n==null?"":n.context);}
    public synchronized long save(long id,String title,String body,String context) {
        InputLimits.note(title,body,context);
        return saveInternal(id,title,body,context);
    }
    /** Only validated legacy backup restoration may retain older, larger notes. */
    private long saveInternal(long id,String title,String body,String context){
        if(title==null||title.trim().isEmpty())title="Untitled memory";
        if(context==null)context="";
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            long now=System.currentTimeMillis();ContentValues values=new ContentValues();values.put("title",title.trim());values.put("body",body);values.put("updated",now);values.put("context",context.trim());
            if(id>0) { removePassages(db,id);if(db.update("notes",values,"id=?",new String[]{""+id})!=1)throw new IllegalArgumentException("This memory was deleted."); }
            else {try(Cursor sequence=db.rawQuery("SELECT next_note FROM identity_counter",null)){sequence.moveToFirst();values.put("id",sequence.getLong(0));}db.execSQL("UPDATE identity_counter SET next_note=next_note+1");values.put("created",now);id=db.insertOrThrow("notes",null,values);}
            recordVersion(db,id,id>0&&versions(id).length()>0?"edited / corrected":"saved");
            Note current=note(id);if(current.status.equals("active"))addPassages(db,current);
            db.setTransactionSuccessful();return id;
        } finally { db.endTransaction(); }
    }
    public synchronized List<JSONObject> documentParts(long note){return LifeStore.query(getReadableDatabase(),"SELECT p.*,n.title,n.status FROM note_parts p JOIN notes n ON n.id=p.note_id WHERE p.group_id=(SELECT group_id FROM note_parts WHERE note_id=? ORDER BY group_id LIMIT 1) ORDER BY ordinal",""+note);}
    public synchronized List<Long> saveSplit(long original,String title,String text,String context,java.util.function.LongConsumer attachFirst){
        InputLimits.TITLE.require(title);InputLimits.CONTEXT.require(context);List<String> parts=NoteSplitter.split(text);
        if(parts.size()<2)throw new IllegalArgumentException("This text fits in one memory. Use Save memory.");
        Note source=original>0?note(original):null;if(original>0&&(source==null||!source.status.equals("active")))throw new IllegalArgumentException("The original memory changed. Open it again.");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            String group=UUID.randomUUID().toString();List<Long> ids=new ArrayList<>();
            for(int i=0;i<parts.size();i++){long id=save(0,NoteSplitter.title(title,i+1,parts.size()),parts.get(i),context);ids.add(id);db.execSQL("INSERT INTO note_parts(group_id,note_id,ordinal,source_title) VALUES(?,?,?,?)",new Object[]{group,id,i+1,title});}
            if(source!=null){db.execSQL("UPDATE notes SET status='superseded',updated=? WHERE id=?",new Object[]{System.currentTimeMillis(),original});removePassages(db,original);recordVersion(db,original,"split into linked memories; original retained");db.execSQL("INSERT INTO note_parts(group_id,note_id,ordinal,source_title) VALUES(?,?,0,?)",new Object[]{group,original,title});}
            if(attachFirst!=null)attachFirst.accept(ids.get(0));db.setTransactionSuccessful();return ids;
        }finally{db.endTransaction();}
    }
    private void addPassages(SQLiteDatabase db,Note note){
            List<String> chunks=split(note.body,1000,140);int ordinal=0;
            for(String text:chunks) {
                ContentValues p=new ContentValues();p.put("note_id",note.id);p.put("ordinal",ordinal++);p.put("text",text);long row=db.insertOrThrow("passages",null,p);
                db.execSQL("INSERT INTO search(rowid,title,text) VALUES(?,?,?)",new Object[]{row,note.title,text});
            }
    }
    private void recordVersion(SQLiteDatabase db,long id,String kind){
        db.execSQL("INSERT INTO versions(note_id,revision,title,body,context,created,kind) SELECT id,(SELECT COALESCE(MAX(revision),0)+1 FROM versions WHERE note_id=?),title,body,context,updated,? FROM notes WHERE id=?",new Object[]{id,kind,id});
    }
    private void removePassages(SQLiteDatabase db,long id) {
        db.execSQL("DELETE FROM search WHERE rowid IN (SELECT id FROM passages WHERE note_id=?)",new Object[]{id});
        db.delete("passages","note_id=?",new String[]{""+id});
    }
    public synchronized void delete(long id) { List<JSONObject> images=PhotoStore.forNote(this,id);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try {removePassages(db,id);db.delete("notes","id=?",new String[]{""+id});db.setTransactionSuccessful();}finally{db.endTransaction();}for(JSONObject image:images)PhotoStore.file(appContext,image.optString("filename")).delete(); }
    public static List<String> split(String text,int maximum,int overlap) {
        ArrayList<String> out=new ArrayList<>();int start=0;
        while(start<text.length()) {
            int end=Math.min(text.length(),start+maximum);
            if(end<text.length()) {
                int boundary=text.lastIndexOf(' ',end);
                if(boundary>start+maximum*2/3)end=boundary;
                if(end>start&&Character.isHighSurrogate(text.charAt(end-1)))end--;
            }
            String part=text.substring(start,end).trim();if(!part.isEmpty())out.add(part);
            if(end==text.length())break;
            int next=Math.max(start+1,end-overlap);
            if(next<text.length()&&Character.isLowSurrogate(text.charAt(next)))next++;
            start=next;
        }return out;
    }
    public synchronized List<Passage> unindexed(String model) {
        List<Passage> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT p.id,n.id,n.title,p.text,n.updated,n.context FROM passages p JOIN notes n ON n.id=p.note_id WHERE n.status='active' AND (p.vector IS NULL OR p.vector_model<>?) ORDER BY p.id",new String[]{model})) {
            while(c.moveToNext())out.add(passage(c));
        }return out;
    }
    public synchronized void vector(Passage p,float[] vector,String model) {
        ByteBuffer bytes=ByteBuffer.allocate(vector.length*4).order(ByteOrder.LITTLE_ENDIAN);for(float v:vector)bytes.putFloat(v);
        ContentValues values=new ContentValues();values.put("vector",bytes.array());values.put("vector_model",model);
        getWritableDatabase().update("passages",values,"id=? AND text=?",new String[]{""+p.id,p.text});
    }
    public synchronized void clearVectors() { getWritableDatabase().execSQL("UPDATE passages SET vector=NULL,vector_model=NULL"); }
    public synchronized int[] counts() {
        int[] out=new int[3];int i=0;for(String sql:new String[]{"SELECT count(*) FROM notes WHERE status='active'","SELECT count(*) FROM passages","SELECT count(*) FROM passages WHERE vector IS NOT NULL"})try(Cursor c=getReadableDatabase().rawQuery(sql,null)){c.moveToFirst();out[i++]=c.getInt(0);}return out;
    }
    private static Passage passage(Cursor c) { Passage p=new Passage(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getLong(4));p.context=c.getString(5);return p; }
    static List<String> terms(String question) {
        LinkedHashSet<String> terms=new LinkedHashSet<>();Matcher m=Pattern.compile("[\\p{L}\\p{N}]{2,}").matcher(question.toLowerCase(Locale.ROOT));
        while(m.find()&&terms.size()<24) {String word=m.group();if(!STOP.contains(word))terms.add(word);}return new ArrayList<>(terms);
    }
    /** Evidence checks inspect the full bounded text; only query expansion is capped at 24 terms. */
    static List<String> allTerms(String text) {
        LinkedHashSet<String> terms=new LinkedHashSet<>();
        Matcher m=Pattern.compile("[\\p{L}\\p{N}]{1,}").matcher(text.toLowerCase(Locale.ROOT));
        while(m.find()){String word=m.group();if(!STOP.contains(word))terms.add(word);}
        return new ArrayList<>(terms);
    }
    public synchronized List<Passage> retrieve(String question,float[] query,String model,int limit) {
        LinkedHashMap<Long,Passage> all=new LinkedHashMap<>();Map<Long,Double> lexical=new HashMap<>(),semantic=new HashMap<>();
        List<String> terms=terms(question);
        if(!terms.isEmpty()) {
            StringBuilder match=new StringBuilder();for(String t:terms){if(match.length()>0)match.append(" OR ");match.append('"').append(t).append('"');}
            try(Cursor c=getReadableDatabase().rawQuery("SELECT p.id,n.id,n.title,p.text,n.updated,n.context FROM search s JOIN passages p ON p.id=s.rowid JOIN notes n ON n.id=p.note_id WHERE n.status='active' AND search MATCH ?",new String[]{match.toString()})) {
                while(c.moveToNext()){Passage p=passage(c);all.put(p.id,p);String content=(p.title+" "+p.text).toLowerCase(Locale.ROOT);double score=0;for(String t:terms)if(content.contains(t))score+=1;lexical.put(p.id,score/terms.size());}
            }
        }
        if(query!=null)try(Cursor c=getReadableDatabase().rawQuery("SELECT p.id,n.id,n.title,p.text,n.updated,n.context,p.vector FROM passages p JOIN notes n ON n.id=p.note_id WHERE n.status='active' AND vector_model=? AND vector IS NOT NULL",new String[]{model})) {
            while(c.moveToNext()) {
                byte[] bytes=c.getBlob(6);if(bytes.length!=query.length*4)continue;ByteBuffer v=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);double score=0;for(float q:query)score+=q*v.getFloat();
                if(score>=0.30){Passage p=passage(c);all.putIfAbsent(p.id,p);semantic.put(p.id,score);}
            }
        }
        List<Long> lex=new ArrayList<>(lexical.keySet()),sem=new ArrayList<>(semantic.keySet());
        lex.sort((a,b)->Double.compare(lexical.get(b),lexical.get(a)));sem.sort((a,b)->Double.compare(semantic.get(b),semantic.get(a)));
        // Reciprocal rank fusion preserves both exact matches and semantic matches.
        for(int i=0;i<lex.size();i++)all.get(lex.get(i)).score+=1.0/(30+i);
        for(int i=0;i<sem.size();i++)all.get(sem.get(i)).score+=1.0/(30+i);
        ArrayList<Passage> ranked=new ArrayList<>(all.values());ranked.sort((a,b)->{int cmp=Double.compare(b.score,a.score);return cmp==0?Long.compare(b.updated,a.updated):cmp;});
        ArrayList<Passage> result=new ArrayList<>();Map<Long,Integer> perNote=new HashMap<>();
        for(Passage p:ranked){int n=perNote.getOrDefault(p.noteId,0);if(n>=2)continue;perNote.put(p.noteId,n+1);result.add(p);if(result.size()==limit)break;}return result;
    }
    /** Only explicit user choices establish correction or context; detection never establishes truth. */
    public synchronized long saveRelated(String title,String body,String context,long earlier,String resolution){
        if(!Set.of("corrected","context","unresolved").contains(resolution))throw new IllegalArgumentException("Choose a relationship.");
        Note old=note(earlier);if(old==null||!old.status.equals("active"))throw new IllegalArgumentException("The earlier memory changed. Open it again.");
        if(resolution.equals("context")&&(context==null||context.isBlank()))throw new IllegalArgumentException("Add context to distinguish these memories.");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            long id=save(0,title,body,context);
            db.execSQL("INSERT INTO relations(left_id,right_id,state,created) VALUES(?,?,?,?)",new Object[]{earlier,id,resolution,System.currentTimeMillis()});
            if(resolution.equals("corrected")){
                db.execSQL("UPDATE notes SET status='superseded' WHERE id=?",new Object[]{earlier});removePassages(db,earlier);
                db.execSQL("UPDATE notes SET supersedes=? WHERE id=?",new Object[]{earlier,id});
            }
            db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    public synchronized void resolve(long earlier,long later,String resolution,String context){
        Note n=note(later),old=note(earlier);if(n==null||old==null)throw new IllegalArgumentException("This memory was deleted.");
        if(!Set.of("corrected","context").contains(resolution))throw new IllegalArgumentException("Choose a resolution.");
        if(resolution.equals("context")&&(context==null||context.isBlank()))throw new IllegalArgumentException("Add context first.");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            if(resolution.equals("context"))save(later,n.title,n.body,context);
            else {db.execSQL("UPDATE notes SET status='superseded' WHERE id=?",new Object[]{earlier});removePassages(db,earlier);db.execSQL("UPDATE notes SET supersedes=? WHERE id=?",new Object[]{earlier,later});}
            db.execSQL("UPDATE relations SET state=? WHERE left_id=? AND right_id=?",new Object[]{resolution,earlier,later});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public synchronized List<Note> possibleConflicts(long except,String body){
        Map<String,String> claims=claims(body);List<Note> found=new ArrayList<>();if(claims.isEmpty())return found;
        for(Note n:notes(""))if(n.id!=except){Map<String,String> other=claims(n.body);for(String subject:claims.keySet())if(other.containsKey(subject)&&!other.get(subject).equals(claims.get(subject))){found.add(n);break;}if(found.size()==8)break;}return found;
    }
    private static Map<String,String> claims(String body){
        Map<String,String> claims=new HashMap<>();Matcher m=Pattern.compile("(?:^|[.!?\\n])\\s*([\\p{L}][\\p{L}\\p{N} _'-]{0,60}?)\\s+(is|are|was|were)\\s+([^.!?\\n]{1,120})",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(body);
        while(m.find())claims.put((m.group(1)+" "+m.group(2)).trim().toLowerCase(Locale.ROOT),m.group(3).trim().toLowerCase(Locale.ROOT));return claims;
    }
    public synchronized JSONArray versions(long noteId){
        JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT revision,title,body,context,created,kind FROM versions WHERE note_id=? ORDER BY revision DESC",new String[]{""+noteId})){while(c.moveToNext())try{out.put(new JSONObject().put("revision",c.getInt(0)).put("title",c.getString(1)).put("body",c.getString(2)).put("context",c.getString(3)).put("created",c.getLong(4)).put("kind",c.getString(5)));}catch(Exception e){throw new IllegalStateException(e);}}return out;
    }
    public synchronized JSONArray relations(long id){
        JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT left_id,right_id,state,created FROM relations WHERE left_id=? OR right_id=? ORDER BY created",new String[]{""+id,""+id})){while(c.moveToNext())try{out.put(new JSONObject().put("earlier",c.getLong(0)).put("later",c.getLong(1)).put("state",c.getString(2)).put("created",c.getLong(3)));}catch(Exception e){throw new IllegalStateException(e);}}return out;
    }
    public synchronized Set<Long> relatedIds(long id){
        Set<Long> found=new LinkedHashSet<>();ArrayDeque<Long> queue=new ArrayDeque<>();found.add(id);queue.add(id);
        while(!queue.isEmpty()){long current=queue.remove();JSONArray links=relations(current);for(int i=0;i<links.length();i++){JSONObject j=links.optJSONObject(i);for(long other:new long[]{j.optLong("earlier"),j.optLong("later")})if(found.add(other))queue.add(other);}}return found;
    }
    public synchronized List<Passage> unresolvedEvidence(List<Passage> hits){
        Set<Long> ids=new LinkedHashSet<>();for(Passage p:hits)ids.add(p.noteId);Set<Long> conflicts=new LinkedHashSet<>();
        for(long id:ids){JSONArray links=relations(id);for(int i=0;i<links.length();i++){JSONObject link=links.optJSONObject(i);if(link.optString("state").equals("unresolved")){Note a=note(link.optLong("earlier")),b=note(link.optLong("later"));if(a!=null&&b!=null&&a.status.equals("active")&&b.status.equals("active")){conflicts.add(a.id);conflicts.add(b.id);}}}}
        List<Passage> result=new ArrayList<>();for(long id:conflicts){Note n=note(id);Passage p=new Passage(0,n.id,n.title,n.body,n.updated);p.context=n.context;result.add(p);}return result;
    }
    private static JSONObject snapshot(Passage p) throws Exception{return new JSONObject().put("note_id",p.noteId).put("title",p.title).put("text",p.text).put("context",p.context).put("updated",p.updated);}
    public synchronized void conversation(String q,String a,List<Passage> passages) {
        JSONArray refs=new JSONArray(),snapshots=new JSONArray();for(Passage p:passages){refs.put(p.noteId);try{snapshots.put(snapshot(p));}catch(Exception e){throw new IllegalStateException(e);}}
        ContentValues v=new ContentValues();v.put("question",q);v.put("answer",a);v.put("sources",refs.toString());v.put("source_snapshots",snapshots.toString());v.put("created",System.currentTimeMillis());getWritableDatabase().insertOrThrow("conversations",null,v);
    }
    public synchronized JSONArray conversations(){return conversations("",0,0);}
    /** Search every stored conversation, paging 100 at a time; note history includes related corrections. */
    public synchronized JSONArray conversations(String query,long noteId,int offset){
        JSONArray out=new JSONArray();Set<Long> related=noteId>0?relatedIds(noteId):Collections.emptySet();int skipped=0;
        String needle=query.toLowerCase(Locale.ROOT);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,question,answer,sources,created,source_snapshots FROM conversations ORDER BY id DESC",null)){
            while(c.moveToNext())try{
                JSONArray refs=new JSONArray(c.getString(3));boolean match=noteId==0;for(int i=0;i<refs.length();i++)if(related.contains(refs.optLong(i)))match=true;
                if(!match||!(c.getString(1)+" "+c.getString(2)+" "+c.getString(5)).toLowerCase(Locale.ROOT).contains(needle))continue;
                if(skipped++<offset)continue;
                out.put(new JSONObject().put("id",c.getLong(0)).put("question",c.getString(1)).put("answer",c.getString(2)).put("sources",refs).put("created",c.getLong(4)).put("source_snapshots",new JSONArray(c.getString(5))));if(out.length()==100)break;
            }catch(Exception e){throw new IllegalStateException(e);}
        }return out;
    }
    public synchronized void clearConversations(){getWritableDatabase().delete("conversations",null,null);}
    public synchronized void erasePersonalData(){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{for(String table:new String[]{"note_parts","messages","chats","journals","photos","versions","relations","passages","conversations","search","todos","water_log","life_settings","alarm_events","reminders","notes"})db.delete(table,null,null);db.setTransactionSuccessful();}finally{db.endTransaction();}
        // Preserve the identity counter so removed IDs can never resolve to a future note.
        try(android.database.Cursor c=db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)",null)){c.moveToFirst();}
        db.execSQL("VACUUM");
    }
    public synchronized JSONObject export() throws Exception {
        JSONArray notes=new JSONArray();for(Note n:allNotes(""))notes.put(new JSONObject().put("id",n.id).put("title",n.title).put("body",n.body).put("context",n.context).put("status",n.status).put("supersedes",n.supersedes).put("created",n.created).put("updated",n.updated).put("versions",versions(n.id)).put("relations",relations(n.id)));
        JSONArray history=new JSONArray();for(int offset=0;;offset+=100){JSONArray page=conversations("",0,offset);for(int i=0;i<page.length();i++)history.put(page.get(i));if(page.length()<100)break;}
        return new JSONObject().put("format","pocket-memory-4").put("note_parts",new JSONArray(LifeStore.query(getReadableDatabase(),"SELECT * FROM note_parts ORDER BY group_id,ordinal"))).put("notes",notes).put("conversations",history).put("organizer",new LifeStore(this).exportData()).put("chat",new ChatStore(this).exportData()).put("photos",PhotoStore.exportData(this));
    }
    public synchronized int restore(JSONObject backup) throws Exception {
        if(!Set.of("pocket-memory-1","pocket-memory-2","pocket-memory-3","pocket-memory-4").contains(backup.optString("format")))throw new IllegalArgumentException("This is not a Pocket Memory backup.");
        JSONArray notes=backup.getJSONArray("notes");
        for(int i=0;i<notes.length();i++){JSONObject n=notes.getJSONObject(i);String body=n.getString("body");n.getString("title");if(body.isBlank()||body.length()>250_000||n.getString("title").length()>10000||n.optString("context").length()>2000||!Set.of("active","superseded").contains(n.optString("status","active")))throw new IllegalArgumentException("Backup contains an invalid note.");}
        Map<String,Long> existing=new HashMap<>();for(Note n:allNotes(""))existing.put(n.title+"\0"+n.body+"\0"+n.context+"\0"+n.status,n.id);
        Map<Long,Long> remap=new HashMap<>();Set<Long> added=new HashSet<>();int imported=0;SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(int i=0;i<notes.length();i++) {
                JSONObject n=notes.getJSONObject(i);String key=n.getString("title")+"\0"+n.getString("body")+"\0"+n.optString("context")+"\0"+n.optString("status","active");Long id=existing.get(key);
                if(id==null){id=saveInternal(0,n.getString("title"),n.getString("body"),n.optString("context"));long created=n.optLong("created",System.currentTimeMillis()),updated=n.optLong("updated",created);ContentValues values=new ContentValues();values.put("created",created);values.put("updated",updated);values.put("status",n.optString("status","active"));db.update("notes",values,"id=?",new String[]{""+id});if(n.optString("status").equals("superseded"))removePassages(db,id);
                    JSONArray history=n.optJSONArray("versions");if(history!=null&&history.length()>0){db.delete("versions","note_id=?",new String[]{""+id});for(int j=0;j<history.length();j++){JSONObject h=history.getJSONObject(j);if(h.getString("body").length()>250000)throw new IllegalArgumentException("Invalid version in backup.");ContentValues v=new ContentValues();v.put("note_id",id);v.put("revision",h.getInt("revision"));v.put("title",h.getString("title"));v.put("body",h.getString("body"));v.put("context",h.optString("context"));v.put("created",h.getLong("created"));v.put("kind",h.optString("kind","restored"));db.insertOrThrow("versions",null,v);}}else{db.delete("versions","note_id=?",new String[]{""+id});recordVersion(db,id,"restored");}
                    existing.put(key,id);added.add(id);imported++;}
                remap.put(n.optLong("id",-(i+1)),id);
            }
            for(int i=0;i<notes.length();i++){JSONObject n=notes.getJSONObject(i);Long id=remap.get(n.optLong("id",-(i+1)));if(!added.contains(id))continue;Long parent=remap.get(n.optLong("supersedes"));if(parent!=null)db.execSQL("UPDATE notes SET supersedes=? WHERE id=?",new Object[]{parent,id});JSONArray links=n.optJSONArray("relations");if(links!=null)for(int j=0;j<links.length();j++){JSONObject link=links.getJSONObject(j);Long a=remap.get(link.optLong("earlier")),b=remap.get(link.optLong("later"));String state=link.getString("state");if(a!=null&&b!=null&&Set.of("corrected","context","unresolved").contains(state))db.execSQL("INSERT OR IGNORE INTO relations(left_id,right_id,state,created) VALUES(?,?,?,?)",new Object[]{a,b,state,link.optLong("created")});}}
            {JSONArray history=backup.optJSONArray("conversations");if(history!=null)for(int i=0;i<history.length();i++){JSONObject h=history.getJSONObject(i);JSONArray refs=h.optJSONArray("sources"),mapped=new JSONArray(),snapshots=h.optJSONArray("source_snapshots")==null?null:new JSONArray(h.getJSONArray("source_snapshots").toString());if(refs!=null)for(int j=0;j<refs.length();j++){Long id=remap.get(refs.optLong(j));if(id!=null)mapped.put(id);}if(snapshots==null)snapshots=new JSONArray();else for(int j=0;j<snapshots.length();j++){JSONObject snap=snapshots.getJSONObject(j);Long id=remap.get(snap.optLong("note_id"));snap.put("note_id",id==null?0:id);}
                long at=h.optLong("created",0);try(Cursor prior=db.rawQuery("SELECT 1 FROM conversations WHERE question=? AND answer=? AND created=? AND source_snapshots=? LIMIT 1",new String[]{h.getString("question"),h.getString("answer"),""+at,snapshots.toString()})){if(prior.moveToFirst())continue;}
                ContentValues v=new ContentValues();v.put("question",h.getString("question"));v.put("answer",h.getString("answer"));v.put("sources",mapped.toString());v.put("source_snapshots",snapshots.toString());v.put("created",at);db.insertOrThrow("conversations",null,v);}}
            JSONArray parts=backup.optJSONArray("note_parts");if(parts!=null)for(int i=0;i<parts.length();i++){
                JSONObject part=parts.getJSONObject(i);String group=part.getString("group_id"),title=part.getString("source_title");UUID.fromString(group);InputLimits.TITLE.require(title);int ordinal=part.getInt("ordinal");Long note=remap.get(part.getLong("note_id"));if(note==null||ordinal<0||ordinal>NoteSplitter.MAX_PARTS)throw new IllegalArgumentException("Invalid document part in backup.");
                try(Cursor prior=db.rawQuery("SELECT note_id,source_title FROM note_parts WHERE group_id=? AND ordinal=?",new String[]{group,""+ordinal})){if(prior.moveToFirst()&&(prior.getLong(0)!=note||!prior.getString(1).equals(title)))throw new IllegalArgumentException("A document group conflicts with an existing local group. Import into a clean copy instead.");}
                db.execSQL("INSERT OR IGNORE INTO note_parts(group_id,note_id,ordinal,source_title) VALUES(?,?,?,?)",new Object[]{group,note,ordinal,title});
            }
            new LifeStore(this).restoreData(backup.optJSONObject("organizer"),remap);
            new ChatStore(this).restoreData(backup.optJSONObject("chat"));
            PhotoStore.restoreData(this,backup.optJSONArray("photos"),remap);
            db.setTransactionSuccessful();return imported;
        } finally {db.endTransaction();}
    }
}
