package com.pocketmemory.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Catalog {
    public static final class Model {
        public final String id, name, repo, revision, filename, kind, group, description, license, sha256, url;
        public final long bytes;
        public final boolean premium;
        public final String productId, parameters, tradeoff, licenseAsset, unavailableReason, upstream, quantization;
        public final int minimumRamGb,recommendedRamGb;
        private Context displayOwner;private long displayAt=-1;private boolean displayInstalled;
        Model(JSONObject j) throws Exception {
            id=j.getString("id"); name=j.getString("name"); repo=j.getString("repo"); revision=j.getString("revision");
            filename=j.getString("filename"); kind=j.getString("kind"); group=j.getString("group");
            description=j.getString("description"); license=j.getString("license"); sha256=j.getString("sha256");
            url=j.getString("url"); bytes=j.getLong("bytes");
            premium=j.optBoolean("premium",false);productId=j.optString("productId");parameters=j.optString("parameters");tradeoff=j.optString("tradeoff");
            licenseAsset=j.optString("licenseAsset");unavailableReason=j.optString("unavailableReason");upstream=j.optString("upstream",repo);quantization=j.optString("quantization");minimumRamGb=j.getInt("minimumRamGb");recommendedRamGb=j.getInt("recommendedRamGb");
            if(minimumRamGb<1||recommendedRamGb<minimumRamGb||recommendedRamGb>64)throw new IOException("Invalid model RAM guidance.");
            if (!id.matches("[a-z0-9.-]+") || !filename.matches("[A-Za-z0-9_.-]+") || !sha256.matches("[a-f0-9]{64}") || bytes<=0)
                throw new IOException("Invalid model catalog entry.");
            if(premium&&(!productId.matches("[a-z][a-z0-9_]{1,99}")||!kind.equals("chat")))throw new IOException("Invalid model product.");
            if(!licenseAsset.isEmpty()&&!licenseAsset.matches("[a-z0-9.-]+\\.txt"))throw new IOException("Invalid model licence asset.");
        }
        public boolean available(){return unavailableReason.isEmpty();}
        public File file(Context c) { return new File(directory(c), id+"-"+filename); }
        public File part(Context c) { return new File(file(c).getPath()+".part"); }
        public File marker(Context c) { return new File(file(c).getPath()+".verified"); }
        public boolean installed(Context c) {
            try{File model=file(c),marker=new File(model.getPath()+".verified");return model.length()==bytes&&marker.length()==64&&new String(Files.readAllBytes(marker.toPath()),StandardCharsets.UTF_8).equals(sha256);}catch(Exception ignored){return false;}
        }
        /** Labels only. Download/import/inference decisions must use installed(), never this cache. */
        public synchronized boolean installedForDisplay(Context c){
            Context owner=c.getApplicationContext();long now=android.os.SystemClock.elapsedRealtime();
            if(owner==displayOwner&&displayAt>=0&&now-displayAt<250)return displayInstalled;
            displayOwner=owner;displayAt=now;displayInstalled=installed(c);return displayInstalled;
        }
        public synchronized void invalidateDisplay(){displayAt=-1;}
        public String size() { return bytes >= 1_000_000_000L ? String.format(Locale.US,"%.2f GB",bytes/1e9) : String.format(Locale.US,"%.0f MB",bytes/1e6); }
        public String ramGuide(){return "Phone RAM · "+recommendedRamGb+" GB+ recommended";}
        public String deviceDetails(){return "Download: "+size()+" · "+parameters+" · "+quantization+"\nPhone RAM guide: "+minimumRamGb+" GB minimum; "+recommendedRamGb+" GB recommended.\nApp estimates for this file and context limit. Memory needs and reply speed vary by chip, background apps and heat. Download size is storage, not RAM.";}
    }
    public final List<Model> models = new ArrayList<>();
    public Catalog(Context c) {
        try (var in=c.getAssets().open("models.json")) {
            JSONArray j=new JSONArray(new String(in.readAllBytes(),StandardCharsets.UTF_8));
            for(int i=0;i<j.length();i++) models.add(new Model(j.getJSONObject(i)));
        } catch(Exception e) { throw new IllegalStateException("The bundled model catalog is missing.",e); }
    }
    public Model get(String id) { for(Model m:models) if(m.id.equals(id)) return m; return null; }
    public static File directory(Context c) { File f=new File(c.getFilesDir(),"models"); if(!f.isDirectory()&&!f.mkdirs()) throw new IllegalStateException("Cannot create model folder."); return f; }
}
