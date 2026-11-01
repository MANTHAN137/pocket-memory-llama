package com.pocketmemory.app;

import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;

/** Direct local-model conversation. Saving an assistant reply to memory is always explicit. */
public final class ChatUi {
    /** Observe drag direction without consuming native scrolling or accessible click handling. */
    static final class ChatScroll extends ScrollView {
        Runnable readingOlder;float downY;
        ChatScroll(android.content.Context context){super(context);}
        @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)downY=event.getY();
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_MOVE&&event.getY()-downY>android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop()&&readingOlder!=null)readingOlder.run();
            return super.dispatchTouchEvent(event);
        }
    }
    final MainActivity a;final MemoryApp app;LinearLayout messages;EditText input;String fingerprint="",paintedChat="";long paintedVersion=-1;TextView streamBody;Button stop,sendButton;ScrollView chatScroll;boolean followNewest=true,scrollPending=false,programmaticScroll=false;
    ChatUi(MainActivity a){this.a=a;app=a.app;}
    String draft(){
        android.content.SharedPreferences settings=app.getSharedPreferences("settings",0);
        String id=settings.getString("new_chat","");
        if(id.isEmpty()||app.chats.exists(id)||id.equals(app.chatRunningId)){id=app.chats.create();settings.edit().putString("new_chat",id).apply();}
        return id;
    }
    String active(){
        if(app.chatId.isEmpty()){
            app.chatId=app.getSharedPreferences("settings",0).getString("chat","");
            String pending=app.getSharedPreferences("settings",0).getString("new_chat","");
            if(app.chatId.isEmpty()||!app.chats.exists(app.chatId)&&!app.chatId.equals(pending)){
                List<JSONObject> chats=app.chats.chats();app.chatId=chats.isEmpty()?draft():chats.get(0).optString("id");
            }
        }
        return app.chatId;
    }
    void select(String id){app.chatId=id;app.getSharedPreferences("settings",0).edit().putString("chat",id).apply();a.go("Chat");}
    void newChat(){String id=active();select(!app.chats.exists(id)&&!id.equals(app.chatRunningId)?id:draft());}
    void render(){
        active();a.content.setPadding(a.dp(22),a.dp(12),a.dp(22),a.dp(4));chatScroll=a.scroll;followNewest=true;scrollPending=false;a.add(a.content,a.bold("Chat",25));a.gap(a.content,14);
        a.paired(a.content,a.button("Conversations",false,this::threads),a.button("+ New chat",false,this::newChat));a.gap(a.content,8);Switch web=new Switch(a);web.setText("Search the web");web.setTextColor(MainActivity.INK);web.setMinHeight(a.dp(48));web.setChecked(app.getSharedPreferences("settings",0).getBoolean("chat_web",false));web.setOnCheckedChangeListener((v,on)->{app.getSharedPreferences("settings",0).edit().putBoolean("chat_web",on).apply();if(on)a.toast("Only the message you send goes to Exa search.");});a.add(a.content,web);a.gap(a.content,12);messages=a.column();fingerprint="";paintedChat="";streamBody=null;a.add(a.content,messages);paint();
        LinearLayout composer=a.column();composer.setId(R.id.chat_composer);composer.setPadding(a.dp(22),a.dp(6),a.dp(22),a.dp(8));composer.setBackgroundColor(MainActivity.BG);
        input=a.input("What's on your mind?",true);input.setId(R.id.chat_input);input.setMinLines(1);input.setMaxLines(3);input.setText(app.getSharedPreferences("chat_drafts",0).getString(app.chatId,""));String chat=app.chatId;a.watch(input,s->app.getSharedPreferences("chat_drafts",0).edit().putString(chat,s).apply());a.add(composer,input);a.compactLimit(composer,input,InputLimits.PROMPT);Button longText=a.button("🧩 Save long text to Memory",false,()->a.edit(null,"From a conversation",input.getText().toString()));a.add(composer,longText);java.util.function.Consumer<String> suggest=s->{InputLimits.Count c=InputLimits.count(s);longText.setVisibility(c.words>InputLimits.PROMPT.words||c.characters>InputLimits.PROMPT.characters?android.view.View.VISIBLE:android.view.View.GONE);};suggest.accept(input.getText().toString());a.watch(input,suggest);a.gap(composer,8);
        LinearLayout controls=a.row();sendButton=a.button("Send ↗",true,this::send);sendButton.setId(R.id.chat_send);stop=a.button("Stop",false,app.engine::cancel);for(Button control:app.catalog.get("whisper-base")==null?new Button[]{stop,sendButton}:new Button[]{a.button("🎙 Voice",false,()->a.record("chat",input)),stop,sendButton}){LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-2,1);size.setMargins(a.dp(3),0,a.dp(3),0);controls.addView(control,size);}a.add(composer,controls);a.root.addView(composer,a.root.getChildCount()-1,new LinearLayout.LayoutParams(-1,-2));jumpToLatest();
        if(chatScroll instanceof ChatScroll)((ChatScroll)chatScroll).readingOlder=()->followNewest=false;
        chatScroll.setOnScrollChangeListener((View.OnScrollChangeListener)(v,x,y,oldX,oldY)->{if(!programmaticScroll&&!scrollPending)followNewest=atBottom();});
        chatScroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(b-t!=ob-ot&&followNewest)jumpToLatest();});
    }
    void jumpToLatest(){
        if(scrollPending)return;ScrollView target=chatScroll;String chat=app.chatId;scrollPending=true;
        target.post(()->{if(a.destroyed||a.scroll!=target||!a.screen.equals("Chat")||!app.chatId.equals(chat))return;
            if(!followNewest){scrollPending=false;return;}programmaticScroll=true;target.scrollTo(0,Math.max(0,target.getChildAt(0).getHeight()-target.getHeight()+target.getPaddingBottom()));programmaticScroll=false;scrollPending=false;
        });
    }
    boolean atBottom(){return a.scroll.getChildCount()==0||a.scroll.getChildAt(0).getHeight()-a.scroll.getScrollY()-a.scroll.getHeight()<a.dp(120);}
    void refresh(){if(messages!=null)paint();if(stop!=null)stop.setVisibility(app.busy&&app.chatId.equals(app.chatRunningId)?android.view.View.VISIBLE:android.view.View.GONE);if(sendButton!=null)sendButton.setEnabled(!app.busy);}
    void paint(){String id=active();if(id.equals(paintedChat)&&app.chatVersion==paintedVersion&&app.busy&&id.equals(app.chatRunningId)&&streamBody!=null){boolean follow=followNewest;streamBody.setText(app.chatReply.isEmpty()?"Thinking…":app.chatReply);if(streamBody instanceof ThinkingText)((ThinkingText)streamBody).thinking(app.chatReply.isEmpty());if(follow)jumpToLatest();return;}boolean follow=followNewest;paintedChat=id;paintedVersion=app.chatVersion;streamBody=null;List<JSONObject> history=app.chats.messages(id,60);String key=id+"|"+app.chatVersion+"|"+history.size()+"|"+(id.equals(app.chatRunningId)?app.chatReply:"")+"|"+app.busy;if(key.equals(fingerprint))return;fingerprint=key;messages.removeAllViews();
        if(history.size()==60){a.add(messages,a.button("Open older messages",false,()->allMessages(id)));a.gap(messages,12);}
        for(JSONObject message:history)bubble(message.optString("role"),message.optString("text"),message.optString("state"),()->actions(message),()->previousQuestion(message));
        if(id.equals(app.chatRunningId)&&app.busy)bubble("assistant",app.chatReply.isEmpty()?"Thinking…":app.chatReply,"streaming",()->{},()->"");
        if(follow)jumpToLatest();
    }
    void bubble(String role,String text,String state,Runnable click,java.util.function.Supplier<String> prompt){
        boolean user=role.equals("user"),streaming=state.equals("streaming");LinearLayout bubble=a.card();bubble.setTag("chat-bubble");bubble.setBackground(Y2k.sticker(a,user?Y2k.AQUA:MainActivity.PAPER,14));bubble.setPadding(a.dp(14),a.dp(12),a.dp(17),a.dp(15));
        TextView body=streaming?new ThinkingText(a):a.text("",16,MainActivity.INK);body.setText(user?text:SafetyGate.output(text));if(streaming)((ThinkingText)body).thinking(app.chatReply.isEmpty());body.setTag("chat-message-body");body.setTextIsSelectable(true);if(streaming)streamBody=body;
        TextView tag=a.text(user?"YOU":state.equals("complete")?"LLAMA":streaming?"LLAMA":"LLAMA · "+state.toUpperCase(Locale.ROOT),10,MainActivity.MUTED);tag.setLetterSpacing(0.1f);
        LinearLayout header=a.row();header.setBaselineAligned(false);header.setGravity(Gravity.CENTER_VERTICAL);header.setMinimumHeight(a.dp(48));header.addView(tag,new LinearLayout.LayoutParams(0,-2,1));
        if(!streaming){if(!user)header.addView(new ReportUi(a).flag(()->text,prompt));header.addView(a.quietAction("•••",user?"Message options":"Response options",click));}
        bubble.setPadding(a.dp(14),a.dp(4),a.dp(17),a.dp(13));a.add(bubble,header);a.add(bubble,body);bubble.setOnLongClickListener(v->{click.run();return true;});
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(user?a.dp(24):0,0,user?0:a.dp(24),a.dp(12));messages.addView(bubble,p);
    }
    void send(){if(app.busy){a.toast("A model task is already running. Stop it or wait for it to finish.");return;}String text=input.getText().toString().trim();if(!a.valid(InputLimits.PROMPT,text))return;String id=active();app.chatRunningId=id;app.chatReply="";app.busy=true;followNewest=true;input.setText("");jumpToLatest();app.changed();
        boolean web=app.getSharedPreferences("settings",0).getBoolean("chat_web",false);
        app.worker.execute(()->{try{AppEngine.Reply reply=new AppEngine.Reply(){public void text(String value){app.chatReply=value;app.changed();}public void sources(List<MemoryStore.Passage> sources){}};if(web)app.engine.webChat(id,text,reply);else app.engine.chat(id,text,reply);}catch(Exception|UnsatisfiedLinkError error){String message=MainActivity.friendly(error);if(web&&app.chats.exists(id)){app.chats.message(id,"assistant",message,"error");app.chatVersion++;}a.app.main.post(()->a.toast(message));}finally{app.busy=false;app.chatRunningId="";app.chatReply="";app.workStatus="";app.changed();}});
    }
    void actions(JSONObject message){String value=message.optString("text");boolean user=message.optString("role").equals("user");new ThemedDialog.Builder(a).setTitle(user?"Your message":"Model reply").setItems(user?new String[]{"Copy","Read aloud","Save as memory"}:new String[]{"Copy","Read aloud","Save as memory","Report response"},(d,i)->{if(i==0)a.copy(user?value:SafetyGate.output(value));if(i==1)a.speak(user?value:SafetyGate.output(value));if(i==2)a.edit(null,"From a conversation",value);if(i==3)new ReportUi(a).compose(value,previousQuestion(message));}).setNegativeButton("Close",null).show();}
    String previousQuestion(JSONObject message){String question="";for(JSONObject row:app.chats.messages(active(),1000)){if(row.optString("id").equals(message.optString("id")))break;if(row.optString("role").equals("user"))question=row.optString("text");}return question;}
    void threads(){threads(0,false,new LinkedHashSet<>());}
    void threads(int offset,boolean selecting,Set<String> selected){
        List<JSONObject> page=app.chats.chats(offset);boolean older=page.size()>50;if(older)page=new ArrayList<>(page.subList(0,50));
        List<JSONObject> rows=page;LinearLayout panel=a.column();panel.setPadding(a.dp(18),a.dp(4),a.dp(18),a.dp(12));
        AlertDialog[] dialog={null};TextView count=a.bold(selected.size()+" selected",14);
        Button remove=a.button("Delete selected",true,()->confirmDelete(dialog[0],selected,false));remove.setEnabled(!selected.isEmpty());remove.setId(R.id.chat_delete_selected);
        if(selecting){a.add(panel,count);a.gap(panel,10);a.paired(panel,a.button("Select page",false,()->{for(JSONObject row:rows)selected.add(row.optString("id"));dialog[0].dismiss();threads(offset,true,selected);}),remove);a.gap(panel,8);}
        else if(!rows.isEmpty()){Button choose=a.button("Select",false,()->{dialog[0].dismiss();threads(offset,true,selected);});choose.setId(R.id.chat_select);Button all=a.button("Delete all",false,()->confirmDelete(dialog[0],selected,true));all.setId(R.id.chat_delete_all);a.paired(panel,choose,all);a.gap(panel,12);}
        if(rows.isEmpty()){LinearLayout empty=a.card();a.add(empty,a.bold("No conversations yet",18));a.gap(empty,8);a.add(empty,a.text("Send a message to start one.",14,MainActivity.MUTED));a.add(panel,empty);}
        for(JSONObject chat:rows){
            String id=chat.optString("id");LinearLayout card=a.card();boolean current=id.equals(active());card.setBackground(Y2k.sticker(a,current?Y2k.AQUA:Y2k.PAPER,14));
            LinearLayout line=a.row();CheckBox checked=new CheckBox(a);checked.setMinHeight(a.dp(48));checked.setButtonTintList(android.content.res.ColorStateList.valueOf(Y2k.INK));checked.setChecked(selected.contains(id));checked.setContentDescription("Select "+chat.optString("title"));if(selecting)line.addView(checked);
            TextView title=a.bold(chat.optString("title"),16);title.setMaxLines(3);title.setEllipsize(android.text.TextUtils.TruncateAt.END);line.addView(title,new LinearLayout.LayoutParams(0,-2,1));a.add(card,line);a.gap(card,7);a.add(card,a.text((current?"Current · ":"")+a.date(chat.optLong("updated")),12,MainActivity.MUTED));
            checked.setOnCheckedChangeListener((v,on)->{if(on)selected.add(id);else selected.remove(id);count.setText(selected.size()+" selected");remove.setEnabled(!selected.isEmpty());});
            card.setOnClickListener(v->{if(selecting)checked.setChecked(!checked.isChecked());else{dialog[0].dismiss();select(id);}});
            card.setOnLongClickListener(v->{selected.add(id);dialog[0].dismiss();threads(offset,true,selected);return true;});
            card.setContentDescription((current?"Current conversation. ":"")+chat.optString("title"));card.setMinimumHeight(a.dp(64));a.add(panel,card);a.gap(panel,10);
        }
        if(offset>0||older){Button newer=a.button("Newer",false,()->{dialog[0].dismiss();threads(Math.max(0,offset-50),selecting,selected);});newer.setEnabled(offset>0);Button next=a.button("Older",false,()->{dialog[0].dismiss();threads(offset+50,selecting,selected);});next.setEnabled(older);a.paired(panel,newer,next);}
        ScrollView scroll=new ScrollView(a);scroll.addView(panel);AlertDialog.Builder builder=new ThemedDialog.Builder(a).setTitle("Conversations").setView(scroll).setNegativeButton("Close",null);
        if(selecting)builder.setNeutralButton("Done",(d,w)->threads(offset,false,new LinkedHashSet<>()));
        dialog[0]=builder.create();a.featureDialog=dialog[0];dialog[0].show();Y2k.dialogActions(dialog[0]);
    }
    void confirmDelete(AlertDialog parent,Set<String> selection,boolean all){
        Set<String> ids=new LinkedHashSet<>(selection);if(!all&&ids.isEmpty())return;
        AlertDialog confirm=new ThemedDialog.Builder(a).setTitle(all?"Delete all conversations?":"Delete "+ids.size()+" conversation"+(ids.size()==1?"?":"s?"))
            .setMessage("Saved memories are kept. This cannot be undone.")
            .setNegativeButton("Cancel",null).setPositiveButton(all?"Delete all":"Delete",(d,w)->{
                if(app.maintenance){a.toast("Please wait for data deletion to finish.");return;}
                if(!app.chatRunningId.isEmpty()&&(all||ids.contains(app.chatRunningId))){a.toast("Stop the response before deleting this conversation.");return;}
                boolean reset=all?app.chats.exists(app.chatId):ids.contains(app.chatId);
                android.content.SharedPreferences settings=app.getSharedPreferences("settings",0);String pending=settings.getString("new_chat","");boolean clearPending=all?app.chats.exists(pending):ids.contains(pending);
                android.content.SharedPreferences drafts=app.getSharedPreferences("chat_drafts",0);android.content.SharedPreferences.Editor edit=drafts.edit();
                if(all){for(String key:drafts.getAll().keySet())if(app.chats.exists(key))edit.remove(key);app.chats.deleteAll();}else{for(String id:ids)edit.remove(id);app.chats.delete(ids);}edit.apply();
                android.content.SharedPreferences.Editor selectionEdit=settings.edit();if(clearPending)selectionEdit.remove("new_chat");if(reset){app.chatId="";selectionEdit.remove("chat");}selectionEdit.apply();app.chatVersion++;parent.dismiss();a.go("Chat");
            }).create();a.featureDialog=confirm;confirm.show();Y2k.dialogActions(confirm);
    }
    void allMessages(String id){
        List<JSONObject> rows=app.chats.messages(id,1000);LinearLayout panel=a.column();panel.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(12));
        for(JSONObject row:rows){LinearLayout card=a.card();card.setBackground(Y2k.sticker(a,row.optString("role").equals("user")?Y2k.AQUA:Y2k.PAPER,14));a.add(card,a.bold((row.optString("role").equals("user")?"YOU":"LLAMA")+" · "+a.date(row.optLong("created")),11));a.gap(card,8);TextView body=a.text(row.optString("text"),15,MainActivity.INK);body.setTextIsSelectable(true);a.add(card,body);a.add(panel,card);a.gap(panel,12);}
        ScrollView scroll=new ScrollView(a);scroll.addView(panel);a.featureDialog=new ThemedDialog.Builder(a).setTitle("Past messages").setView(scroll).setPositiveButton("Close",null).show();
    }
}
