package com.pocketmemory.app;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.text.InputFilter;
import android.text.InputType;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;

final class ModelAccessUi {
    final MainActivity a;
    ModelAccessUi(MainActivity a){this.a=a;}
    void code(){
        LinearLayout form=a.column();form.setPadding(a.dp(20),a.dp(8),a.dp(20),a.dp(16));
        a.add(form,a.text("A gift for your pocket. Enter your code to unlock premium downloads on this phone.",14,MainActivity.INK));a.gap(form,14);
        EditText field=a.input("Unlock code",false);field.setId(R.id.model_unlock_input);field.setContentDescription("Unlock code");field.setInputType(InputType.TYPE_CLASS_NUMBER);field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});field.setGravity(Gravity.CENTER);field.setTextSize(24);field.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);field.setLetterSpacing(.06f);a.add(form,field);
        TextView error=a.text("",12,Y2k.ERROR);error.setId(R.id.model_unlock_error);error.setVisibility(View.GONE);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);a.add(form,error);
        ScrollView scroll=new ScrollView(a);scroll.addView(form);
        AlertDialog dialog=new ThemedDialog.Builder(a).setTitle("Enter unlock code").setCustomTitle(Y2k.dialogTitle(a,"🔑","Enter unlock code",Y2k.YELLOW)).setView(scroll).setPositiveButton("Unlock",null).setNegativeButton("Cancel",null).create();a.featureDialog=dialog;
        dialog.setOnShowListener(d->{Y2k.dialogActions(dialog);Button unlock=dialog.getButton(AlertDialog.BUTTON_POSITIVE);unlock.setId(R.id.model_unlock_apply);unlock.setOnClickListener(v->{
            if(a.app.maintenance)return;String entered=field.getText().toString();unlock.setEnabled(false);error.setText("");error.setVisibility(View.GONE);
            a.app.files.execute(()->{try{a.app.access.redeem(entered,System.currentTimeMillis());a.app.main.post(()->{if(a.destroyed)return;dialog.dismiss();a.modelFilter="Paid";a.modelSearch="";a.go("Models");Y2k.stickerPop(a,"🔓 Unlocked!",Y2k.AQUA);a.toast("Unlocked. Choose a model to download.");});}
                catch(Exception e){a.app.main.post(()->{if(!a.destroyed){error.setText(MainActivity.friendly(e));error.setVisibility(View.VISIBLE);unlock.setEnabled(true);}});}});
        });});dialog.show();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    boolean compatible(Catalog.Model model){
        if(!model.available()){a.toast(model.unavailableReason);return false;}
        ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();a.getSystemService(ActivityManager.class).getMemoryInfo(info);
        if(info.lowMemory){a.toast("Phone memory is low. Close other apps before downloading Llama.");return false;}
        if(!model.installed(a)&&new android.os.StatFs(Catalog.directory(a).getPath()).getAvailableBytes()<Math.max(0,model.bytes-model.part(a).length())+100_000_000L){a.toast("Free some storage before downloading Llama.");return false;}
        if(!enoughRam(info.totalMem,model.minimumRamGb)){
            a.featureDialog=new ThemedDialog.Builder(a).setTitle("Try Llama on this phone?").setMessage("This phone has less memory than the recommended guide. Replies may be slow or the model may not fit.").setNegativeButton("Cancel",null).setPositiveButton("Download anyway",(d,w)->a.startForegroundService(new android.content.Intent(a,ModelDownloadService.class).putExtra("id",model.id))).show();return false;
        }
        return true;
    }
    static boolean enoughRam(long total,int minimum){return minimum>0&&total>=minimum*850_000_000L;}
    void options(Catalog.Model model){
        if(!compatible(model))return;
        if(!ModelTerms.accepted(a,model)){ModelTerms.ensure(a,model,()->options(model));return;}
        a.app.billing.start();LinearLayout form=a.column();form.setPadding(a.dp(20),a.dp(8),a.dp(20),a.dp(16));
        LinearLayout tag=a.row();tag.addView(Y2k.badge(a,"★ ONE-TIME UNLOCK",Y2k.LILAC));a.add(form,tag);a.gap(form,12);a.add(form,a.bold(model.name,18));a.gap(form,10);a.add(form,a.text("Pay once for this model in Pocket Memory. Re-download anytime. Upstream weights remain available separately under their own licence.",14,MainActivity.INK));a.gap(form,8);
        a.add(form,a.text(model.deviceDetails(),12,MainActivity.MUTED));a.gap(form,12);
        Button buy=a.button("",true,()->{a.featureDialog.dismiss();a.app.billing.buy(a,model);});Runnable priceUpdate=()->{if(a.destroyed)return;boolean ready=a.app.billing.canBuy(model);buy.setEnabled(ready);buy.setText(ready?"Unlock · "+a.app.billing.price(model):a.app.billing.configured()?"Google Play unavailable":"₹21 · Coming soon");};priceUpdate.run();a.app.listeners.add(priceUpdate);a.add(form,buy);a.gap(form,10);
        a.add(form,a.button("🔑 I have an unlock code",false,()->{a.featureDialog.dismiss();code();}));a.gap(form,8);
        a.add(form,a.button("↻ Restore purchases",false,()->{a.app.billing.start();a.toast(a.app.billing.configured()?"Checking Google Play…":"Play purchases are not configured in this preview.");}));
        ScrollView scroll=new ScrollView(a);scroll.addView(form);AlertDialog dialog=new ThemedDialog.Builder(a).setTitle("Unlock model").setCustomTitle(Y2k.dialogTitle(a,"✨","Unlock model",Y2k.LILAC)).setView(scroll).setNegativeButton("Close",null).create();a.featureDialog=dialog;dialog.setOnDismissListener(d->a.app.listeners.remove(priceUpdate));dialog.setOnShowListener(d->Y2k.dialogActions(dialog));dialog.show();
    }
}
