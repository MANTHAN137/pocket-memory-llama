package com.pocketmemory.app;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Bounded-memory, versioned authenticated encryption around the existing portable ZIP backup. */
public final class EncryptedBackup {
    private static final byte[] MAGIC="PMBACK02".getBytes(StandardCharsets.US_ASCII);
    private static final int ROUNDS=210000,CHUNK=65536,MAX_BYTES=300_000_000;
    static boolean encrypted(InputStream in)throws IOException{return Arrays.equals(in.readNBytes(MAGIC.length),MAGIC);}
    private static SecretKey key(char[] password,byte[] salt)throws Exception{
        PBEKeySpec spec=new PBEKeySpec(password,salt,ROUNDS,256);
        try{byte[] derived=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();try{return new SecretKeySpec(derived,"AES");}finally{Arrays.fill(derived,(byte)0);}}finally{spec.clearPassword();}
    }
    private static void password(char[] value)throws IOException{if(value==null||value.length<12||value.length>256)throw new IOException("Use a password of 12–256 characters. It cannot be recovered if lost.");}
    private static Cipher cipher(int mode,SecretKey key,byte[] header,int number,int length)throws Exception{
        byte[] iv=ByteBuffer.allocate(12).put(header,24,8).putInt(number).array();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,key,new GCMParameterSpec(128,iv));cipher.updateAAD(header);cipher.updateAAD(ByteBuffer.allocate(8).putInt(number).putInt(length).array());return cipher;
    }
    public static void write(MemoryStore store,OutputStream output,char[] password)throws Exception{
        password(password);byte[] header=new byte[32];new SecureRandom().nextBytes(header);System.arraycopy(MAGIC,0,header,0,8);SecretKey key=key(password,Arrays.copyOfRange(header,8,24));output.write(header);
        try(ChunkOutput secure=new ChunkOutput(output,key,header)){BackupIO.write(store,secure);}
    }
    private static final class ChunkOutput extends OutputStream {
        final DataOutputStream out;final SecretKey key;final byte[] header,buffer=new byte[CHUNK];int size=0,number=0,total=0;boolean closed;
        ChunkOutput(OutputStream output,SecretKey key,byte[] header){out=new DataOutputStream(output);this.key=key;this.header=header;}
        void record(int length)throws IOException{try{out.writeInt(length);out.write(cipher(Cipher.ENCRYPT_MODE,key,header,number++,length).doFinal(buffer,0,length));}catch(IOException e){throw e;}catch(Exception e){throw new IOException("Could not encrypt the backup.",e);}}
        @Override public void write(int b)throws IOException{byte[] one={(byte)b};write(one,0,1);}
        @Override public void write(byte[] data,int offset,int length)throws IOException{
            if(closed)throw new IOException("Backup stream is closed.");if((long)total+length>MAX_BYTES)throw new IOException("Split backups larger than 300 MB into smaller collections.");total+=length;
            while(length>0){int count=Math.min(length,CHUNK-size);System.arraycopy(data,offset,buffer,size,count);size+=count;offset+=count;length-=count;if(size==CHUNK){record(size);size=0;}}
        }
        @Override public void close()throws IOException{if(closed)return;closed=true;try{if(size>0)record(size);record(0);out.flush();}finally{Arrays.fill(buffer,(byte)0);out.close();}}
    }
    public static int restore(MemoryStore store,InputStream input,char[] password)throws Exception{
        password(password);byte[] header=input.readNBytes(32);if(header.length!=32||!Arrays.equals(Arrays.copyOf(header,8),MAGIC))throw new IOException("This is not a supported encrypted backup.");SecretKey key=key(password,Arrays.copyOfRange(header,8,24));
        File temp=File.createTempFile("backup-auth-",".zip",store.appContext.getCacheDir());
        try{
            // Verify every frame and the authenticated terminal marker before any import mutates the database.
            try(OutputStream out=new FileOutputStream(temp)){
                DataInputStream data=new DataInputStream(input);int total=0,number=0;
                while(true){int length;try{length=data.readInt();}catch(EOFException e){throw new IOException("This backup is incomplete. No data was imported.");}if(length<0||length>CHUNK||(long)total+length>MAX_BYTES)throw new IOException("Invalid backup frame or import limit exceeded.");byte[] encrypted=data.readNBytes(length+16);if(encrypted.length!=length+16)throw new IOException("This backup is incomplete.");byte[] plain=cipher(Cipher.DECRYPT_MODE,key,header,number++,length).doFinal(encrypted);if(length==0){if(data.read()!=-1)throw new IOException("Unexpected data after the encrypted backup.");break;}out.write(plain);total+=plain.length;Arrays.fill(plain,(byte)0);}
            }catch(AEADBadTagException e){throw new IOException("The password is incorrect or the backup was changed. No data was imported.");}
            try(InputStream verified=new FileInputStream(temp)){return BackupIO.restore(store,verified);}
        }finally{Files.deleteIfExists(temp.toPath());}
    }
    private EncryptedBackup(){}
}
