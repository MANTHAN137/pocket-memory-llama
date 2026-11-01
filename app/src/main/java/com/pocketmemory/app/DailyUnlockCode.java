package com.pocketmemory.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/** A simple complimentary date-code gate, not a secure offline DRM mechanism. */
final class DailyUnlockCode {
    static final ZoneId ZONE=ZoneId.of("Asia/Kolkata");
    static boolean configured(int multiplier,int offset){int a=multiplier,b=1_000_000;while(b!=0){int remainder=a%b;a=b;b=remainder;}return multiplier>=1&&multiplier<=9999&&offset>=0&&offset<1_000_000&&a==1;}
    static String forDate(LocalDate date,int multiplier,int offset){
        if(!configured(multiplier,offset))throw new IllegalArgumentException("Unlock code is not configured.");
        int dateValue=date.getMonthValue()*100+date.getDayOfMonth();
        return String.format(Locale.ROOT,"%06d",Math.floorMod((long)dateValue*multiplier+offset,1_000_000));
    }
    static boolean matches(String entered,LocalDate date,int multiplier,int offset){
        if(entered==null||!entered.matches("[0-9]{6}")||!configured(multiplier,offset))return false;
        return MessageDigest.isEqual(entered.getBytes(StandardCharsets.US_ASCII),forDate(date,multiplier,offset).getBytes(StandardCharsets.US_ASCII));
    }
    private DailyUnlockCode(){}
}
