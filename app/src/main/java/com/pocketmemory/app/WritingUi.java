package com.pocketmemory.app;

import android.app.AlertDialog;
import android.view.View;
import android.widget.*;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

/** Two everyday actions; details and less frequent tools stay in contextual menus. */
final class WritingUi {
    final MainActivity a;final MemoryApp app;
    LinearLayout result;TextView count,resultTitle;Button enhance,summarize,longText,report;
    WritingUi(MainActivity a){this.a=a;app=a.app;}
    void render(){
        LinearLayout heading=a.row();TextView title=a.bold("Writing",25);title.setAccessibilityHeading(true);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button more=a.quietAction("•••","Writing options",this::options);more.setId(R.id.writing_more);heading.addView(more);a.add(a.content,heading);a.gap(a.content,12);
        a.writing=a.input("Paste your text…",true);a.writing.setId(R.id.writing_input);a.writing.setMinLines(5);a.writing.setMaxLines(10);a.writing.setText(app.writingInput);a.add(a.content,a.writing);
        count=a.text("",11,MainActivity.MUTED);count.setId(R.id.writing_count);count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);a.add(a.content,count);
        longText=a.button("🧩 Split into memories",false,this::saveInput);longText.setId(R.id.writing_split);a.add(a.content,longText);a.watch(a.writing,value->{app.updateWritingDraft(value);updateCount(value);});updateCount(app.writingInput);a.gap(a.content,14);
        enhance=a.button("✨ Enhance",true,()->{if(app.writingRunning)app.engine.cancel();else a.write("Enhance text");});enhance.setId(R.id.writing_enhance);
        summarize=a.button("Summarize",false,()->a.write("Summarize"));summarize.setId(R.id.writing_summarize);a.paired(a.content,enhance,summarize);a.gap(a.content,18);
        result=a.card();result.setId(R.id.writing_result_card);LinearLayout tools=a.row();resultTitle=a.bold("Result",16);tools.addView(resultTitle,new LinearLayout.LayoutParams(0,-2,1));
        Button copy=a.quietAction("Copy","Copy result",()->a.copy(app.writingOutput));copy.setTextSize(12);tools.addView(copy);
        report=new ReportUi(a).flag(()->app.writingOutput,()->app.writingPrompt);report.setId(R.id.writing_report);tools.addView(report);
        tools.addView(a.quietAction("•••","Result options",this::resultOptions));a.add(result,tools);a.gap(result,8);
        a.writingResult=a.text("",16,MainActivity.INK);a.writingResult.setId(R.id.writing_output);a.writingResult.setTextIsSelectable(true);a.add(result,a.writingResult);a.add(a.content,result);refresh();
    }
    void updateCount(String text){
        InputLimits.Count c=InputLimits.count(text);boolean over=c.words>InputLimits.NOTE.words||c.characters>InputLimits.NOTE.characters;
        String value=String.format(Locale.ROOT,"%,d words",c.words);
        if(c.invalid)value="Remove unsupported characters.";
        else if(over)value+=" · Split or shorten";
        else if(c.words>InputLimits.REWRITE.words||c.characters>InputLimits.REWRITE.characters)value+=" · Summarize or save to Memory";
        count.setText(value);count.setVisibility(text.isEmpty()?View.GONE:View.VISIBLE);count.setTextColor(c.invalid||over?0xffa92043:MainActivity.MUTED);
        longText.setVisibility(over?View.VISIBLE:View.GONE);
    }
    void refresh(){if(result==null)return;
        boolean running=app.writingRunning;enhance.setText(running?"Stop":"✨ Enhance");enhance.setContentDescription(running?"Stop writing task":"Enhance text");enhance.setEnabled(running||!app.busy);summarize.setEnabled(!app.busy);summarize.setVisibility(running?View.GONE:View.VISIBLE);
        result.setVisibility(app.writingOutput.isBlank()?View.GONE:View.VISIBLE);if(!a.writingResult.getText().toString().equals(app.writingOutput))a.writingResult.setText(app.writingOutput);resultTitle.setText(app.writingFailed?"Couldn’t finish":"Result");report.setVisibility(app.writingFailed?View.GONE:View.VISIBLE);
    }
    void saveInput(){if(a.writing.getText().toString().isBlank()){a.toast("Add some text first.");return;}a.edit(null,"Writing document",a.writing.getText().toString());}
    void options(){
        List<String> names=new ArrayList<>(List.of("Fix grammar","Make concise","Rewrite & format","Writing stats","Save text to Memory","Input limits"));if(app.writingUndoAvailable)names.add("Undo draft replacement");
        AlertDialog dialog=new ThemedDialog.Builder(a).setTitle("Writing options").setCustomTitle(Y2k.dialogTitle(a,"✍️","Writing options",Y2k.PINK)).setItems(names.toArray(new String[0]),(d,i)->{if(i==0)a.write("Fix grammar");if(i==1)a.write("Make concise");if(i==2)rewrites();if(i==3)stats();if(i==4)saveInput();if(i==5)limits();if(i==6)undo();}).setNegativeButton("Close",null).create();a.featureDialog=dialog;dialog.setOnShowListener(d->Y2k.dialogActions(dialog));dialog.show();
    }
    void rewrites(){AlertDialog dialog=new ThemedDialog.Builder(a).setTitle("Rewrite & format").setCustomTitle(Y2k.dialogTitle(a,"✨","Rewrite & format",Y2k.LILAC)).setItems(WritingTools.EXTRA,(d,i)->a.write(WritingTools.EXTRA[i])).setNegativeButton("Close",null).create();a.featureDialog=dialog;dialog.setOnShowListener(d->Y2k.dialogActions(dialog));dialog.show();}
    void sheet(String title,String icon,LinearLayout body){
        body.setPadding(a.dp(20),a.dp(8),a.dp(20),a.dp(12));ScrollView scroll=new ScrollView(a);scroll.addView(body);AlertDialog dialog=new ThemedDialog.Builder(a).setTitle(title).setCustomTitle(Y2k.dialogTitle(a,icon,title,Y2k.AQUA)).setView(scroll).setNegativeButton("Close",null).create();a.featureDialog=dialog;dialog.setOnShowListener(d->Y2k.dialogActions(dialog));dialog.show();
    }
    void stats(){
        WritingTools.Stats stats=new WritingTools.Stats(a.writing.getText().toString());LinearLayout body=a.column();
        a.add(body,a.bold(String.format(Locale.ROOT,"%,d words · %,d characters",stats.words,stats.characters),17));a.gap(body,10);
        a.add(body,a.text(stats.sentences+" sentences · About "+(stats.readingSeconds<60?stats.readingSeconds+" sec":((stats.readingSeconds+59)/60)+" min")+" to read",14,MainActivity.INK));a.gap(body,12);
        a.add(body,a.text(stats.longSentences+" sentences over 25 words",14,MainActivity.INK));
        for(String example:stats.examples){a.gap(body,10);a.add(body,a.text("• "+example,13,MainActivity.MUTED));}
        a.gap(body,14);a.add(body,a.text("Reading time assumes 200 words/minute. Sentence-length hints work best in English; they are not a grammar or accuracy check. No model is needed.",12,MainActivity.MUTED));sheet("Writing stats","📊",body);
    }
    void compare(){
        LinearLayout body=a.column();a.add(body,a.bold("Original",16));a.gap(body,8);TextView original=a.text(app.writingPrompt,15,MainActivity.INK);original.setTextIsSelectable(true);a.add(body,original);a.gap(body,20);a.add(body,a.bold("Result",16));a.gap(body,8);TextView rewritten=a.text(app.writingOutput,15,MainActivity.INK);rewritten.setTextIsSelectable(true);a.add(body,rewritten);sheet("Compare drafts","↔️",body);
    }
    void useResult(){
        if(app.writingRunning||app.writingFailed||app.writingOutput.isBlank()){a.toast("Finish a writing result first.");return;}
        String result=app.writingOutput;AlertDialog dialog=new ThemedDialog.Builder(a).setTitle("Use result as your draft?").setMessage("This replaces the current editor text, including any newer edits. You can undo this replacement from Writing options during this app session.").setPositiveButton("Use as draft",(d,i)->{app.writingUndo=a.writing.getText().toString();app.writingUndoAvailable=true;a.writing.setText(result);app.persistWritingDraft();a.toast("Draft updated. Undo is in Writing options.");}).setNegativeButton("Keep current",null).create();a.featureDialog=dialog;dialog.setOnShowListener(d->Y2k.dialogActions(dialog));dialog.show();
    }
    void undo(){
        if(!app.writingUndoAvailable)return;AlertDialog dialog=new ThemedDialog.Builder(a).setTitle("Restore previous draft?").setMessage("This replaces your current editor text with the draft saved before the last replacement.").setPositiveButton("Restore draft",(d,i)->{a.writing.setText(app.writingUndo);app.writingUndo="";app.writingUndoAvailable=false;app.persistWritingDraft();}).setNegativeButton("Keep current",null).create();a.featureDialog=dialog;dialog.setOnShowListener(d->Y2k.dialogActions(dialog));dialog.show();
    }
    void limits(){new ThemedDialog.Builder(a).setTitle("Input limits").setMessage("Edit: 500 words · 4,000 characters\nSummarize: 1,000 words · 10,000 characters\n\nLonger text can be saved as linked memories. Some languages may need a shorter passage.").setPositiveButton("Got it",null).show();}
    void resultOptions(){AlertDialog dialog=new ThemedDialog.Builder(a).setTitle("Result options").setItems(new String[]{"Use as draft","Compare with original","Save as memory","Read aloud"},(d,i)->{if(i==0)useResult();if(i==1)compare();if(i==2)a.edit(null,"Writing result",app.writingOutput);if(i==3)a.speak(app.writingOutput);}).setNegativeButton("Close",null).create();a.featureDialog=dialog;dialog.setOnShowListener(d->Y2k.dialogActions(dialog));dialog.show();}
}
