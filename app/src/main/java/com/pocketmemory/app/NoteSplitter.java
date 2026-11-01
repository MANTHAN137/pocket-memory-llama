package com.pocketmemory.app;

import java.util.*;

/** Deterministic local splitting; boundaries are measured again and no characters are discarded. */
public final class NoteSplitter {
    private NoteSplitter(){}
    public static final int MAX_PARTS=128;
    public static List<String> split(String text){
        if(text==null||text.length()>InputLimits.EDITOR_CEILING)throw new IllegalArgumentException("Split the source file into sections under 250,000 characters before importing.");
        InputLimits.Count whole=InputLimits.count(text);if(whole.invalid||!whole.visible)throw new IllegalArgumentException("Remove unsupported characters or add visible text before splitting.");
        List<String> parts=new ArrayList<>();int start=0;
        while(start<text.length()){
            int end=start,chars=0,words=0,spaceBreak=-1,paragraph=-1;boolean inside=false;
            while(end<text.length()){
                int c=Character.codePointAt(text,end);boolean space=InputLimits.separator(c);int nextWords=words+(!space&&!inside?1:0);
                if(chars+1>InputLimits.NOTE.characters||nextWords>InputLimits.NOTE.words)break;
                end+=Character.charCount(c);chars++;words=nextWords;inside=!space;
                if(space)spaceBreak=end;if(c=='\n'&&end>start+1&&text.charAt(end-2)=='\n')paragraph=end;
            }
            if(end<text.length()){
                if(paragraph>start+(end-start)/2)end=paragraph;
                else if(spaceBreak>start+(end-start)/2)end=spaceBreak;
            }
            if(end<=start)throw new IllegalArgumentException("This text cannot be split safely. Choose a smaller section.");
            String part=text.substring(start,end);if(!InputLimits.count(part).visible)throw new IllegalArgumentException("This text contains an oversized block of empty space. Remove excess blank space before splitting.");
            InputLimits.NOTE.require(part);parts.add(part);if(parts.size()>MAX_PARTS)throw new IllegalArgumentException("One document can have up to 128 parts. Import smaller sections.");start=end;
        }
        return Collections.unmodifiableList(parts);
    }
    public static String title(String source,int part,int total){
        String prefix="Part "+part+" of "+total+" · ";int remaining=InputLimits.TITLE.characters-prefix.codePointCount(0,prefix.length());
        String tail=source.isBlank()?"Untitled memory":source;
        if(tail.codePointCount(0,tail.length())>remaining)tail=tail.substring(0,tail.offsetByCodePoints(0,remaining-1))+"…";
        return prefix+tail;
    }
}
