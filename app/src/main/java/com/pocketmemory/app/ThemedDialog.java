package com.pocketmemory.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.*;
import android.widget.*;

/** Shared sticker titles and menu rows. Keeps native dialog focus, scrolling and callbacks. */
final class ThemedDialog {
    static final class Builder extends AlertDialog.Builder {
        Builder(Context context){super(context);}
        @Override public Builder setTitle(CharSequence title){
            super.setTitle(title);
            String value=title==null?"":title.toString(),lower=value.toLowerCase(java.util.Locale.ROOT);
            String icon=lower.contains("water")?"💧":lower.contains("reminder")||lower.contains("time")?"⏰":lower.contains("chat")||lower.contains("conversation")||lower.contains("message")||lower.contains("reply")?"💬":lower.contains("privacy")||lower.contains("erase")||lower.contains("password")?"🔒":lower.contains("model")?"✨":lower.contains("backup")?"📦":lower.contains("photo")?"📷":lower.contains("writ")||lower.contains("text")||lower.contains("note")?"✍️":"✦";
            super.setCustomTitle(Y2k.dialogTitle(getContext(),icon,value,Y2k.LILAC));return this;
        }
        @Override public Builder setTitle(int title){return setTitle(getContext().getText(title));}
        @Override public Builder setItems(CharSequence[] items,DialogInterface.OnClickListener listener){
            super.setAdapter(new ArrayAdapter<CharSequence>(getContext(),android.R.layout.simple_list_item_1,items){
                @Override public View getView(int position,View reused,ViewGroup parent){
                    Context c=getContext();FrameLayout frame;
                    TextView label;
                    if(reused instanceof FrameLayout){frame=(FrameLayout)reused;label=(TextView)frame.getChildAt(0);}
                    else{frame=new FrameLayout(c);frame.setPadding(Y2k.dp(c,18),Y2k.dp(c,4),Y2k.dp(c,18),Y2k.dp(c,4));label=new TextView(c);label.setTextColor(Y2k.INK);label.setTextSize(15);label.setGravity(Gravity.CENTER_VERTICAL);label.setMinHeight(Y2k.dp(c,52));label.setPadding(Y2k.dp(c,15),Y2k.dp(c,12),Y2k.dp(c,18),Y2k.dp(c,15));label.setBackground(Y2k.sticker(c,Y2k.PAPER,12));frame.addView(label,new FrameLayout.LayoutParams(-1,-2));}
                    label.setText(getItem(position));return frame;
                }
            },listener);return this;
        }
    }
    private ThemedDialog(){}
}
