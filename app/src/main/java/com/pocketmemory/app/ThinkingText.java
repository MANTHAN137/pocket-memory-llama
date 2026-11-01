package com.pocketmemory.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.widget.TextView;

/** One visible-only clock drives a pastel sticker bot, thought dots and a gentle text shimmer. */
@android.annotation.SuppressLint("AppCompatCustomView") // Native Activity/Theme.Material throughout this app.
final class ThinkingText extends TextView {
    boolean thinking;ValueAnimator animator;LinearGradient gradient;final Matrix matrix=new Matrix();
    final Paint botPaint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF shape=new RectF();final Path mouth=new Path();float phase;
    ThinkingText(Context context){super(context);setTextSize(16);setTextColor(Y2k.INK);setLineSpacing(Y2k.dp(context,3),1);setGravity(android.view.Gravity.CENTER_VERTICAL);}
    void thinking(boolean value){
        if(thinking==value)return;thinking=value;
        setPadding(value?dp(82):0,value?dp(8):0,0,value?dp(8):0);setMinHeight(value?dp(88):0);requestLayout();update();
    }
    int dp(float value){return Y2k.dp(getContext(),value);}
    void update(){
        boolean run=thinking&&isAttachedToWindow()&&getWindowVisibility()==View.VISIBLE&&isShown()&&Y2k.motion();
        if(!run){if(animator!=null){animator.cancel();animator=null;}phase=0;getPaint().setShader(null);invalidate();return;}
        if(animator!=null)return;
        float span=Math.max(1,getPaint().measureText("Thinking…"));gradient=new LinearGradient(0,0,span,0,new int[]{0xff69616f,Y2k.INK,0xff69616f},new float[]{0,.5f,1},Shader.TileMode.MIRROR);getPaint().setShader(gradient);
        animator=ValueAnimator.ofFloat(0,1);animator.setDuration(4800);animator.setRepeatCount(ValueAnimator.INFINITE);animator.setInterpolator(new android.view.animation.LinearInterpolator());
        animator.addUpdateListener(a->{if(!Y2k.motion()){update();return;}phase=(float)a.getAnimatedValue();matrix.setTranslate((phase*4%1)*span*2-span,0);gradient.setLocalMatrix(matrix);invalidate();});animator.start();
    }
    int blend(int from,int to,float t){return Color.rgb(Math.round(Color.red(from)+(Color.red(to)-Color.red(from))*t),Math.round(Color.green(from)+(Color.green(to)-Color.green(from))*t),Math.round(Color.blue(from)+(Color.blue(to)-Color.blue(from))*t));}
    int glow(){float p=phase*3;return p<1?blend(Y2k.AQUA,Y2k.LILAC,p):p<2?blend(Y2k.LILAC,Y2k.PINK,p-1):blend(Y2k.PINK,Y2k.AQUA,p-2);}
    void round(Canvas c,float left,float top,float right,float bottom,float radius,int fill,boolean outline){shape.set(left,top,right,bottom);botPaint.setStyle(Paint.Style.FILL);botPaint.setColor(fill);c.drawRoundRect(shape,radius,radius,botPaint);if(outline){botPaint.setStyle(Paint.Style.STROKE);botPaint.setStrokeWidth(1.6f);botPaint.setColor(Y2k.INK);c.drawRoundRect(shape,radius,radius,botPaint);botPaint.setStyle(Paint.Style.FILL);}}
    @Override protected void onDraw(Canvas canvas){
        if(thinking){
            boolean moving=animator!=null;float wave=(float)Math.sin(phase*Math.PI*2),look=moving?(float)Math.sin(phase*Math.PI*4)*1.7f:0;
            float blink=moving?1-.92f*(float)Math.pow(Math.max(0,Math.cos((phase-.44f)*Math.PI*2)),30):1;
            canvas.save();canvas.translate(dp(39),getHeight()/2f);canvas.scale(getResources().getDisplayMetrics().density,getResources().getDisplayMetrics().density);
            botPaint.setColor(glow());botPaint.setAlpha(58);canvas.drawCircle(0,0,34+(moving?wave*2:0),botPaint);botPaint.setAlpha(255);
            canvas.save();canvas.translate(0,moving?wave*1.8f:0);canvas.rotate(moving?wave*4:0);
            round(canvas,-11,13,14,27,5,Y2k.INK,false);round(canvas,-13,11,11,25,5,Y2k.LILAC,true);
            round(canvas,-25,-7,-18,5,3,Y2k.YELLOW,true);round(canvas,18,-7,25,5,3,Y2k.PINK,true);
            botPaint.setColor(Y2k.INK);botPaint.setStrokeWidth(1.6f);canvas.drawLine(0,-24,0,-17,botPaint);botPaint.setColor(Y2k.YELLOW);canvas.drawCircle(0,-26,3.6f,botPaint);botPaint.setStyle(Paint.Style.STROKE);botPaint.setColor(Y2k.INK);canvas.drawCircle(0,-26,3.6f,botPaint);botPaint.setStyle(Paint.Style.FILL);
            round(canvas,-18,-15,22,15,10,Y2k.INK,false);round(canvas,-21,-18,19,12,10,Y2k.AQUA,true);
            botPaint.setColor(Y2k.INK);shape.set(-11+look,-7-3*blink,-6+look,-7+3*blink);canvas.drawOval(shape,botPaint);shape.set(5+look,-7-3*blink,10+look,-7+3*blink);canvas.drawOval(shape,botPaint);
            botPaint.setColor(Y2k.PINK);botPaint.setAlpha(180);canvas.drawCircle(-14,2,2.7f,botPaint);canvas.drawCircle(12,2,2.7f,botPaint);botPaint.setAlpha(255);
            mouth.reset();mouth.moveTo(-5,1);mouth.quadTo(-1,5,3,1);botPaint.setColor(Y2k.INK);botPaint.setStyle(Paint.Style.STROKE);botPaint.setStrokeWidth(1.4f);botPaint.setStrokeCap(Paint.Cap.ROUND);canvas.drawPath(mouth,botPaint);botPaint.setStyle(Paint.Style.FILL);canvas.restore();
            for(int i=0;i<3;i++){float bounce=moving?(float)Math.sin(phase*Math.PI*8-i*.8f):0;botPaint.setColor(i==0?Y2k.PINK:i==1?Y2k.YELLOW:Y2k.LILAC);canvas.drawCircle(18+i*6,-28-bounce*2,2.1f+bounce*.35f,botPaint);}
            canvas.restore();
        }
        super.onDraw(canvas);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();update();}
    @Override protected void onDetachedFromWindow(){if(animator!=null){animator.cancel();animator=null;}getPaint().setShader(null);super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);update();}
    @Override public void onVisibilityAggregated(boolean visible){super.onVisibilityAggregated(visible);update();}
}
