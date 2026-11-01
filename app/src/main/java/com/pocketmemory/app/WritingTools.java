package com.pocketmemory.app;

import java.text.BreakIterator;
import java.util.*;
import java.util.regex.*;

/** Small, explicit local writing tasks. No website/API connection or autonomous tool loop. */
final class WritingTools {
    static final String[] EXTRA={"Simplify","Make professional","Make friendly","Bullet points","Draft email"};
    static boolean supported(String operation){return Set.of("Enhance text","Fix grammar","Make concise","Simplify","Make professional","Make friendly","Bullet points","Draft email").contains(operation);}
    static String instruction(String operation){
        switch(operation){
            case "Fix grammar":return "Correct spelling, punctuation and grammar only. Preserve the original meaning and tone.";
            case "Make concise":return "Remove wordiness while preserving all facts and important details.";
            case "Simplify":return "Use familiar words and shorter sentences. Preserve all facts; do not remove qualifications.";
            case "Make professional":return "Use clear, professional and respectful language. Do not add commitments or promises.";
            case "Make friendly":return "Use warm, natural and respectful language. Do not invent relationships or promises.";
            case "Bullet points":return "Turn the draft into short bullet points starting with '- '. Retain the supplied facts and action items. Do not invent tasks, owners or deadlines. Do not number the bullets.";
            case "Draft email":return "Turn these notes into a concise email with a short Subject: line and body. Do not invent recipients, sender names, dates, deadlines, commitments or contact details. Use a neutral greeting if no recipient is supplied.";
            case "Enhance text":return "Improve clarity, flow and wording without changing the meaning or adding facts.";
            default:throw new IllegalArgumentException("Choose a supported writing action.");
        }
    }
    static final Pattern NUMBER=Pattern.compile("[\\p{L}]*\\d[\\p{L}\\d.,:/-]*");
    static boolean numbersPreserved(String source,String result){
        Set<String> before=new HashSet<>(),after=new HashSet<>();Matcher a=NUMBER.matcher(source),b=NUMBER.matcher(result);
        while(a.find())before.add(a.group().replaceAll("[.,:/-]+$",""));while(b.find())after.add(b.group().replaceAll("[.,:/-]+$",""));return before.equals(after);
    }
    static final class Stats {
        final int words,characters,sentences,longSentences,readingSeconds;
        final List<String> examples=new ArrayList<>();
        Stats(String text){
            InputLimits.Count count=InputLimits.count(text);words=count.words;characters=count.characters;readingSeconds=(int)Math.ceil(words*60.0/200);
            BreakIterator iterator=BreakIterator.getSentenceInstance(Locale.ENGLISH);iterator.setText(text);int start=iterator.first(),total=0,longCount=0;
            for(int end=iterator.next();end!=BreakIterator.DONE;start=end,end=iterator.next()){
                String sentence=text.substring(start,end).trim();if(sentence.isEmpty())continue;total++;
                if(InputLimits.count(sentence).words>25){longCount++;if(examples.size()<3)examples.add(sentence.length()>350?sentence.substring(0,350)+"…":sentence);}
            }sentences=total;longSentences=longCount;
        }
    }
    private WritingTools(){}
}
