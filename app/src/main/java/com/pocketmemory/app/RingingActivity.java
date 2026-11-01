package com.pocketmemory.app;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

public final class RingingActivity extends Activity {
    String eventId;MemoryApp app;boolean registered;
    final BroadcastReceiver changed=new BroadcastReceiver(){public void onReceive(Context c,Intent i){check();}};
    @Override public void onCreate(Bundle state){super.onCreate(state);setShowWhenLocked(true);setTurnScreenOn(true);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);app=(MemoryApp)getApplication();eventId=getIntent().getStringExtra("event");getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->perform(Reminders.SNOOZE));render();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);eventId=intent.getStringExtra("event");render();}
    @Override protected void onStart(){super.onStart();app.visibleActivities++;registerReceiver(changed,new IntentFilter(Reminders.CHANGED),Context.RECEIVER_NOT_EXPORTED);registered=true;check();}
    @Override protected void onStop(){if(registered){unregisterReceiver(changed);registered=false;}app.visibleActivities--;super.onStop();}
    void check(){JSONObject e=eventId==null?null:app.life.event(eventId);if(e==null||!Set.of("pending","ringing").contains(e.optString("state")))finish();}
    int dp(int n){return (int)(getResources().getDisplayMetrics().density*n);}
    TextView label(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER);return t;}
    void gap(LinearLayout l,int n){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    Button action(String title,boolean coral,Runnable run){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(18);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(MainActivity.INK);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x28222025),Y2k.sticker(this,coral?Y2k.PINK:Y2k.AQUA,18),null));Y2k.press(b);b.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);run.run();});return b;}
    void perform(String action){Reminders.act(this,eventId,action);finish();}
    void render(){JSONObject event=eventId==null?null:app.life.event(eventId);if(event==null){finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_HORIZONTAL);root.setPadding(dp(28),dp(30),dp(28),dp(28));root.setBackground(Y2k.grid(this));root.setOnApplyWindowInsetsListener((v,i)->{var bars=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(28)+bars.left,dp(24)+bars.top,dp(28)+bars.right,dp(24)+bars.bottom);return i;});
        add(root,label("✳  P O C K E T   M E M O R Y  ✳",12,MainActivity.INK));gap(root,24);View topSpace=new View(this);root.addView(topSpace,new LinearLayout.LayoutParams(1,0,1));
        TextView sticker=label(Y2k.emoji(event.optString("kind"),event.optString("title")),52,MainActivity.INK);sticker.setBackground(Y2k.sticker(this,Y2k.YELLOW,58));sticker.setRotation(-7);root.addView(sticker,new LinearLayout.LayoutParams(dp(108),dp(108)));gap(root,18);
        TextView clock=label(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()),48,MainActivity.INK);clock.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));add(root,clock);gap(root,14);
        TextView title=label(event.optString("title"),28,MainActivity.INK);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setBackground(Y2k.sticker(this,MainActivity.PAPER,10));title.setPadding(dp(12),dp(10),dp(15),dp(13));title.setRotation(-1.5f);title.setId(R.id.alarm_title);add(root,title);gap(root,16);add(root,label(event.optString("body").isBlank()?event.optString("kind").equals("water")?"A small pause for yourself.":"One thing to make time for.":event.optString("body"),17,MainActivity.INK));gap(root,22);add(root,label("YOU’VE GOT THIS. ✨",12,MainActivity.INK));root.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));
        if(event.optString("kind").equals("water")){Button log=action("💧 Log "+app.life.setting("water_cup",250)+" ml",true,()->perform(Reminders.WATER));add(root,log);gap(root,12);}
        Button dismiss=action("✓ Dismiss",true,()->perform(Reminders.DISMISS));dismiss.setId(R.id.alarm_dismiss);add(root,dismiss);gap(root,12);Button snooze=action("😴 Snooze · 10 minutes",false,()->perform(Reminders.SNOOZE));snooze.setId(R.id.alarm_snooze);add(root,snooze);gap(root,16);add(root,label("Ringing stops automatically after 2 minutes",11,MainActivity.MUTED));setContentView(root);root.requestApplyInsets();root.post(()->{WindowInsetsController controller=root.getWindowInsetsController();if(controller!=null)controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);});
    }
    void add(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(-1,v instanceof Button?dp(62):-2));}
}
