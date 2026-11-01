package com.pocketmemory.app;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Gives only the selected camera app temporary access to a single capture file. */
public final class PhotoProvider extends ContentProvider {
    public static Uri uri(Context c,String name){return new Uri.Builder().scheme("content").authority(c.getPackageName()+".camera").appendPath(name).build();}
    public static File capture(Context c,String name){if(name==null||!name.matches("[a-f0-9-]{36}\\.jpg"))throw new IllegalArgumentException("Invalid capture.");File dir=new File(c.getCacheDir(),"camera");dir.mkdirs();return new File(dir,name);}
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return "image/jpeg";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {if(uri.getPathSegments().size()!=1)throw new FileNotFoundException();File file=capture(getContext(),uri.getLastPathSegment());return ParcelFileDescriptor.open(file,ParcelFileDescriptor.parseMode(mode));}
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
