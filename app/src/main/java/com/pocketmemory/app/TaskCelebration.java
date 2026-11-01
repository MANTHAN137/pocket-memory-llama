package com.pocketmemory.app;

import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;

/** A short check stroke and sixteen pastel confetti pieces; never blocks a tap or opens a modal. */
final class TaskCelebration extends View {
    final ViewGroup host;final String label;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final Path check=new Path(),segment=new Path();final PathMeasure pathMeasure=new PathMeasure();
    final Runnable finishLater=this::finish;final Drawable sticker;ValueAnimator animator;float progress;boolean finished;
    static void show(ViewGroup host,boolean allDone){
        if(host==null)return;
        if(!host.isAttachedToWindow()||host.getWidth()==0){
            host.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){@Override public boolean onPreDraw(){host.getViewTreeObserver().removeOnPreDrawListener(this);show(host,allDone);return true;}});return;
        }
        Object previous=host.getTag(R.id.task_celebration);if(previous instanceof TaskCelebration)((TaskCelebration)previous).finish();
        TaskCelebration view=new TaskCelebration(host,allDone);host.setTag(R.id.task_celebration,view);view.layout(0,0,host.getWidth(),host.getHeight());host.getOverlay().add(view);
        host.performHapticFeedback(HapticFeedbackConstants.CONFIRM);host.announceForAccessibility(allDone?"All tasks complete. Nice work!":"Task complete. Nice work!");
        if(Y2k.motion()){view.animator=ValueAnimator.ofFloat(0,1);view.animator.setDuration(900);view.animator.addUpdateListener(a->{view.progress=(float)a.getAnimatedValue();view.invalidate();});view.animator.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){view.finish();}});view.animator.start();}
        else{view.progress=.55f;view.invalidate();view.postDelayed(view.finishLater,900);}
    }
    TaskCelebration(ViewGroup host,boolean allDone){super(host.getContext());this.host=host;label=allDone?"All done!":"Nice work!";sticker=Y2k.sticker(getContext(),Y2k.YELLOW,18);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(false);}
    float dp(float value){return Y2k.dp(getContext(),value);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float p=progress,cx=getWidth()/2f,cy=getHeight()*.46f;
        if(Y2k.motion()){
            int[] colors={Y2k.AQUA,Y2k.PINK,Y2k.LILAC,Y2k.YELLOW};float travel=1-(float)Math.pow(1-Math.min(1,p*1.5f),3);
            for(int i=0;i<16;i++){double angle=i*Math.PI*2/16;float r=dp(40+((i*29)%68))*travel,x=cx+(float)Math.cos(angle)*r,y=cy+(float)Math.sin(angle)*r+dp(45)*p*p;paint.setColor(colors[i%4]);paint.setAlpha((int)(255*Math.min(1,(1-p)*3)));canvas.save();canvas.translate(x,y);canvas.rotate(i*37+p*100);canvas.drawRoundRect(-dp(3),-dp(6),dp(3),dp(6),dp(2),dp(2),paint);canvas.restore();}
        }
        paint.setAlpha(255);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(16*getResources().getDisplayMetrics().scaledDensity);float width=Math.min(getWidth()-dp(24),paint.measureText(label)+dp(78)),height=Math.max(dp(60),paint.getTextSize()+dp(30));
        float alpha=Y2k.motion()?Math.min(1,p*7)*Math.min(1,(1-p)*6):1;int save=canvas.saveLayerAlpha(0,0,getWidth(),getHeight(),(int)(255*alpha));
        float scale=Y2k.motion()?1-.12f*(float)Math.exp(-p*16)* (float)Math.cos(p*24):1;canvas.scale(scale,scale,cx,cy);sticker.setBounds((int)(cx-width/2),(int)(cy-height/2),(int)(cx+width/2),(int)(cy+height/2));sticker.draw(canvas);
        float x=cx-width/2+dp(19);paint.setColor(Y2k.INK);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);check.reset();check.moveTo(x,cy);check.lineTo(x+dp(7),cy+dp(7));check.lineTo(x+dp(21),cy-dp(9));segment.reset();pathMeasure.setPath(check,false);pathMeasure.getSegment(0,pathMeasure.getLength()*Math.min(1,p*5),segment,true);canvas.drawPath(segment,paint);paint.setStyle(Paint.Style.FILL);canvas.drawText(label,x+dp(34),cy-(paint.ascent()+paint.descent())/2-dp(1),paint);canvas.restoreToCount(save);
    }
    void finish(){if(finished)return;finished=true;if(animator!=null)animator.cancel();removeCallbacks(finishLater);host.getOverlay().remove(this);if(host.getTag(R.id.task_celebration)==this)host.setTag(R.id.task_celebration,null);}
    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
}
