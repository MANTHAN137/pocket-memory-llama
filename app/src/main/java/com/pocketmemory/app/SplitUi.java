package com.pocketmemory.app;

import android.app.*;
import android.widget.*;
import java.util.*;

/** User-reviewed document conversion. The model neither chooses split boundaries nor writes facts. */
final class SplitUi {
    final MainActivity a;final MemoryApp app;
    SplitUi(MainActivity a){this.a=a;app=a.app;}
    Button option(LinearLayout parent,EditText body,java.util.function.Supplier<String> title,java.util.function.Supplier<String> context,long original,Dialog editor,java.util.function.LongConsumer attach,Runnable saved){
        return option(parent,body,title,context,original,()->editor,attach,saved);
    }
    Button option(LinearLayout parent,EditText body,java.util.function.Supplier<String> title,java.util.function.Supplier<String> context,long original,java.util.function.Supplier<Dialog> editor,java.util.function.LongConsumer attach,Runnable saved){
        Button split=a.button("🧩 Split into linked memories",false,()->open(original,title.get(),body.getText().toString(),context.get(),editor.get(),attach,saved));split.setId(R.id.split_memory);a.add(parent,split);java.util.function.Consumer<String> update=value->{InputLimits.Count count=InputLimits.count(value);split.setVisibility(count.words>InputLimits.NOTE.words||count.characters>InputLimits.NOTE.characters?android.view.View.VISIBLE:android.view.View.GONE);};update.accept(body.getText().toString());a.watch(body,update);return split;
    }
    void open(long original,String title,String text,String context,Dialog editor,java.util.function.LongConsumer attach,Runnable saved){
        if(app.busy||app.maintenance){a.toast("Finish or stop the current task first.");return;}
        List<String> parts;try{InputLimits.TITLE.require(title);InputLimits.CONTEXT.require(context);parts=NoteSplitter.split(text);if(parts.size()<2)throw new IllegalArgumentException("This text fits in one memory. Use Save memory.");}catch(Exception e){a.toast(e.getMessage());return;}
        LinearLayout form=a.column();form.setPadding(a.dp(18),a.dp(10),a.dp(18),a.dp(18));a.add(form,a.bold(parts.size()+" linked memories",21));a.gap(form,10);
        a.add(form,a.text("Each part stays within 1,000 words and 10,000 characters. Text stays in order, with no overlapping copies. All parts become searchable; open any part to browse the document. You can open each preview below to read it completely.",14,MainActivity.INK));
        if(original>0){a.gap(form,10);a.add(form,a.text("The existing memory will be archived with its complete wording and history. Its new parts will be used for current answers.",14,MainActivity.INK));}
        a.gap(form,14);
        for(int i=0;i<parts.size();i++){String part=parts.get(i),heading=NoteSplitter.title(title,i+1,parts.size());a.add(form,a.button(heading+"\n"+InputLimits.NOTE.counter(part),false,()->preview(heading,part)));a.gap(form,6);}
        a.gap(form,12);a.add(form,a.text("Splitting organizes the text; it does not verify its claims or mark earlier memories as corrected. Any differences still need your review.",12,MainActivity.MUTED));a.gap(form,12);
        AlertDialog[] dialog={null};Button save=a.button("Save all "+parts.size()+" parts",true,()->{
            if(app.busy||app.maintenance){a.toast("Finish the current task first.");return;}app.busy=true;app.workStatus="Saving linked memories…";app.changed();dialog[0].setCancelable(false);dialog[0].getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);setButtons(form,false);
            app.files.execute(()->{try{List<Long> ids=app.store.saveSplit(original,title,text,context,attach);app.busy=false;app.workStatus="";app.main.post(()->{if(saved!=null)saved.run();if(a.destroyed){app.worker.execute(app.engine::indexAll);return;}dialog[0].dismiss();if(editor!=null)editor.dismiss();a.go("Memory");a.toast("Saved "+ids.size()+" linked memories. Open any part to browse them.");app.worker.execute(app.engine::indexAll);});}catch(Exception e){app.busy=false;app.workStatus="";app.main.post(()->{a.toast(MainActivity.friendly(e));dialog[0].setCancelable(true);dialog[0].getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);setButtons(form,true);});}finally{app.changed();}});
        });save.setId(R.id.split_save);a.add(form,save);ScrollView scroll=new ScrollView(a);scroll.addView(form);dialog[0]=new ThemedDialog.Builder(a).setTitle("🧩 Split preview").setView(scroll).setNegativeButton("Keep editing",null).create();a.featureDialog=dialog[0];dialog[0].show();
    }
    void setButtons(android.view.View view,boolean enabled){if(view instanceof Button)view.setEnabled(enabled);if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)setButtons(group.getChildAt(i),enabled);}}
    void preview(String title,String text){TextView body=a.text(text,15,MainActivity.INK);body.setTextIsSelectable(true);body.setPadding(a.dp(18),a.dp(12),a.dp(18),a.dp(18));ScrollView scroll=new ScrollView(a);scroll.addView(body);new ThemedDialog.Builder(a).setTitle(title).setView(scroll).setPositiveButton("Back to parts",null).show();}
    void parts(long note){List<org.json.JSONObject> parts=app.store.documentParts(note);LinearLayout panel=a.column();panel.setPadding(a.dp(18),a.dp(12),a.dp(18),a.dp(18));for(org.json.JSONObject p:parts){a.add(panel,a.button((p.optInt("ordinal")==0?"Original (archived) · ":"")+p.optString("title")+(p.optString("status").equals("superseded")&&p.optInt("ordinal")>0?" · archived":""),false,()->a.viewNote(p.optLong("note_id"))));a.gap(panel,8);}ScrollView scroll=new ScrollView(a);scroll.addView(panel);new ThemedDialog.Builder(a).setTitle(parts.isEmpty()?"Document parts":parts.get(0).optString("source_title")).setView(scroll).setNegativeButton("Close",null).show();}
}
