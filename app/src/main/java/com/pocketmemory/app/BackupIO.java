package com.pocketmemory.app;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Stream photos separately from JSON so a backup does not expand every JPEG into base64 in RAM. */
public final class BackupIO {
    static final int MAX_JSON=50_000_000,MAX_IMAGE=6_000_000,MAX_TOTAL=250_000_000;
    public static void write(MemoryStore store,OutputStream output) throws Exception {
        JSONObject data=store.export();try(ZipOutputStream zip=new ZipOutputStream(output)){zip.putNextEntry(new ZipEntry("data.json"));zip.write(data.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();JSONArray photos=data.getJSONArray("photos");for(int i=0;i<photos.length();i++){String name=photos.getJSONObject(i).getString("filename");zip.putNextEntry(new ZipEntry("photos/"+name));try(InputStream in=new FileInputStream(PhotoStore.file(store.appContext,name))){in.transferTo(zip);}zip.closeEntry();}}
    }
    static byte[] limited(InputStream in,int max) throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[32768];int n;while((n=in.read(block))!=-1){if(out.size()+n>max)throw new IOException("This backup entry is too large.");out.write(block,0,n);}return out.toByteArray();}
    public static int restore(MemoryStore store,InputStream input) throws Exception {
        BufferedInputStream buffered=new BufferedInputStream(input);buffered.mark(4);int a=buffered.read(),b=buffered.read();buffered.reset();if(a!='P'||b!='K')return store.restore(new JSONObject(new String(limited(buffered,MAX_JSON),StandardCharsets.UTF_8)));
        File staging=new File(store.appContext.getCacheDir(),"backup-"+LifeStore.id());staging.mkdirs();List<File> created=new ArrayList<>();boolean committed=false;
        try{JSONObject data=null;Set<String> seen=new HashSet<>();long total=0;try(ZipInputStream zip=new ZipInputStream(buffered)){ZipEntry entry;int count=0;while((entry=zip.getNextEntry())!=null){if(++count>2000)throw new IOException("Too many files in this backup.");String name=entry.getName();if(!seen.add(name))throw new IOException("Duplicate backup entry.");if(name.equals("data.json")){byte[] json=limited(zip,MAX_JSON);total+=json.length;data=new JSONObject(new String(json,StandardCharsets.UTF_8));}else if(name.matches("photos/[a-f0-9-]{36}\\.jpg")){byte[] bytes=limited(zip,MAX_IMAGE);total+=bytes.length;File file=new File(staging,name.substring(7));try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(file.getPath(),options);if(options.outWidth<1||options.outHeight<1||options.outWidth>4096||options.outHeight>4096)throw new IOException("Invalid backup photo.");}else throw new IOException("Unknown file in backup.");if(total>MAX_TOTAL)throw new IOException("Split backups larger than 250 MB into smaller collections.");zip.closeEntry();}}
            if(data==null)throw new IOException("Backup has no data.json.");JSONArray photos=data.optJSONArray("photos");if(photos!=null)for(int i=0;i<photos.length();i++){JSONObject photo=photos.getJSONObject(i);String original=photo.getString("filename");File source=new File(staging,PhotoStore.file(store.appContext,original).getName());if(!source.isFile())throw new IOException("A backup photo is missing.");String fresh=LifeStore.id()+".jpg";File target=PhotoStore.file(store.appContext,fresh);Files.move(source.toPath(),target.toPath());created.add(target);photo.put("filename",fresh);}
            int count=store.restore(data);committed=true;return count;
        }finally{for(File file:created)if(!committed||LifeStore.query(store.getReadableDatabase(),"SELECT id FROM photos WHERE filename=?",file.getName()).isEmpty())file.delete();File[] files=staging.listFiles();if(files!=null)for(File file:files)file.delete();staging.delete();}
    }
}
