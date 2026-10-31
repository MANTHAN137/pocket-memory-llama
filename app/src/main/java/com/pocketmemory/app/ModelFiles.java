package com.pocketmemory.app;

import android.content.Context;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

/** One pinned Meta file. Import is explicit, cancellable and checksum verified. */
final class ModelFiles {
    final Context context;final JSONObject model;final File file,marker;
    ModelFiles(Context c){context=c;try(InputStream in=c.getAssets().open("models.json")){model=new JSONArray(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getJSONObject(0);File dir=new File(c.getFilesDir(),"models");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Could not create model folder");file=new File(dir,model.getString("id")+"-"+model.getString("filename"));marker=new File(file+".verified");}catch(Exception e){throw new IllegalStateException(e);}}
    String name(){return model.optString("name");}
    String url(){return model.optString("url");}
    boolean accepted(){return context.getSharedPreferences("llama_terms",0).getBoolean(model.optString("sha256"),false);}
    void accept(){context.getSharedPreferences("llama_terms",0).edit().putBoolean(model.optString("sha256"),true).apply();}
    boolean installed(){try{return file.length()==model.getLong("bytes")&&marker.length()==64&&new String(Files.readAllBytes(marker.toPath()),StandardCharsets.UTF_8).equals(model.getString("sha256"));}catch(Exception e){return false;}}
    interface Progress {void update(long done,long total);}
    void importFile(Uri uri,AtomicBoolean cancel,Progress progress)throws Exception{
        if(!accepted())throw new IOException("Review the Llama terms first.");
        long size=model.getLong("bytes");if(new android.os.StatFs(file.getParent()).getAvailableBytes()<size+100_000_000L)throw new IOException("Free at least 2.2 GB inside app storage before importing.");
        File part=new File(file+".import");MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(InputStream in=context.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(part)){
            if(in==null)throw new IOException("The selected file could not be opened.");byte[] buffer=new byte[131072];int count;long done=0,last=0;
            while((count=in.read(buffer))!=-1){if(cancel.get())throw new IOException("Import stopped.");done+=count;if(done>size)throw new IOException("This is not the pinned Llama file.");hash.update(buffer,0,count);out.write(buffer,0,count);if(done-last>16_000_000){progress.update(done,size);last=done;}}
            out.getFD().sync();StringBuilder digest=new StringBuilder();for(byte b:hash.digest())digest.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(done!=size||!digest.toString().equals(model.getString("sha256")))throw new IOException("The model size or checksum did not match. Download the pinned file and try again.");
        }catch(Exception e){part.delete();throw e;}
        Files.move(part.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);Files.write(marker.toPath(),model.getString("sha256").getBytes(StandardCharsets.UTF_8));
        for(String name:new String[]{"llama-3.2.txt","llama-3.2-use-policy.txt"})try(InputStream in=context.getAssets().open("licenses/"+name)){Files.copy(in,new File(file.getParentFile(),name).toPath(),StandardCopyOption.REPLACE_EXISTING);}
        progress.update(size,size);
    }
}
