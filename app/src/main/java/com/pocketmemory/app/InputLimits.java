package com.pocketmemory.app;

import java.util.Locale;

/** Shared UI, storage and inference budgets. Count Unicode code points, never bytes as words. */
public final class InputLimits {
    private InputLimits(){}
    public static final int EDITOR_CEILING=250_000, TEXT_IMPORT_BYTES=256_000;
    public static final Rule PROMPT=new Rule("Question or message",500,4_000,true);
    public static final Rule NOTE=new Rule("Note",1_000,10_000,true);
    public static final Rule SUMMARY=new Rule("Text to summarize",1_000,10_000,true);
    public static final Rule REWRITE=new Rule("Text to edit",500,4_000,true);
    public static final Rule TITLE=new Rule("Title",0,100,false);
    public static final Rule CONTEXT=new Rule("Context",200,1_000,false);
    public static final Rule TASK=new Rule("Task",0,120,true);
    public static final Rule REMINDER=new Rule("Reminder title",0,120,true);
    public static final Rule DETAIL=new Rule("Reminder detail",200,1_000,false);
    public static final class Count {
        public final int words,characters;public final boolean invalid,visible;
        Count(int words,int characters,boolean invalid,boolean visible){this.words=words;this.characters=characters;this.invalid=invalid;this.visible=visible;}
    }
    static boolean separator(int c){return Character.isWhitespace(c)||Character.isSpaceChar(c)||Character.getType(c)==Character.FORMAT;}
    public static Count count(CharSequence value){
        if(value==null)return new Count(0,0,false,false);
        int words=0,characters=0;boolean inside=false,invalid=false,visible=false;
        for(int i=0;i<value.length();){int c=Character.codePointAt(value,i);i+=Character.charCount(c);characters++;
            if((c>=0xd800&&c<=0xdfff)||(Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))invalid=true;
            boolean space=separator(c);if(!space&&!inside)words++;inside=!space;if(!space)visible=true;
        }return new Count(words,characters,invalid,visible);
    }
    public static final class Rule {
        public final String label;public final int words,characters;final boolean required;
        Rule(String label,int words,int characters,boolean required){this.label=label;this.words=words;this.characters=characters;this.required=required;}
        public String error(CharSequence text){Count c=count(text);
            if(c.invalid)return label+" contains unsupported control characters or broken Unicode. Remove them before continuing.";
            if(required&&!c.visible)return "Add "+label.toLowerCase(Locale.ROOT)+" first.";
            if(c.characters>characters||(words>0&&c.words>words))return label+" allows "+(words>0?String.format(Locale.ROOT,"%,d words and ",words):"")+String.format(Locale.ROOT,"%,d characters",characters)+". Shorten or split the text; nothing has been saved or cut.";
            return null;
        }
        public void require(CharSequence value){String error=error(value);if(error!=null)throw new IllegalArgumentException(error);}
        public String counter(CharSequence value){Count c=count(value);return (words>0?String.format(Locale.ROOT,"%,d / %,d words · ",c.words,words):"")+String.format(Locale.ROOT,"%,d / %,d characters",c.characters,characters);}
    }
    public static void note(String title,String body,String context){TITLE.require(title);NOTE.require(body);CONTEXT.require(context);}
    public static Rule writing(String operation){
        if("Summarize".equals(operation))return SUMMARY;
        if(!WritingTools.supported(operation))throw new IllegalArgumentException("Choose a supported writing action.");
        return REWRITE;
    }
}
