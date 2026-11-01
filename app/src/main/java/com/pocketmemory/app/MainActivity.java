package com.pocketmemory.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.util.*;

/** Native Android views, local inference, bundled OCR and an on-device organizer. */
public final class MainActivity extends Activity {
    static final int BG=Y2k.BG,INK=Y2k.INK,MUTED=0xff57505f,BLUE=Y2k.INK,CORAL=Y2k.PINK,DEEP_INK=Y2k.INK,LINE=Y2k.INK,PAPER=Y2k.PAPER,MIST=0xffe7dcf3;
    static final int IMPORT_TEXT=31,IMPORT_MODEL=32,EXPORT=33,RESTORE=34,MIC=35,PICK_PHOTO=36,CAPTURE_PHOTO=37,NOTIFICATIONS=38;
    MemoryApp app;
    LinearLayout root,content,sourceList,modelCards;
    TextView status,answerView,writingResult,memoryCount,indexCount;
    EditText question,writing;
    ScrollView scroll;
    String screen="Today",modelFilter="Free",modelSearch="",importModel="",recordTarget="",voiceLanguage="auto";
    final Map<String,TextView> modelProgress=new HashMap<>();
    final Map<String,Button> modelActions=new HashMap<>();
    final Map<String,ProgressBar> modelBars=new HashMap<>();
    final Map<String,Integer> tabScrollPositions=new HashMap<>();
    String modelFingerprint="",sourceFingerprint="";
    boolean destroyed=false,ttsReady=false;
    OfflineVoice voice;
    TextToSpeech tts;
    Dialog noteDialog,featureDialog;
    TodayUi todayUi;ChatUi chatUi;PhotoUi photoUi;WritingUi writingUi;Button askStop;LinearLayout answerCard;Button answerReport;
    final Runnable observer=()->refresh();long seenTaskChanges;
    final BroadcastReceiver dayAndReminders=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){if(TaskAssist.CHANGED.equals(intent.getAction())&&seenTaskChanges==app.taskChanges.get())return;if(screen.equals("Today")&&!destroyed){int position=scroll.getScrollY();render();scroll.post(()->scroll.scrollTo(0,position));}}};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);app=(MemoryApp)getApplication();todayUi=new TodayUi(this);chatUi=new ChatUi(this);photoUi=new PhotoUi(this);writingUi=new WritingUi(this);
        if(state!=null){screen=state.getString("screen","Today");importModel=state.getString("importModel","");modelFilter=state.getString("modelFilter","Free");if(modelFilter.equals("Recommended"))modelFilter="Free";Bundle positions=state.getBundle("tabScrollPositions");if(positions!=null)for(String key:positions.keySet())tabScrollPositions.put(key,positions.getInt(key));}
        else if(getIntent().hasExtra("screen"))screen=getIntent().getStringExtra("screen");
        if(getIntent().hasExtra("todaySection")){screen="Today";todayUi.section="Tasks";}
        voice=new OfflineVoice();
        tts=new TextToSpeech(this,result->{ttsReady=result==TextToSpeech.SUCCESS;});
        app.listeners.add(observer);render();if(state!=null)restoreTabPosition(screen);if(PrivacyUi.accepted(this))handleSharedText();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.hasExtra("todaySection")){todayUi.section="Tasks";go("Today");}else if(Intent.ACTION_SEND.equals(intent.getAction())&&PrivacyUi.accepted(this))handleSharedText();}
    void handleSharedText(){if(Intent.ACTION_SEND.equals(getIntent().getAction())){String text=getIntent().getStringExtra(Intent.EXTRA_TEXT);getIntent().removeExtra(Intent.EXTRA_TEXT);if(text!=null)edit(null,"Shared text",text);}}
    @Override protected void onStart(){super.onStart();app.visibleActivities++;IntentFilter changes=new IntentFilter();for(String action:new String[]{Intent.ACTION_DATE_CHANGED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,Reminders.CHANGED,TaskAssist.CHANGED})changes.addAction(action);registerReceiver(dayAndReminders,changes,Context.RECEIVER_NOT_EXPORTED);}
    @Override protected void onStop(){unregisterReceiver(dayAndReminders);app.visibleActivities--;super.onStop();}
    @Override protected void onResume(){super.onResume();if(app!=null){app.alarms.execute(()->Reminders.reschedule(this));if(PrivacyUi.accepted(this))app.billing.start();if(screen.equals("Today")&&todayUi!=null)render();TaskAssist.onForeground(this);}}
    @Override public void onSaveInstanceState(Bundle state){state.putString("screen",screen);state.putString("importModel",importModel);state.putString("modelFilter",modelFilter);if(scroll!=null)tabScrollPositions.put(screen,scroll.getScrollY());Bundle positions=new Bundle();for(Map.Entry<String,Integer> position:tabScrollPositions.entrySet())positions.putInt(position.getKey(),position.getValue());state.putBundle("tabScrollPositions",positions);super.onSaveInstanceState(state);}
    @Override protected void onPause(){if(app!=null)app.persistWritingDraft();if(voice!=null&&voice.recording())voice.stop();if(tts!=null)tts.stop();super.onPause();}
    @Override protected void onDestroy(){destroyed=true;if(content!=null)content.animate().cancel();app.listeners.remove(observer);if(featureDialog!=null)featureDialog.dismiss();if(voice!=null)voice.stop();if(tts!=null)tts.shutdown();super.onDestroy();}
    int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(3),1);return v;}
    TextView bold(String value,int size){TextView v=text(value,size,INK);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    void add(LinearLayout parent,View child){parent.addView(child,new LinearLayout.LayoutParams(-1,-2));}
    void gap(LinearLayout parent,int size){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(size)));}
    Button button(String value,boolean primary,Runnable action){
        Button b=new Button(this);b.setText(value);Y2k.styleButton(b,primary?Y2k.PINK:PAPER);b.setOnClickListener(v->{if(app.maintenance){toast("Personal data is being erased. Please wait.");return;}v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);action.run();});return b;
    }
    Button quietAction(String label,String description,Runnable action){Button b=button(label,false,action);b.setContentDescription(description);b.setTooltipText(description);b.setTextSize(18);b.setMinWidth(dp(48));b.setMinimumWidth(dp(48));b.setPadding(dp(8),0,dp(8),0);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x28222025),shape(Color.TRANSPARENT,10),null));return b;}
    void paired(LinearLayout parent,Button a,Button b){LinearLayout r=row();r.setBaselineAligned(false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(0,0,dp(8),0);r.addView(a,p);r.addView(b,new LinearLayout.LayoutParams(0,-1,1));add(parent,r);}
    EditText input(String hint,boolean multiline){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(16);e.setTextColor(INK);e.setHintTextColor(MUTED);GradientDrawable background=shape(PAPER,10);background.setStroke(dp(1.5f),LINE);e.setBackground(background);e.setPadding(dp(15),dp(14),dp(15),dp(14));e.setGravity(Gravity.TOP);e.setSingleLine(!multiline);e.setFilters(new InputFilter[]{(source,start,end,dest,dstart,dend)->{if((long)dest.length()-(dend-dstart)+(end-start)>Math.max(InputLimits.EDITOR_CEILING,dest.length())){e.post(()->toast("This edit is too large to open safely. Split the source into smaller files. Your existing text is unchanged."));return dest.subSequence(dstart,dend);}return null;}});if(multiline)e.setMinLines(4);return e;}
    LinearLayout card(){LinearLayout l=column();l.setBackground(Y2k.sticker(this,PAPER,15));l.setPadding(dp(18),dp(18),dp(18),dp(18));return l;}
    void watch(EditText e,java.util.function.Consumer<String> consumer){e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){consumer.accept(s.toString());}public void afterTextChanged(Editable e){}});}
    TextView limit(LinearLayout parent,EditText field,InputLimits.Rule rule){
        TextView counter=text("",11,MUTED);counter.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(parent,counter);
        java.util.function.Consumer<String> update=value->{String error=rule.error(value);counter.setText(rule.counter(value)+(error!=null&&!value.isEmpty()?"\n"+error:""));counter.setTextColor(error!=null&&!value.isEmpty()?0xffa92043:MUTED);};
        update.accept(field.getText().toString());watch(field,update);return counter;
    }
    TextView compactLimit(LinearLayout parent,EditText field,InputLimits.Rule rule){
        TextView counter=text("",11,MUTED);counter.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(parent,counter);
        java.util.function.Consumer<String> update=value->{InputLimits.Count c=InputLimits.count(value);boolean over=c.words>rule.words||c.characters>rule.characters;String label=String.format(Locale.ROOT,"%,d words",c.words);if(c.invalid)label="Remove unsupported characters.";else if(over)label=(c.words>rule.words?String.format(Locale.ROOT,"%,d / %,d words",c.words,rule.words):String.format(Locale.ROOT,"%,d / %,d characters",c.characters,rule.characters))+" · Shorten or save to Memory";counter.setText(label);counter.setTextColor(over||c.invalid?0xffa92043:MUTED);counter.setVisibility(value.isEmpty()?View.GONE:View.VISIBLE);};update.accept(field.getText().toString());watch(field,update);return counter;
    }
    boolean valid(InputLimits.Rule rule,String value){String error=rule.error(value);if(error==null)return true;toast(error);return false;}
    void toast(String message){if(!destroyed)Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    void go(String target){
        int from=Y2k.tabIndex(screen),to=Y2k.tabIndex(target);boolean changed=!screen.equals(target),navigation=changed&&from>=0&&to>=0;
        if(navigation&&scroll!=null)tabScrollPositions.put(screen,scroll.getScrollY());
        if(content!=null)content.animate().cancel();screen=target;render();
        if(navigation&&screen.equals(target)){restoreTabPosition(target);if(from!=to)Y2k.enterTab(content,to>from?1:-1);}
    }
    void restoreTabPosition(String target){ScrollView current=scroll;int position=tabScrollPositions.getOrDefault(target,0);current.post(()->{if(!destroyed&&scroll==current)current.scrollTo(0,position);});}
    void render(){
        seenTaskChanges=app.taskChanges.get();
        if(!PrivacyUi.accepted(this))screen="Setup";
        root=column();root.setBackground(Y2k.grid(this));root.setFocusableInTouchMode(true);root.setDescendantFocusability(android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS);root.requestFocus();
        root.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());view.setPadding(i.left,i.top,i.right,i.bottom);return insets;});
        LinearLayout header=row();header.setPadding(dp(22),dp(15),dp(18),dp(12));
        TextView mark=text("☺",27,INK);mark.setGravity(Gravity.CENTER);mark.setBackground(Y2k.sticker(this,Y2k.YELLOW,24));mark.setRotation(-8);mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams markSize=new LinearLayout.LayoutParams(dp(40),dp(40));markSize.setMargins(0,0,dp(10),0);header.addView(mark,markSize);
        TextView brand=bold("POCKET MEMORY",18);brand.setLetterSpacing(-0.035f);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=button("•••",false,this::settings);settings.setId(R.id.settings_open);settings.setContentDescription("Settings, privacy and backup");header.addView(settings,new LinearLayout.LayoutParams(dp(54),-2));add(root,header);
        status=text("",11,MUTED);status.setLetterSpacing(0.035f);status.setPadding(dp(22),0,dp(22),dp(10));add(root,status);
        scroll=screen.equals("Chat")?new ChatUi.ChatScroll(this):new ScrollView(this);scroll.setFillViewport(true);content=column();content.setPadding(dp(22),dp(12),dp(22),dp(24));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=row();nav.setBackgroundColor(PAPER);nav.setPadding(dp(8),dp(10),dp(8),dp(10));
        String[] tabs={"Today","Memory","Chat","Writing","Models"};String[] navEmoji={"☀️","🧠","💬","✍️","✨"};int[] ids={R.id.nav_today,R.id.nav_memory,R.id.nav_chat,R.id.nav_writing,R.id.nav_models};String selectedScreen=screen.equals("Ask")?"Memory":screen;
        for(int i=0;i<tabs.length;i++){String tab=tabs[i];Button b=button(tab,selectedScreen.equals(tab),()->go(tab));b.setId(ids[i]);b.setSelected(selectedScreen.equals(tab));String icon=navEmoji[i];android.text.SpannableString caption=new android.text.SpannableString(icon+"\n"+tab);caption.setSpan(new android.text.style.RelativeSizeSpan(1.7f),0,icon.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);b.setText(caption);b.setContentDescription(tab+" tab");b.setTextSize(10);b.setMinHeight(dp(58));b.setPadding(dp(3),dp(4),dp(3),dp(4));if(selectedScreen.equals(tab))b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x28222025),Y2k.sticker(this,Y2k.YELLOW,12),null));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),0,dp(3),0);nav.addView(b,p);}if(!screen.equals("Setup"))add(root,nav);
        switch(screen){case "Setup":new PrivacyUi(this).setup();break;case "Today":todayUi.render();break;case "Chat":chatUi.render();break;case "Ask":askScreen();break;case "Writing":writingScreen();break;case "Models":modelsScreen();break;default:memoryScreen();}
        setContentView(root);root.requestApplyInsets();refresh();
    }
    void title(String heading,String description){
        TextView tag=text("✳ YOUR LITTLE MEMORY UNIVERSE",10,INK);tag.setTypeface(Typeface.DEFAULT,Typeface.BOLD);tag.setLetterSpacing(.08f);add(content,tag);gap(content,14);
        TextView h=text(heading.toUpperCase(Locale.ROOT),31,INK);h.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));h.setLetterSpacing(-0.04f);h.setBackground(Y2k.sticker(this,PAPER,8));h.setPadding(dp(12),dp(9),dp(15),dp(13));h.setRotation(-1.3f);add(content,h);gap(content,16);add(content,text(description,14,MUTED));gap(content,20);
        h.setAccessibilityHeading(true);
    }
    void refresh(){
        if(destroyed||status==null)return;
        if(app.maintenance){status.setText("Erasing personal data…");return;}
        Catalog.Model active=app.catalog.get(app.activeId());String model=active!=null&&active.installedForDisplay(this)?active.name:"Choose a model to begin";
        status.setText(app.workStatus.isEmpty()?"ON THIS PHONE  ·  "+model:app.workStatus);
        if(screen.equals("Chat")&&chatUi!=null)chatUi.refresh();
        if(screen.equals("Memory")&&memoryCount!=null){int[] counts=app.store.counts();memoryCount.setText(counts[0]+(counts[0]==1?" saved memory":" saved memories"));indexCount.setText("Ready for local search with Llama");}
        if(screen.equals("Models")&&modelCards!=null){String fingerprint=app.downloadId+app.billing.message;for(Catalog.Model m:app.catalog.models)fingerprint+="|"+m.id+":"+m.installedForDisplay(this)+":"+app.access.allowed(m)+":"+app.billing.price(m);if(!fingerprint.equals(modelFingerprint)){modelFingerprint=fingerprint;renderModelCards();}updateDownloadProgress();}
        if(screen.equals("Ask")&&answerView!=null){answerView.setText(app.answer);if(answerCard!=null)answerCard.setVisibility(app.answer.isBlank()?View.GONE:View.VISIBLE);if(askStop!=null)askStop.setVisibility(app.busy?View.VISIBLE:View.GONE);String fingerprint=app.sources.toString();if(!fingerprint.equals(sourceFingerprint)){sourceFingerprint=fingerprint;renderSources();}}
        if(screen.equals("Writing")&&writingUi!=null)writingUi.refresh();
    }
    void memoryScreen(){
        title("A place for everything\non your mind.","Save it once. Find it when you need it.");
        paired(content,button("Saved memories",true,()->{}),button("Ask memory",false,()->go("Ask")));gap(content,16);if(photoUi.hasDraft()){add(content,button("Finish your photo scan",false,photoUi::resumeDraft));gap(content,12);}
        LinearLayout info=card();info.setBackground(Y2k.sticker(this,Y2k.AQUA,15));TextView label=text("🧠 YOUR PRIVATE COLLECTION",10,INK);label.setLetterSpacing(0.12f);add(info,label);gap(info,10);memoryCount=bold("",21);memoryCount.setTextColor(INK);add(info,memoryCount);gap(info,6);indexCount=text("",13,INK);add(info,indexCount);gap(info,13);View accent=new View(this);accent.setBackgroundColor(CORAL);info.addView(accent,new LinearLayout.LayoutParams(dp(30),dp(3)));add(content,info);gap(content,16);
        Button photo=button("📸 Photo → text",false,photoUi::choose);photo.setId(R.id.scan_photo);paired(content,photo,button("Import text",false,()->pick(IMPORT_TEXT,"text/*")));gap(content,12);
        Button save=button("✨ Add a memory",true,()->edit(null,"",""));save.setId(R.id.add_memory);save.setTextColor(DEEP_INK);save.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x2217364d),Y2k.sticker(this,Y2k.PINK,12),null));add(content,save);gap(content,18);
        EditText search=input("Find a saved memory…",false);add(content,search);gap(content,16);LinearLayout list=column();add(content,list);
        int[] page={0};Runnable[] populate={null};populate[0]=()->{list.removeAllViews();String query=search.getText().toString();if(query.length()>200){add(list,text("Keep a library search under 200 characters.",13,MUTED));return;}List<MemoryStore.Note> notes=app.store.notePreviews(query,page[0]);if(notes.isEmpty()){LinearLayout empty=card();add(empty,bold("Make room in your head.",18));gap(empty,8);add(empty,text("Try saving where you put something, a conversation detail, an idea, or a useful piece of text.",14,MUTED));add(list,empty);}for(MemoryStore.Note n:notes.subList(0,Math.min(50,notes.size()))){LinearLayout c=card();add(c,bold(n.title,17));gap(c,7);add(c,text(excerpt(n.body,160),14,MUTED));gap(c,12);add(c,text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(n.updated)),11,MUTED));c.setOnClickListener(v->viewNote(n.id));add(list,c);gap(list,12);}if(page[0]>0)add(list,button("Previous memories",false,()->{page[0]=Math.max(0,page[0]-50);populate[0].run();}));if(notes.size()>50)add(list,button("More memories",false,()->{page[0]+=50;populate[0].run();}));};populate[0].run();watch(search,s->{page[0]=0;populate[0].run();});
    }
    static String excerpt(String text,int max){return text.length()>max?text.substring(0,max)+"…":text;}
    String date(long time){return DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(time));}
    void viewNote(long id){
        MemoryStore.Note n=app.store.note(id);if(n==null){toast("This memory was deleted. A saved answer may still contain its historical snapshot.");return;}
        LinearLayout panel=column();panel.setPadding(dp(18),dp(8),dp(18),dp(16));TextView body=text(n.body,16,INK);body.setTextIsSelectable(true);add(panel,body);gap(panel,12);add(panel,text((n.context.isBlank()?"":"Context: "+n.context+"\n")+"Saved: "+date(n.created)+"\nUpdated: "+date(n.updated),12,MUTED));gap(panel,14);
        List<JSONObject> document=app.store.documentParts(id);if(!document.isEmpty()){add(panel,button("🧩 Browse linked document parts",true,()->new SplitUi(this).parts(id)));gap(panel,10);}
        long photoOwner=id;List<JSONObject> photos=PhotoStore.forNote(app.store,id);if(photos.isEmpty())for(JSONObject part:document){List<JSONObject> linked=PhotoStore.forNote(app.store,part.optLong("note_id"));if(!linked.isEmpty()){photos=linked;photoOwner=part.optLong("note_id");break;}}final long shownPhoto=photoOwner;if(!photos.isEmpty()){add(panel,button("View "+photos.size()+(photos.size()==1?" saved photo":" saved photos"),true,()->photoUi.view(shownPhoto)));gap(panel,10);}
        paired(panel,button("Edit memory",false,()->edit(n,n.title,n.body)),button("History & related",false,()->noteHistory(id)));gap(panel,10);add(panel,button("Use in Writing",false,()->{app.writingInput=n.body;go("Writing");}));ScrollView scroller=new ScrollView(this);scroller.addView(panel);new ThemedDialog.Builder(this).setTitle(n.title+(n.status.equals("superseded")?" · superseded":"")).setView(scroller).setNegativeButton("Close",null).show();
    }
    void noteHistory(long id){
        MemoryStore.Note n=app.store.note(id);if(n==null){toast("This memory was deleted.");return;}
        LinearLayout panel=column();panel.setPadding(dp(18),dp(12),dp(18),dp(12));ScrollView scroller=new ScrollView(this);scroller.addView(panel);
        add(panel,text("Current status: "+n.status+". Earlier wording remains below. Saving an edit creates another version.",13,MUTED));gap(panel,12);
        add(panel,button("Related conversations",true,()->history("",id,0)));
        JSONArray versions=app.store.versions(id);for(int i=0;i<versions.length();i++){JSONObject v=versions.optJSONObject(i);gap(panel,12);add(panel,bold("Version "+v.optInt("revision")+" · "+date(v.optLong("created")),14));add(panel,text(v.optString("kind")+"\n"+v.optString("title")+"\n"+v.optString("body")+(v.optString("context").isBlank()?"":"\nContext: "+v.optString("context")),14,INK));add(panel,button("Use this version in editor",false,()->edit(app.store.note(id),v.optString("title"),v.optString("body"))));}
        JSONArray links=app.store.relations(id);for(int i=0;i<links.length();i++){JSONObject link=links.optJSONObject(i);long earlier=link.optLong("earlier"),later=link.optLong("later"),other=id==earlier?later:earlier;MemoryStore.Note related=app.store.note(other);if(related==null)continue;gap(panel,16);add(panel,bold("Relationship: "+link.optString("state"),14));add(panel,text(related.title+"\n"+excerpt(related.body,300),14,INK));add(panel,button("Open related memory",false,()->viewNote(other)));if(link.optString("state").equals("unresolved"))add(panel,button("Clarify this relationship",false,()->resolveRelationship(earlier,later)));}
        new ThemedDialog.Builder(this).setTitle("Memory history").setView(scroller).setPositiveButton("Close",null).show();
    }
    void resolveRelationship(long earlier,long later){
        new ThemedDialog.Builder(this).setTitle("How do these memories relate?").setMessage("Dates alone do not decide which statement is true.")
            .setPositiveButton("Later corrects earlier",(d,w)->{app.store.resolve(earlier,later,"corrected","");go("Memory");toast("Earlier memory kept in history and excluded from current answers.");})
            .setNeutralButton("Different context",(d,w)->{EditText context=input("Explain the different person, meaning, or situation",true);new ThemedDialog.Builder(this).setTitle("Context for the later memory").setView(context).setPositiveButton("Keep both",(x,y)->{try{app.store.resolve(earlier,later,"context",context.getText().toString());go("Memory");app.worker.execute(app.engine::indexAll);}catch(Exception e){toast(e.getMessage());}}).setNegativeButton("Cancel",null).show();}).setNegativeButton("Keep unresolved",null).show();
    }
    void storeWithAttachment(java.util.function.LongSupplier save,java.util.function.LongConsumer attachment){var db=app.store.getWritableDatabase();db.beginTransaction();try{long id=save.getAsLong();attachment.accept(id);db.setTransactionSuccessful();}finally{db.endTransaction();}}
    void saveReviewed(MemoryStore.Note existing,String title,String body,String context,long manualRelated,Dialog editor){saveReviewed(existing,title,body,context,manualRelated,editor,id->{});}
    void saveReviewed(MemoryStore.Note existing,String title,String body,String context,long manualRelated,Dialog editor,java.util.function.LongConsumer afterSave){
        try{InputLimits.note(title,body,context);}catch(IllegalArgumentException e){toast(e.getMessage());return;}
        Runnable done=()->{editor.dismiss();go("Memory");app.worker.execute(app.engine::indexAll);};
        if(existing!=null){try{storeWithAttachment(()->app.store.save(existing.id,title,body,context),afterSave);done.run();}catch(Exception e){toast(e.getMessage());}return;}
        List<MemoryStore.Note> candidates=app.store.possibleConflicts(0,body);
        if(manualRelated>0){MemoryStore.Note n=app.store.note(manualRelated);candidates=new ArrayList<>();if(n!=null)candidates.add(n);}
        if(candidates.isEmpty()){try{storeWithAttachment(()->app.store.save(0,title,body,context),afterSave);done.run();}catch(Exception e){toast(e.getMessage());}return;}
        if(candidates.size()==1){reviewSave(title,body,context,candidates.get(0),done,afterSave);return;}
        String[] labels=new String[candidates.size()];for(int i=0;i<labels.length;i++)labels[i]=candidates.get(i).title+" · "+excerpt(candidates.get(i).body,100);List<MemoryStore.Note> choices=candidates;
        new ThemedDialog.Builder(this).setTitle("Choose a possibly related memory").setItems(labels,(d,i)->reviewSave(title,body,context,choices.get(i),done,afterSave)).setNegativeButton("Cancel",null).show();
    }
    void reviewSave(String title,String body,String context,MemoryStore.Note earlier,Runnable done,java.util.function.LongConsumer afterSave){
        java.util.function.BiConsumer<String,String> save=(resolution,scope)->{try{storeWithAttachment(()->app.store.saveRelated(title,body,scope,earlier.id,resolution),afterSave);done.run();}catch(Exception e){toast(e.getMessage());}};
        new ThemedDialog.Builder(this).setTitle("Review a possible difference")
            .setMessage("Earlier: "+excerpt(earlier.body,600)+"\n\nNew: "+excerpt(body,600)+"\n\nThese may describe different people or contexts. This check cannot determine truth.")
            .setPositiveButton("Correct earlier",(d,w)->save.accept("corrected",context))
            .setNeutralButton("Keep both with context",(d,w)->{EditText scope=input("Explain the different person, meaning, or situation",true);scope.setText(context);new ThemedDialog.Builder(this).setTitle("Context for this new memory").setView(scope).setPositiveButton("Save both",(x,y)->save.accept("context",scope.getText().toString())).setNegativeButton("Cancel",null).show();})
            .setNegativeButton("Keep unresolved",(d,w)->save.accept("unresolved",context)).setCancelable(true).show();
    }
    void edit(MemoryStore.Note existing,String title,String body){
        Dialog dialog=new Dialog(this,R.style.AppTheme);noteDialog=dialog;LinearLayout form=column();form.setBackground(Y2k.grid(this));form.setPadding(dp(22),dp(26),dp(22),dp(24));
        add(form,bold(existing==null?"New memory":"Edit memory",24));gap(form,12);
        EditText context=input("Context (optional): person, situation, or meaning",false);context.setText(existing==null?"":existing.context);add(form,context);limit(form,context,InputLimits.CONTEXT);gap(form,8);
        final long[] related={0};if(existing==null){Button relation=button("Relates to an earlier memory (optional)",false,()->{List<MemoryStore.Note> notes=app.store.notes("");String[] labels=new String[notes.size()];for(int i=0;i<labels.length;i++)labels[i]=notes.get(i).title;if(labels.length==0){toast("No earlier memories yet.");return;}new ThemedDialog.Builder(this).setTitle("Choose an earlier memory").setItems(labels,(d,i)->{related[0]=notes.get(i).id;toast("Related memory selected. Choose the relationship when saving.");}).setNegativeButton("Cancel",null).show();});add(form,relation);gap(form,8);}
        EditText name=input("A title to find it later",false);name.setId(R.id.note_title);name.setText(title);add(form,name);limit(form,name,InputLimits.TITLE);gap(form,12);
        EditText text=input("What would you like to remember?",true);text.setId(R.id.note_body);text.setText(body);text.setMinLines(7);text.setMaxLines(12);add(form,text);limit(form,text,InputLimits.NOTE);new SplitUi(this).option(form,text,()->name.getText().toString(),()->context.getText().toString(),existing==null?0:existing.id,dialog,null,null);gap(form,12);
        if(app.catalog.get("whisper-base")!=null){add(form,button("Record a voice note",false,()->record("note",text)));gap(form,12);}
        Button save=button("Save memory",true,()->{
            try{saveReviewed(existing,name.getText().toString(),text.getText().toString(),context.getText().toString(),related[0],dialog);}
            catch(Exception e){toast(e.getMessage());}
        });save.setId(R.id.save_memory);paired(form,button("Cancel",false,dialog::dismiss),save);
        if(existing!=null){gap(form,12);add(form,button("Delete memory",false,()->new ThemedDialog.Builder(this).setTitle("Delete this memory?").setMessage("The memory, its versions and search passages will be removed. Past answers keep their saved evidence until you clear conversation history in Settings.").setPositiveButton("Delete",(d,w)->{app.store.delete(existing.id);dialog.dismiss();go("Memory");}).setNegativeButton("Cancel",null).show()));}
        ScrollView editorScroll=new ScrollView(this);editorScroll.setFillViewport(true);editorScroll.addView(form);dialog.setContentView(editorScroll);dialog.getWindow().setLayout(-1,-1);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        form.setOnApplyWindowInsetsListener((v,insets)->{var i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(dp(22)+i.left,dp(20)+i.top,dp(22)+i.right,dp(20)+i.bottom);return insets;});dialog.show();form.requestApplyInsets();
    }
    void askScreen(){
        paired(content,button("Saved memories",false,()->go("Memory")),button("Ask memory",true,()->{}));gap(content,16);
        title("Ask your memory.",app.historicalAnswer?"Historical answer · sources show the wording saved with this answer.":"Answers from information you have saved on this phone.");
        question=input("Where did I keep my headphones?",true);question.setId(R.id.question);question.setText(app.question);watch(question,s->app.question=s);add(content,question);compactLimit(content,question,InputLimits.PROMPT);gap(content,12);
        Button ask=button("🔎 Ask",true,this::ask);ask.setId(R.id.ask);if(app.catalog.get("whisper-base")!=null)paired(content,button("🎙 Voice",false,()->record("ask",question)),ask);else add(content,ask);gap(content,12);askStop=button("Stop",false,()->app.engine.cancel());add(content,askStop);gap(content,20);
        LinearLayout reply=card();answerCard=reply;LinearLayout tools=row();tools.addView(bold("Answer",16),new LinearLayout.LayoutParams(0,-2,1));Button copy=quietAction("Copy","Copy answer",()->copy(app.answer));copy.setTextSize(12);tools.addView(copy);tools.addView(quietAction("🔊","Read answer aloud",()->speak(app.answer)));answerReport=new ReportUi(this).flag(()->app.answer,()->app.answerPrompt);tools.addView(answerReport);add(reply,tools);gap(reply,8);answerView=text("",17,INK);answerView.setId(R.id.answer);answerView.setTextIsSelectable(true);add(reply,answerView);add(content,reply);gap(content,16);
        sourceList=column();add(content,sourceList);sourceFingerprint="";renderSources();gap(content,18);add(content,button("Previous questions",false,this::history));
    }
    void renderSources(){if(sourceList==null)return;sourceList.removeAllViews();int i=0;for(MemoryStore.Passage p:app.sources){LinearLayout c=card();add(c,bold("["+(++i)+"] "+p.title,14));gap(c,7);add(c,text(excerpt(p.text,220),13,MUTED));add(c,text("Source saved: "+date(p.updated)+(p.context.isBlank()?"":" · "+p.context),11,MUTED));c.setOnClickListener(v->viewSource(p));add(sourceList,c);gap(sourceList,9);}}
    void ask(){
        if(app.busy){toast("A task is already running. You can stop it first.");return;}
        String q=question.getText().toString().trim();if(!valid(InputLimits.PROMPT,q))return;
        app.historicalAnswer=false;app.question=q;app.answerPrompt=q;app.answer="";app.sources=new ArrayList<>();app.busy=true;app.taskFailed=false;app.changed();
        app.worker.execute(()->{try{app.engine.ask(q,new AppEngine.Reply(){public void text(String s){app.answer=s;app.changed();}public void sources(List<MemoryStore.Passage> p){app.sources=p;app.changed();}});}catch(Exception|UnsatisfiedLinkError e){app.answer=friendly(e);app.taskFailed=true;}finally{app.busy=false;app.workStatus="";app.changed();}});
    }
    void writingScreen(){writingUi.render();}
    void write(String operation){
        if(app.busy){toast("A task is already running. You can stop it first.");return;}
        String original=writing.getText().toString().trim();if(!valid(InputLimits.writing(operation),original))return;app.writingInput=original;app.writingPrompt=original;app.writingOutput="";app.writingRunning=true;app.writingFailed=false;app.busy=true;app.taskFailed=false;app.changed();
        app.worker.execute(()->{try{app.engine.write(operation,original,new AppEngine.Reply(){public void text(String s){app.writingOutput=s;app.changed();}public void sources(List<MemoryStore.Passage> s){}});}catch(Exception|UnsatisfiedLinkError e){app.writingOutput=friendly(e);app.writingFailed=true;app.taskFailed=true;}finally{app.writingRunning=false;app.busy=false;app.workStatus="";app.changed();}});
    }
    void modelsScreen(){
        title("Llama","Download once. Use it on this phone.");
        modelCards=column();add(content,modelCards);modelFingerprint="";renderModelCards();
    }
    void renderModelCards(){
        modelCards.removeAllViews();modelProgress.clear();modelActions.clear();modelBars.clear();
        for(Catalog.Model m:app.catalog.models){
            LinearLayout c=card();add(c,Y2k.badge(this,"BUILT WITH LLAMA",Y2k.AQUA));gap(c,12);add(c,bold(m.name,22));gap(c,7);add(c,text("By Meta · "+m.size()+" download",14,MUTED));gap(c,16);
            TextView progress=text("",14,INK);modelProgress.put(m.id,progress);add(c,progress);ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);modelBars.put(m.id,bar);add(c,bar);gap(c,10);
            Button action=button("",true,()->modelAction(m));modelActions.put(m.id,action);add(c,action);gap(c,8);add(c,button("Model details",false,()->modelDetails(m)));add(modelCards,c);
        }
        updateDownloadProgress();
    }
    void modelDetails(Catalog.Model model){
        LinearLayout form=column();form.setPadding(dp(20),dp(8),dp(20),dp(16));add(form,text(model.description,14,INK));gap(form,10);add(form,text(model.tradeoff,13,MUTED));gap(form,14);add(form,text(model.deviceDetails(),13,INK));gap(form,14);add(form,text(ModelTerms.details(model),12,MUTED));gap(form,14);
        add(form,button("Read licence",false,()->new PrivacyUi(this).document(ModelTerms.asset(model),model.license)));gap(form,8);add(form,button("Hugging Face source",false,()->openUrl("https://huggingface.co/"+model.repo)));
        ScrollView details=new ScrollView(this);details.addView(form);featureDialog=new ThemedDialog.Builder(this).setTitle(model.name).setView(details).setPositiveButton("Close",null).show();
    }
    void updateDownloadProgress(){
        for(Catalog.Model m:app.catalog.models){TextView label=modelProgress.get(m.id);if(label==null)continue;boolean running=app.downloadId.equals(m.id),installed=m.installedForDisplay(this);long partial=m.part(this).length();Button b=modelActions.get(m.id);ProgressBar bar=modelBars.get(m.id);
            bar.setVisibility(running?View.VISIBLE:View.GONE);bar.setProgress((int)(100*app.downloadBytes/m.bytes));
            label.setText(running?app.downloadMessage:installed?(m.id.equals(app.activeId())&&app.getSharedPreferences("settings",0).getBoolean("model_selected",false)?"Ready to go":"Ready to use"):partial>0?"Paused · "+(100*partial/m.bytes)+"% downloaded":"Not downloaded");
            b.setText(running?"Pause download":installed?(m.kind.equals("chat")?(m.id.equals(app.activeId())&&app.getSharedPreferences("settings",0).getBoolean("model_selected",false)?"Using Llama":"Use this model"):m.kind.equals("embedding")?"Rebuild search index":"Voice ready"):partial>0?"Resume download":"Download · "+m.size());
            boolean active=!running&&installed&&m.kind.equals("chat")&&m.id.equals(app.activeId())&&app.getSharedPreferences("settings",0).getBoolean("model_selected",false)&&m.available()&&app.access.allowed(m);
            if(!Boolean.valueOf(active).equals(b.getTag(R.id.model_active_state))){Y2k.styleButton(b,active?Y2k.AQUA:Y2k.PINK);b.setTag(R.id.model_active_state,active);}
            if(active){b.setText("✓ Active model");b.setContentDescription(m.name+" · Active model on this phone");}else b.setContentDescription(null);
            b.setEnabled(active||running||app.downloadId.isEmpty());
            if(!running&&!m.available()){label.setText(m.unavailableReason);b.setText("Commercial permission required");b.setEnabled(false);}
            else if(!running&&!app.access.allowed(m)){label.setText("One-time in-app access · Or enter an unlock code");b.setText("Unlock · "+app.billing.price(m));}
        }
        if(app.downloadId.isEmpty()&&!app.downloadMessage.isEmpty())status.setText(app.downloadMessage);
        else if(!app.billing.message.isEmpty())status.setText(app.billing.message);
    }
    void modelAction(Catalog.Model m){
        if(app.downloadId.equals(m.id)){startService(new Intent(this,ModelDownloadService.class).setAction("pause"));return;}
        if(!m.available()){toast(m.unavailableReason);return;}
        if(!app.access.allowed(m)){new ModelAccessUi(this).options(m);return;}
        if(!ModelTerms.accepted(this,m)){ModelTerms.ensure(this,m,()->modelAction(m));return;}
        if(m.installed(this)){
            if(m.kind.equals("chat")){if(m.id.equals(app.activeId())&&app.getSharedPreferences("settings",0).getBoolean("model_selected",false)){go("Ask");return;}if(app.busy){toast("Finish or stop the current task before switching.");return;}app.active(m.id);app.worker.execute(NativeEngine::unload);go("Ask");}
            else if(m.kind.equals("embedding")){app.worker.execute(()->{app.store.clearVectors();app.engine.indexAll();});}
            else toast("Use Voice in Ask, or record a voice note in Memory.");return;
        }
        if(!app.downloadId.isEmpty()){toast("Finish or pause the current download first.");return;}
        if(!new ModelAccessUi(this).compatible(m))return;
        startForegroundService(new Intent(this,ModelDownloadService.class).putExtra("id",m.id));
    }
    void removeModel(Catalog.Model m){
        if(app.busy||!app.downloadId.isEmpty()){toast("Finish the current task or download first.");return;}
        new ThemedDialog.Builder(this).setTitle("Remove "+m.name+"?").setMessage("This frees "+m.size()+". Your saved memories will remain.").setPositiveButton("Remove",(d,w)->app.worker.execute(()->{
            NativeEngine.unload();try{Files.deleteIfExists(m.file(this).toPath());Files.deleteIfExists(m.marker(this).toPath());Files.deleteIfExists(m.part(this).toPath());if(m.kind.equals("embedding"))app.store.clearVectors();m.invalidateDisplay();app.changed();}catch(Exception e){m.invalidateDisplay();app.changed();app.main.post(()->toast(e.getMessage()));}
        })).setNegativeButton("Cancel",null).show();
    }
    void record(String target,EditText destination){
        if(voice.recording()){voice.stop();toast("Transcribing on your phone…");return;}
        if(app.busy){toast("Finish the current task before recording.");return;}
        Catalog.Model model=app.catalog.get("whisper-base");if(model==null){toast("Voice input is not included in this edition. Type your question instead.");return;}if(!model.installed(this)){toast("Download Offline voice in Models first.");go("Models");return;}
        if(!ModelTerms.accepted(this,model)){ModelTerms.ensure(this,model,()->record(target,destination));return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){recordTarget=target;requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC);return;}
        app.workStatus="Listening · tap Voice again to stop · maximum 30 seconds";app.changed();
        voice.start(this,new OfflineVoice.RecordingResult(){public void ready(float[] samples){
            app.busy=true;app.engine.status("Transcribing on this phone…");app.worker.execute(()->{try(ModelHarness.Job job=app.engine.harness.begin(ModelHarness.Task.VOICE)){app.engine.harness.stage(ModelHarness.Stage.GENERATING);String transcript=OfflineVoice.transcribe(model.file(app).getPath(),samples,voiceLanguage).trim();app.engine.harness.check();job.success();app.main.post(()->{if(!destroyed){destination.setText(destination.getText().toString()+(destination.length()>0?"\n":"")+transcript);toast("Transcript ready. Review it before saving or asking.");}});}catch(Exception|UnsatisfiedLinkError e){app.main.post(()->toast(friendly(e)));}finally{app.busy=false;app.workStatus="";app.changed();}});
        }public void failed(String error){app.workStatus="";app.changed();app.main.post(()->toast(error));}});
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==NOTIFICATIONS){toast(Reminders.notifications(this)?"Notifications enabled. Open Reminder setup for precise timing and lock-screen alerts.":"Notifications are off. You can enable them in Android Settings.");if(screen.equals("Today"))render();}if(code==TaskAssist.PERMISSION){TaskAssist.onForeground(this);toast(Reminders.notifications(this)?"Task view ready.":"Notifications are off. Your tasks remain in the app.");if(screen.equals("Today"))render();}if(code==MIC)toast(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED?"Microphone enabled. Tap Voice to record.":"You can continue using text without microphone access.");}
    void speak(String value){
        if(value.isBlank()){toast("There is no answer to read yet.");return;}if(!ttsReady){toast("Speech output is still initializing.");return;}
        Set<Voice> voices=tts.getVoices();Voice selected=null;Locale wanted=Locale.getDefault();
        if(voices!=null)for(Voice v:voices)if(!v.isNetworkConnectionRequired()&&v.getLocale().getLanguage().equals(wanted.getLanguage())){selected=v;break;}
        if(selected==null&&voices!=null)for(Voice v:voices)if(!v.isNetworkConnectionRequired()&&v.getLocale().getLanguage().equals("en")){selected=v;break;}
        if(selected==null){toast("Install an offline text-to-speech voice in Android Settings first.");return;}
        tts.setVoice(selected);int max=TextToSpeech.getMaxSpeechInputLength();for(int start=0;start<value.length();start+=max)tts.speak(value.substring(start,Math.min(start+max,value.length())),start==0?TextToSpeech.QUEUE_FLUSH:TextToSpeech.QUEUE_ADD,null,"memory-"+start);
    }
    void settings(){
        String[] options={"Export password-protected backup","Import a backup","Voice language","Release model memory","About local storage","Open-source notices","All memory history","Clear memory answer history","Model task diagnostics","Reminder permissions","Privacy, reports & data controls","Browser companion · WebMCP"};
        boolean hasVoice=app.catalog.get("whisper-base")!=null;String[] visibleOptions=java.util.stream.IntStream.range(0,options.length).filter(i->hasVoice||i!=2).mapToObj(i->options[i]).toArray(String[]::new);
        featureDialog=new ThemedDialog.Builder(this).setTitle("Your pocket memory").setItems(visibleOptions,(d,selected)->{int which=!hasVoice&&selected>=2?selected+1:selected;
            if(which==11)CompanionUi.open(this);
            if(which==0)new PrivacyUi(this).chooseExport();
            if(which==1)pick(RESTORE,"*/*");
            if(which==2)new ThemedDialog.Builder(this).setTitle("Voice input language").setItems(new String[]{"Detect automatically","English","Hindi"},(dialog,index)->{voiceLanguage=new String[]{"auto","en","hi"}[index];toast("Voice input language updated.");}).setNegativeButton("Close",null).show();
            if(which==3){if(app.busy)toast("Stop the current task first.");else app.worker.execute(()->{NativeEngine.unload();app.engine.status("Model memory released.");});}
            if(which==4)new ThemedDialog.Builder(this).setTitle("Stored on this phone").setMessage("Models: "+Catalog.directory(this).getPath()+"\n\nMemories, tasks, daily notes, chat and search vectors: private SQLite database. Photos: private compressed files. Automatic cloud backup is disabled.\n\nOCR processes images and text on this phone. The Google ML Kit SDK may send diagnostic metrics when online; your photos and recognized text are not uploaded.\n\nDeleting the app also deletes its private files. Password-protected backups require their password to restore.\n\nVersion "+BuildConfig.VERSION_NAME+" · Android 13+ · arm64\nPublisher: "+BuildConfig.PUBLISHER).setPositiveButton("Privacy & data",(x,y)->new PrivacyUi(this).privacy()).setNegativeButton("Close",null).show();
            if(which==5){try(InputStream in=getAssets().open("third-party-notices.txt")){new ThemedDialog.Builder(this).setTitle("Open-source notices").setMessage(new String(in.readAllBytes(),StandardCharsets.UTF_8)).setPositiveButton("Close",null).show();}catch(IOException e){toast("Notices could not be opened.");}}
            if(which==6){List<MemoryStore.Note> notes=app.store.allNotes("");String[] labels=new String[notes.size()];for(int i=0;i<labels.length;i++)labels[i]=notes.get(i).title+" · "+notes.get(i).status;new ThemedDialog.Builder(this).setTitle("Current and superseded memories").setItems(labels,(x,i)->noteHistory(notes.get(i).id)).setPositiveButton("Close",null).show();}
            if(which==7)new ThemedDialog.Builder(this).setTitle("Clear memory answer history?").setMessage("This removes saved questions, answers and their evidence snapshots. Your memories and memory versions remain.").setPositiveButton("Clear history",(x,y)->{app.store.clearConversations();toast("Memory answer history cleared.");}).setNegativeButton("Cancel",null).show();
            if(which==9)Reminders.setup(this);
            if(which==10)new PrivacyUi(this).privacy();
            if(which==8)new ThemedDialog.Builder(this).setTitle("Recent model tasks").setMessage(app.engine.harness.diagnostics()).setPositiveButton("Close",null).show();

        }).setNegativeButton("Close",null).show();
    }
    void viewSource(MemoryStore.Passage p){new ThemedDialog.Builder(this).setTitle(p.title+" · source snapshot").setMessage(p.text+(p.context.isBlank()?"":"\n\nContext: "+p.context)+"\n\nSource updated: "+date(p.updated)+"\nThis is the wording used for the answer.").setPositiveButton("Current memory",(d,w)->viewNote(p.noteId)).setNeutralButton("Memory history",(d,w)->noteHistory(p.noteId)).setNegativeButton("Close",null).show();}
    void history(){history("",0,0);}
    void history(String query,long noteId,int offset){
        JSONArray history=app.store.conversations(query,noteId,offset);LinearLayout panel=column();panel.setPadding(dp(18),dp(8),dp(18),dp(8));EditText search=input("Search all past questions, answers and sources",false);search.setText(query);add(panel,search);gap(panel,8);add(panel,button("Search history",true,()->history(search.getText().toString(),noteId,0)));gap(panel,12);
        if(history.length()==0)add(panel,text("No matching past conversations.",14,MUTED));
        AlertDialog[] dialog={null};for(int i=0;i<history.length();i++){JSONObject saved=history.optJSONObject(i);Button entry=button(date(saved.optLong("created"))+"\n"+excerpt(saved.optString("question"),110),false,()->{dialog[0].dismiss();openConversation(saved);});entry.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);add(panel,entry);gap(panel,6);}
        if(history.length()==100)add(panel,button("Older conversations",false,()->{dialog[0].dismiss();history(query,noteId,offset+100);}));
        ScrollView scroller=new ScrollView(this);scroller.addView(panel);dialog[0]=new ThemedDialog.Builder(this).setTitle(noteId>0?"Related conversation history":"Conversation history").setView(scroller).setNegativeButton("Close",null).create();dialog[0].show();
    }
    void openConversation(JSONObject saved){
        app.historicalAnswer=true;app.question=saved.optString("question");app.answerPrompt=app.question;app.answer="Saved answer · "+date(saved.optLong("created"))+"\n\n"+saved.optString("answer");app.sources=new ArrayList<>();JSONArray snapshots=saved.optJSONArray("source_snapshots");
        if(snapshots!=null)for(int i=0;i<snapshots.length();i++){JSONObject j=snapshots.optJSONObject(i);MemoryStore.Passage p=new MemoryStore.Passage(0,j.optLong("note_id"),j.optString("title"),j.optString("text"),j.optLong("updated"));p.context=j.optString("context");app.sources.add(p);}
        if(app.sources.isEmpty())app.answer+="\n\nThis older answer has no saved evidence snapshot.";go("Ask");
    }
    void copy(String value){if(value==null||value.isBlank()){toast("Nothing to copy yet.");return;}getSystemService(android.content.ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Pocket Memory",value));toast("Copied.");}
    void openUrl(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){toast("No browser is installed.");}}
    void pick(int request,String type){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type);startActivityForResult(i,request);}
    static String friendly(Throwable e){String m=e.getMessage();return m==null?"This task could not finish. Please try again.":m;}
    byte[] readLimited(Uri uri,int limit) throws Exception {try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IOException("Could not open this file.");byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("This file is too large to import.");out.write(buffer,0,n);}return out.toByteArray();}}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(app.maintenance){toast("Data deletion is in progress. Please choose the file again afterward.");return;}if(request==CAPTURE_PHOTO){photoUi.captured(result==RESULT_OK);return;}if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==PICK_PHOTO){photoUi.importPhoto(uri,()->{});return;}
        if(request==IMPORT_TEXT){app.files.execute(()->{try{String body=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(readLimited(uri,InputLimits.TEXT_IMPORT_BYTES))).toString();if(body.indexOf('\0')>=0)throw new IOException("Choose a plain text or Markdown file.");app.main.post(()->{if(!destroyed)edit(null,"Imported text",body);});}catch(Exception e){app.main.post(()->toast(e.getMessage()));}});}
        if(request==EXPORT)new PrivacyUi(this).export(uri);
        if(request==RESTORE)new PrivacyUi(this).restore(uri);
        if(request==IMPORT_MODEL){Catalog.Model m=app.catalog.get(importModel);if(m==null)return;if(!app.access.allowed(m)){toast("Unlock this model before importing its file.");return;}if(!app.downloadId.isEmpty()){toast("Pause the download before importing a model.");return;}app.files.execute(()->{File temp=new File(m.file(this).getPath()+".import");try{if(!app.access.allowed(m))throw new IOException("Model access changed. Unlock it before importing.");if(new android.os.StatFs(Catalog.directory(this).getPath()).getAvailableBytes()<m.bytes+100_000_000L)throw new IOException("Free up storage before importing this model.");app.engine.status("Importing "+m.name+"…");try(InputStream in=getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(temp)){if(in==null)throw new IOException("Could not open this model file.");byte[] buffer=new byte[131072];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>m.bytes)throw new IOException("This is not the selected model file.");out.write(buffer,0,n);}out.getFD().sync();}ModelDownloadService.verify(temp,m);Files.move(temp.toPath(),m.file(this).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);ModelTerms.installNotices(this,m);Files.write(m.marker(this).toPath(),m.sha256.getBytes(StandardCharsets.UTF_8));m.invalidateDisplay();if(m.kind.equals("embedding"))app.worker.execute(()->app.engine.indexAll());app.engine.status("Model imported and verified.");app.changed();}catch(Exception e){temp.delete();app.engine.status(friendly(e));}});}
    }
}
