package com.pocketmemory.app;

import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.*;
import android.net.Uri;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Bundled OCR, capped image dimensions, private JPEGs. No image or recognized text is uploaded. */
public final class PhotoStore {
    public static final class Photo {public String id,filename,text="";public int width,height;}
    static void create(SQLiteDatabase db){db.execSQL("CREATE TABLE photos(id TEXT PRIMARY KEY,note_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,filename TEXT NOT NULL UNIQUE,width INTEGER NOT NULL,height INTEGER NOT NULL,created INTEGER NOT NULL)");db.execSQL("CREATE INDEX photo_note ON photos(note_id)");}
    public static File directory(Context c){File dir=new File(c.getFilesDir(),"photos");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Photo storage is unavailable.");return dir;}
    public static File file(Context c,String name){if(name==null||!name.matches("[a-f0-9-]{36}\\.jpg"))throw new IllegalArgumentException("Invalid photo filename.");return new File(directory(c),name);}
    public static Photo importPhoto(Context c,Uri uri) throws IOException {
        ImageDecoder.Source source=ImageDecoder.createSource(c.getContentResolver(),uri);
        Bitmap bitmap=ImageDecoder.decodeBitmap(source,(decoder,info,src)->{
            int w=info.getSize().getWidth(),h=info.getSize().getHeight();if((long)w*h>100_000_000L)throw new IllegalArgumentException("This image is too large. Choose a smaller photo.");
            double scale=Math.min(1.0,2048.0/Math.max(w,h));decoder.setTargetSize(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
        Photo photo=new Photo();photo.id=LifeStore.id();photo.filename=photo.id+".jpg";photo.width=bitmap.getWidth();photo.height=bitmap.getHeight();File out=file(c,photo.filename);
        try(FileOutputStream stream=new FileOutputStream(out)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,84,stream))throw new IOException("Could not save the photo.");stream.getFD().sync();}catch(Exception e){out.delete();throw e;}finally{bitmap.recycle();}
        return photo;
    }
    public static String recognize(Context c,String filename,boolean hindi) throws Exception {
        TextRecognizer recognizer=hindi?TextRecognition.getClient(new DevanagariTextRecognizerOptions.Builder().build()):TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {InputImage image=InputImage.fromFilePath(c,Uri.fromFile(file(c,filename)));String text=Tasks.await(recognizer.process(image),30,TimeUnit.SECONDS).getText();return text;}finally{recognizer.close();}
    }
    public static Bitmap thumbnail(Context c,String name,int maximum){try{File f=file(c,name);return ImageDecoder.decodeBitmap(ImageDecoder.createSource(f),(d,i,s)->{double scale=Math.min(1.0,(double)maximum/Math.max(i.getSize().getWidth(),i.getSize().getHeight()));d.setTargetSize(Math.max(1,(int)(i.getSize().getWidth()*scale)),Math.max(1,(int)(i.getSize().getHeight()*scale)));d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});}catch(Exception e){return null;}}
    public static void attach(MemoryStore store,long note,Photo photo){if(!file(store.appContext,photo.filename).isFile())throw new IllegalArgumentException("This photo is no longer available.");store.getWritableDatabase().execSQL("INSERT INTO photos(id,note_id,filename,width,height,created) VALUES(?,?,?,?,?,?)",new Object[]{photo.id,note,photo.filename,photo.width,photo.height,System.currentTimeMillis()});}
    public static List<JSONObject> forNote(MemoryStore store,long id){return LifeStore.query(store.getReadableDatabase(),"SELECT * FROM photos WHERE note_id=?", ""+id);}
    public static void remove(MemoryStore store,String id){List<JSONObject> rows=LifeStore.query(store.getWritableDatabase(),"SELECT filename FROM photos WHERE id=?",id);store.getWritableDatabase().delete("photos","id=?",new String[]{id});for(JSONObject j:rows)file(store.appContext,j.optString("filename")).delete();}
    static JSONArray exportData(MemoryStore store){return new JSONArray(LifeStore.query(store.getReadableDatabase(),"SELECT * FROM photos"));}
    static void restoreData(MemoryStore store,JSONArray photos,Map<Long,Long> notes) throws JSONException {
        if(photos==null)return;for(int i=0;i<photos.length();i++){JSONObject p=photos.getJSONObject(i);Long note=notes.get(p.getLong("note_id"));if(note==null)continue;if(!p.getString("id").matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Invalid photo in backup.");
            if(!LifeStore.query(store.getReadableDatabase(),"SELECT id FROM photos WHERE id=?",p.getString("id")).isEmpty())continue;
            if(!file(store.appContext,p.getString("filename")).isFile())throw new IllegalArgumentException("Photo files are missing. Import the complete .zip backup.");ContentValues v=new ContentValues();v.put("id",p.getString("id"));v.put("note_id",note);v.put("filename",p.getString("filename"));v.put("width",p.getInt("width"));v.put("height",p.getInt("height"));v.put("created",p.getLong("created"));store.getWritableDatabase().insertOrThrow("photos",null,v);
        }
    }
}
