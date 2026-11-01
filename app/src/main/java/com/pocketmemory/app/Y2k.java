package com.pocketmemory.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Static canvas artwork and short native feedback. No image assets, render loop or extra library. */
public final class Y2k {
    public static final int INK=0xff222025,PINK=0xffff88d1,AQUA=0xff72e5d6,YELLOW=0xfff8ef72,LILAC=0xffdcc5fa,PAPER=0xfffdfbff,BG=0xffefedf2,ERROR=0xffa92043;
    static int dp(Context c,float value){return Math.round(c.getResources().getDisplayMetrics().density*value);}
    static GradientDrawable tile(Context c,int fill,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(c,radius));d.setStroke(dp(c,1.5f),INK);return d;}
    public static Drawable sticker(Context c,int fill,int radius){GradientDrawable shadow=tile(c,INK,radius),face=tile(c,fill,radius);LayerDrawable layers=new LayerDrawable(new Drawable[]{shadow,face});int offset=dp(c,3);layers.setLayerInset(0,offset,offset,0,0);layers.setLayerInset(1,0,0,offset,offset);return layers;}
    public static Drawable grid(Context c){return new Grid(c);}
    /** Use for every app-owned action, including new dialogs. Preserve click listeners and IDs. */
    public static void styleButton(Button button,int fill){
        Context c=button.getContext();StateListDrawable faces=new StateListDrawable();
        faces.addState(new int[]{-android.R.attr.state_enabled},sticker(c,0xffe7e1ed,12));
        faces.addState(new int[]{},sticker(c,fill,12));
        button.setBackgroundTintList(null);button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x28222025),faces,null));
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff6b6371,INK}));
        button.setAllCaps(false);button.setTypeface(Typeface.DEFAULT,Typeface.BOLD);button.setTextSize(14);
        button.setMinHeight(dp(c,48));button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(dp(c,12),dp(c,7),dp(c,15),dp(c,10));press(button);
        // Restyling an already-enabled view must initialise the wrapper as well as its face.
        button.refreshDrawableState();Drawable background=button.getBackground();background.setState(button.getDrawableState());
        // Framework background tint mutation can clone the nested selector during setBackground.
        if(background instanceof RippleDrawable)((RippleDrawable)background).getDrawable(0).setState(button.getDrawableState());
    }
    public static TextView badge(Context c,String label,int fill){
        TextView badge=new TextView(c);badge.setText(label);badge.setTextColor(INK);badge.setTextSize(10);badge.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        badge.setPadding(dp(c,10),dp(c,6),dp(c,13),dp(c,9));badge.setBackground(sticker(c,fill,9));return badge;
    }
    /** Compact title artwork; decorative icons do not duplicate TalkBack's title. */
    public static LinearLayout dialogTitle(Context c,String icon,String title,int accent){
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(c,20),dp(c,18),dp(c,20),dp(c,12));
        TextView mark=new TextView(c);mark.setText(icon);mark.setTextSize(21);mark.setGravity(Gravity.CENTER);mark.setBackground(sticker(c,accent,10));mark.setRotation(-5);mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconSize=new LinearLayout.LayoutParams(dp(c,42),dp(c,42));iconSize.setMargins(0,0,dp(c,12),0);row.addView(mark,iconSize);
        TextView heading=new TextView(c);heading.setText(title);heading.setTextColor(INK);heading.setTextSize(20);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);heading.setAccessibilityHeading(true);heading.setMaxLines(3);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(heading,new LinearLayout.LayoutParams(0,-2,1));return row;
    }
    public static void dialogActions(android.app.AlertDialog dialog){
        ViewGroup footer=null;
        for(int which:new int[]{android.app.AlertDialog.BUTTON_POSITIVE,android.app.AlertDialog.BUTTON_NEGATIVE,android.app.AlertDialog.BUTTON_NEUTRAL}){
            Button button=dialog.getButton(which);if(button!=null){styleButton(button,which==android.app.AlertDialog.BUTTON_POSITIVE?PINK:which==android.app.AlertDialog.BUTTON_NEUTRAL?AQUA:PAPER);button.setTextSize(13);if(button.getParent() instanceof ViewGroup)footer=(ViewGroup)button.getParent();ViewGroup.LayoutParams layout=button.getLayoutParams();if(layout instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams margins=(ViewGroup.MarginLayoutParams)layout;margins.setMarginStart(dp(button.getContext(),4));margins.setMarginEnd(dp(button.getContext(),4));button.setLayoutParams(margins);}}
        }
        if(footer!=null){Context c=footer.getContext();footer.setPadding(dp(c,8),dp(c,8),dp(c,8),dp(c,12));footer.setClipChildren(false);}
    }
    static boolean motion(){return ValueAnimator.areAnimatorsEnabled();}
    static int tabIndex(String screen){return java.util.List.of("Today","Memory","Chat","Writing","Models").indexOf("Ask".equals(screen)?"Memory":screen);}
    public static void enterTab(View content,int direction){
        content.animate().cancel();content.setAlpha(1);content.setTranslationX(0);
        if(!motion())return;
        content.setAlpha(.78f);content.setTranslationX(dp(content.getContext(),12)*direction);
        content.animate().alpha(1).translationX(0).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }
    public static void press(Button b){b.setStateListAnimator(null);b.setElevation(0);b.setOnTouchListener((view,event)->{if(motion()){if(event.getActionMasked()==MotionEvent.ACTION_DOWN)view.animate().scaleX(.965f).scaleY(.965f).translationY(dp(view.getContext(),2)).setDuration(85).start();else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)view.animate().scaleX(1).scaleY(1).translationY(0).setDuration(130).start();}return false;});}
    public static void switchColors(Switch control){control.setThumbTintList(ColorStateList.valueOf(INK));control.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{AQUA,0xffc8c0ce}));}
    public static String emoji(String kind,String title){
        if("water".equals(kind))return "💧";if("journal".equals(kind))return "📓";String value=title==null?"":title;
        for(String icon:new String[]{"⏰","💊","📚","🎂","🎯","💧","📓","🌿"})if(value.startsWith(icon))return icon;
        String lower=value.toLowerCase(Locale.ROOT);if(lower.contains("birthday"))return "🎂";if(lower.contains("medicine")||lower.contains("pill"))return "💊";if(lower.contains("study")||lower.contains("read"))return "📚";return "⏰";
    }
    public static String reminderLabel(String kind,String title){String icon=emoji(kind,title);return title.startsWith(icon)?title:icon+" "+title;}
    public static String withEmoji(String icon,String title){String value=title.trim();for(String previous:new String[]{"⏰","💊","📚","🎂","🎯","💧","📓","🌿"})if(value.startsWith(previous)){value=value.substring(previous.length()).trim();break;}return icon+" "+value;}
    public static void waterPop(Activity activity,int ml){
        stickerPop(activity,"💧 +"+ml+" ml",YELLOW);
    }
    public static void stickerPop(Activity activity,String label,int fill){
        if(!motion())return;FrameLayout host=activity.findViewById(android.R.id.content);if(host==null)return;TextView badge=new TextView(activity);badge.setText(label);badge.setTextColor(INK);badge.setTextSize(18);badge.setTypeface(Typeface.DEFAULT,Typeface.BOLD);badge.setPadding(dp(activity,14),dp(activity,9),dp(activity,14),dp(activity,9));badge.setBackground(sticker(activity,fill,20));badge.setRotation(-6);badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER_HORIZONTAL|Gravity.TOP);p.topMargin=host.getHeight()/2;host.addView(badge,p);badge.animate().translationY(-dp(activity,55)).alpha(0).setStartDelay(180).setDuration(650).withEndAction(()->host.removeView(badge)).start();
    }
    private static final class Grid extends Drawable {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final float step;final RectF r=new RectF();
        Grid(Context c){step=dp(c,29);}
        @Override public void draw(Canvas canvas){Rect b=getBounds();canvas.drawColor(BG);paint.setShader(new RadialGradient(b.right,b.top+step*4,step*4,0x55ff88d1,0x00ff88d1,Shader.TileMode.CLAMP));canvas.drawRect(b,paint);paint.setShader(new RadialGradient(b.left,b.top+step*17,step*4,0x5072e5d6,0x0072e5d6,Shader.TileMode.CLAMP));canvas.drawRect(b,paint);paint.setShader(null);paint.setColor(0x20918a99);paint.setStrokeWidth(1);for(float x=b.left;x<b.right;x+=step)canvas.drawLine(x,b.top,x,b.bottom,paint);for(float y=b.top;y<b.bottom;y+=step)canvas.drawLine(b.left,y,b.right,y,paint);}
        @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter filter){}@Override public int getOpacity(){return PixelFormat.OPAQUE;}
    }
}
