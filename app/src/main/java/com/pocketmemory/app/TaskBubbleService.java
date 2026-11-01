package com.pocketmemory.app;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;

/** User-enabled overlay only. No background boot start, polling, network, model or wake lock. */
public final class TaskBubbleService extends Service {
    static volatile TaskBubbleService instance;
    final Handler main=new Handler(Looper.getMainLooper());WindowManager windows;Bubble bubble;FrameLayout panel;WindowManager.LayoutParams bubbleParams,panelParams;int page;boolean stopping;
    AppOpsManager ops;AppOpsManager.OnOpChangedListener permissionListener;
    final BroadcastReceiver lock=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(Intent.ACTION_SCREEN_OFF.equals(i.getAction()))removeWindows();else refresh();}};
    static void refreshIfRunning(){TaskBubbleService current=instance;if(current!=null)current.main.post(current::refresh);}
    MemoryApp app(){return (MemoryApp)getApplication();}int dp(float n){return Y2k.dp(this,n);}
    @Override public void onCreate(){super.onCreate();instance=this;windows=getSystemService(WindowManager.class);IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);registerReceiver(lock,filter,Context.RECEIVER_NOT_EXPORTED);
        ops=getSystemService(AppOpsManager.class);permissionListener=(op,pkg)->main.post(()->{if(!TaskAssist.overlayAllowed(this))stopSelf();});ops.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,getPackageName(),permissionListener);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!allowed()||app().life.pendingCount()==0){stopSelf();return START_NOT_STICKY;}
        try{startForeground(TaskAssist.FLOAT_ID,TaskAssist.notification(this,true));refresh();}catch(RuntimeException denied){stopSelf();}
        return START_NOT_STICKY;
    }
    boolean allowed(){return !app().maintenance&&TaskAssist.mode(this).equals(TaskAssist.FLOATING)&&TaskAssist.overlayAllowed(this)&&Reminders.notifications(this);}
    Rect safe(){WindowMetrics metrics=windows.getCurrentWindowMetrics();Rect bounds=new Rect(metrics.getBounds());Insets insets=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());bounds.left+=insets.left+dp(8);bounds.top+=insets.top+dp(8);bounds.right-=insets.right+dp(8);bounds.bottom-=insets.bottom+dp(8);return bounds;}
    void refresh(){
        if(stopping)return;if(!allowed()){stopSelf();return;}int count=app().life.pendingCount();
        if(count==0){if(panel!=null){renderPanel();main.removeCallbacks(stopWhenEmpty);main.postDelayed(stopWhenEmpty,1100);}else stopSelf();return;}
        main.removeCallbacks(stopWhenEmpty);
        if(getSystemService(KeyguardManager.class).isDeviceLocked()){removeWindows();return;}
        try{
            if(bubble==null){Rect area=safe();bubble=new Bubble();bubbleParams=new WindowManager.LayoutParams(dp(72),dp(72),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);bubbleParams.gravity=Gravity.TOP|Gravity.LEFT;bubbleParams.x=TaskAssist.prefs(this).getInt("x",area.right-dp(72));bubbleParams.y=TaskAssist.prefs(this).getInt("y",area.centerY());clampBubble();windows.addView(bubble,bubbleParams);}
            bubble.count.setText(count>9?"9+":""+count);bubble.setContentDescription(count+" pending tasks. Tap to open, or drag to move.");
            if(panel!=null)renderPanel();getSystemService(NotificationManager.class).notify(TaskAssist.FLOAT_ID,TaskAssist.notification(this,true));
        }catch(RuntimeException denied){stopSelf();}
    }
    final Runnable stopWhenEmpty=()->{if(app().life.pendingCount()==0)stopSelf();else refresh();};
    void clampBubble(){Rect area=safe();bubbleParams.x=Math.max(area.left,Math.min(bubbleParams.x,area.right-dp(72)));bubbleParams.y=Math.max(area.top,Math.min(bubbleParams.y,area.bottom-dp(72)));}
    void togglePanel(){if(panel!=null){closePanel();return;}Rect area=safe();panel=new FrameLayout(this);panel.setId(R.id.task_float_panel);panel.setBackground(Y2k.sticker(this,Y2k.PAPER,18));panel.setPadding(dp(16),dp(16),dp(19),dp(19));
        panelParams=new WindowManager.LayoutParams(Math.min(dp(344),area.width()),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);panelParams.gravity=Gravity.TOP|Gravity.LEFT;panelParams.x=Math.max(area.left,Math.min(bubbleParams.x,area.right-panelParams.width));panelParams.y=Math.max(area.top,Math.min(bubbleParams.y,area.bottom-dp(400)));page=0;renderPanel();
        try{windows.addView(panel,panelParams);panel.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(panel==v){Rect current=safe();int y=Math.max(current.top,Math.min(panelParams.y,current.bottom-v.getHeight()));if(y!=panelParams.y){panelParams.y=y;windows.updateViewLayout(panel,panelParams);}}});}catch(RuntimeException denied){closePanel();stopSelf();}
    }
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    TextView text(String value,int sp,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(sp);v.setTextColor(Y2k.INK);if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);return v;}
    Button button(String label,int color,Runnable action){Button b=new Button(this);b.setText(label);Y2k.styleButton(b,color);b.setOnClickListener(v->action.run());return b;}
    void add(LinearLayout parent,View view){parent.addView(view,new LinearLayout.LayoutParams(-1,-2));}
    void renderPanel(){
        if(panel==null)return;panel.removeAllViews();LinearLayout content=column();LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(text("✓ Your tasks",18,true),new LinearLayout.LayoutParams(0,-2,1));Button close=button("×",Y2k.PAPER,this::closePanel);close.setContentDescription("Collapse task list");heading.addView(close,new LinearLayout.LayoutParams(dp(48),-2));add(content,heading);
        int count=app().life.pendingCount(),size=12,pages=Math.max(1,(count+size-1)/size);page=Math.min(page,pages-1);List<JSONObject> rows=app().life.pendingTasks(size,page*size);
        TextView summary=text(count==0?"All done. Take a little breath. ☀️":count+" left · "+(page+1)+"/"+pages,12,false);summary.setPadding(0,dp(6),0,dp(10));add(content,summary);
        ScrollView scroller=new ScrollView(this){@Override protected void onMeasure(int w,int h){super.onMeasure(w,MeasureSpec.makeMeasureSpec(Math.max(dp(80),(int)(safe().height()*.42)),MeasureSpec.AT_MOST));}};LinearLayout list=column();
        for(JSONObject task:rows){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(6),0,dp(6));CheckBox done=new CheckBox(this);done.setButtonTintList(android.content.res.ColorStateList.valueOf(Y2k.INK));done.setMinHeight(dp(48));done.setMinWidth(dp(48));done.setContentDescription("Mark "+task.optString("title")+" done");row.addView(done);LinearLayout words=column();TextView title=text(task.optString("title"),15,false);title.setMaxLines(3);title.setEllipsize(android.text.TextUtils.TruncateAt.END);add(words,title);if(!task.optString("day").isEmpty())add(words,text(task.optString("day"),11,false));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));done.setOnCheckedChangeListener((v,on)->{if(!on)return;done.setEnabled(false);complete(task);});list.addView(row);}
        scroller.addView(list);add(content,scroller);
        if(pages>1){LinearLayout navigation=new LinearLayout(this);Button previous=button("Previous",Y2k.PAPER,()->{page--;renderPanel();}),next=button("Next",Y2k.PAPER,()->{page++;renderPanel();});previous.setEnabled(page>0);next.setEnabled(page+1<pages);navigation.addView(previous,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=dp(8);navigation.addView(next,p);add(content,navigation);}
        Button open=button("Open Pocket Memory",Y2k.AQUA,()->{Intent intent=new Intent(this,MainActivity.class).putExtra("screen","Today").putExtra("todaySection","Tasks").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(intent);closePanel();});add(content,open);Button stop=button("Stop floating",Y2k.PAPER,()->{TaskAssist.disable(this);sendBroadcast(new Intent(TaskAssist.CHANGED).setPackage(getPackageName()));});LinearLayout.LayoutParams stopSize=new LinearLayout.LayoutParams(-1,-2);stopSize.topMargin=dp(8);content.addView(stop,stopSize);panel.addView(content);
    }
    void complete(JSONObject task){app().alarms.execute(()->{boolean success=false;try{success=TaskAssist.complete(this,task.optString("id"),TaskAssist.token(task));}catch(RuntimeException failure){main.post(()->Toast.makeText(this,"Task could not be saved. Please try again.",Toast.LENGTH_SHORT).show());}boolean completed=success;main.post(()->{refresh();if(completed&&panel!=null)TaskCelebration.show(panel,app().life.pendingCount()==0);});app().taskChanges.incrementAndGet();sendBroadcast(new Intent(TaskAssist.CHANGED).setPackage(getPackageName()));app().changed();});}
    void closePanel(){if(panel!=null){try{windows.removeView(panel);}catch(IllegalArgumentException ignored){}panel=null;}}
    void removeWindows(){closePanel();if(bubble!=null){try{windows.removeView(bubble);}catch(IllegalArgumentException ignored){}bubble=null;}}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);removeWindows();refresh();}
    @Override public void onDestroy(){stopping=true;main.removeCallbacksAndMessages(null);ops.stopWatchingMode(permissionListener);unregisterReceiver(lock);removeWindows();stopForeground(STOP_FOREGROUND_REMOVE);if(instance==this)instance=null;super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
    final class Bubble extends FrameLayout {
        TextView count;float startX,startY;int originalX,originalY;boolean moved;
        Bubble(){super(TaskBubbleService.this);setId(R.id.task_float_button);setClickable(true);setFocusable(true);setClipChildren(false);setBackground(Y2k.sticker(getContext(),Y2k.AQUA,30));TextView check=text("✓",25,true);check.setGravity(Gravity.CENTER);check.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(check,new LayoutParams(-1,-1));count=text("",10,true);count.setGravity(Gravity.CENTER);count.setMinWidth(dp(24));count.setPadding(dp(4),dp(3),dp(4),dp(3));count.setBackground(Y2k.sticker(getContext(),Y2k.YELLOW,10));count.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);LayoutParams badge=new LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);addView(count,badge);setOnClickListener(v->togglePanel());}
        @Override public boolean onTouchEvent(MotionEvent event){switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:startX=event.getRawX();startY=event.getRawY();originalX=bubbleParams.x;originalY=bubbleParams.y;moved=false;return true;
            case MotionEvent.ACTION_MOVE:float dx=event.getRawX()-startX,dy=event.getRawY()-startY;if(Math.hypot(dx,dy)>ViewConfiguration.get(getContext()).getScaledTouchSlop())moved=true;if(moved){closePanel();bubbleParams.x=originalX+(int)dx;bubbleParams.y=originalY+(int)dy;clampBubble();try{windows.updateViewLayout(this,bubbleParams);}catch(RuntimeException denied){stopSelf();}}return true;
            case MotionEvent.ACTION_UP:if(!moved)performClick();else TaskAssist.prefs(getContext()).edit().putInt("x",bubbleParams.x).putInt("y",bubbleParams.y).apply();return true;
            case MotionEvent.ACTION_CANCEL:return true;default:return super.onTouchEvent(event);
        }}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
