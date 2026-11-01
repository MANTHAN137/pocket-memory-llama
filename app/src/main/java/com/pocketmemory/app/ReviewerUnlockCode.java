package com.pocketmemory.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Durable complimentary review access; contains only a digest, never the publisher's code. */
final class ReviewerUnlockCode {
    static boolean configured(String digest){return digest!=null&&digest.matches("[0-9a-f]{64}");}
    static boolean matches(String entered,String digest){
        if(entered==null||!entered.matches("[0-9]{12}")||!configured(digest))return false;
        byte[] expected=new byte[32];for(int i=0;i<expected.length;i++)expected[i]=(byte)Integer.parseInt(digest.substring(i*2,i*2+2),16);
        try{return MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(entered.getBytes(StandardCharsets.US_ASCII)),expected);}
        catch(java.security.NoSuchAlgorithmException impossible){return false;}
    }
    private ReviewerUnlockCode(){}
}
