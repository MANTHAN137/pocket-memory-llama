package com.pocketmemory.app;

import java.time.*;

/** Wall-clock recurrence; intervals are anchored to the user's daytime window, never a 24h timer. */
public final class AlarmSchedule {
    private AlarmSchedule(){}
    public static long next(String repeat,int minute,int interval,int start,int end,long after,ZoneId zone){
        ZonedDateTime clock=Instant.ofEpochMilli(after).atZone(zone);LocalDate date=clock.toLocalDate();
        if(repeat.equals("daily")){
            for(int day=0;day<3;day++){long candidate=date.plusDays(day).atTime(minute/60,minute%60).atZone(zone).toInstant().toEpochMilli();if(candidate>after)return candidate;}
        }else if(repeat.equals("interval")){
            if(interval<15||interval>720||start<0||end>1439||start>=end)throw new IllegalArgumentException("Choose a daytime window and an interval of 15 minutes or more.");
            for(int day=0;day<3;day++)for(int at=start;at<=end;at+=interval){long candidate=date.plusDays(day).atTime(at/60,at%60).atZone(zone).toInstant().toEpochMilli();if(candidate>after)return candidate;}
        }
        throw new IllegalArgumentException("This reminder has no repeating schedule.");
    }
}
