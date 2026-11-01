package com.pocketmemory.app;

import android.app.*;
import android.widget.*;
import org.json.*;

/** An in-app preview and explicit submission flow; offline reports remain visibly pending. */
final class ReportUi {
    final MainActivity a;final MemoryApp app;final ReportStore store;
    ReportUi(MainActivity a){this.a=a;app=a.app;store=new ReportStore(a);}
    Button flag(java.util.function.Supplier<String> response,java.util.function.Supplier<String> prompt){return a.quietAction("⚑","Report response",()->compose(response.get(),prompt.get()));}
    void compose(String response,String prompt){
        if(response==null||response.isBlank()){a.toast("Generate a response first.");return;}
        boolean configured=!BuildConfig.REPORTS_URL.isBlank();LinearLayout form=a.column();form.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(14));
        if(!configured){a.add(form,a.text("Reporting isn’t connected yet. Save this report on your phone.",13,MainActivity.MUTED));a.gap(form,10);}
        String[] reasons={"Unsafe content","Hate or harassment","Sexual content","Child safety","Incorrect answer","Privacy concern","Other"};Spinner reason=new Spinner(a);reason.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,reasons));reason.setContentDescription("Reason for reporting");reason.setMinimumHeight(a.dp(48));a.add(form,reason);a.gap(form,10);
        EditText edited=a.input("Response · remove private details",true);edited.setId(R.id.report_response);edited.setText(MainActivity.excerpt(response,7999));edited.setMinLines(3);edited.setMaxLines(6);a.add(form,edited);a.gap(form,8);
        CheckBox include=new CheckBox(a);include.setId(R.id.report_include);include.setText("Include my question");include.setTextColor(MainActivity.INK);include.setMinHeight(a.dp(48));a.add(form,include);
        EditText context=a.input("Question · remove private details",true);context.setId(R.id.report_question);context.setMinLines(2);context.setMaxLines(4);context.setText(prompt==null?"":MainActivity.excerpt(prompt,3999));context.setVisibility(android.view.View.GONE);include.setOnCheckedChangeListener((b,on)->context.setVisibility(on?android.view.View.VISIBLE:android.view.View.GONE));a.add(form,context);a.gap(form,8);
        a.add(form,a.text("When sent, "+BuildConfig.PUBLISHER+" receives the reviewed text, reason, app/safety versions, time and report ID for AI safety review. The server sees your IP. Retention: up to 30 days. Other app content isn’t attached.",12,MainActivity.MUTED));
        CheckBox consent=new CheckBox(a);consent.setId(R.id.report_consent);consent.setText(configured?"I agree to send this report.":"Save this report on this phone.");consent.setTextColor(MainActivity.INK);consent.setMinHeight(a.dp(48));a.add(form,consent);
        ScrollView scroll=new ScrollView(a);scroll.addView(form);AlertDialog dialog=new ThemedDialog.Builder(a).setTitle("Report response").setView(scroll).setPositiveButton(configured?"Send report":"Save locally",null).setNegativeButton("Cancel",null).create();a.featureDialog=dialog;
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!consent.isChecked()){a.toast(configured?"Review the report and agree before sending.":"Confirm to save this report locally.");return;}
            try{JSONObject report=store.create(edited.getText().toString().trim(),include.isChecked()?context.getText().toString().trim():"",reasons[reason.getSelectedItemPosition()]);dialog.dismiss();if(configured)send(report);else a.toast("Saved on this phone. Not sent.");}catch(Exception e){a.toast(MainActivity.friendly(e));}
        }));dialog.show();
    }
    void send(JSONObject report){app.files.execute(()->{try{store.send(report);app.main.post(()->a.toast("Report sent. Reference: "+report.optString("id")));}catch(Exception e){app.main.post(()->a.toast("Delivery was not confirmed. Your report is kept here for a manual retry. "+MainActivity.friendly(e)));}});}
    void history(){JSONArray rows=store.list();String[] labels=new String[rows.length()];for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);labels[i]=(r.optString("state").equals("sent")?"Sent":r.optString("state").equals("unconfirmed")?"Delivery unconfirmed":"Not sent")+" · "+r.optString("reason")+"\n"+a.date(r.optLong("created"));}
        new ThemedDialog.Builder(a).setTitle("Response reports").setMessage(rows.length()==0?"No saved reports. Nothing is sent without your review.":null).setItems(labels,(d,i)->details(rows.optJSONObject(i))).setNegativeButton("Close",null).show();
    }
    void details(JSONObject report){boolean sent=report.optString("state").equals("sent"),attempted=!report.optString("state").equals("pending");AlertDialog.Builder dialog=new ThemedDialog.Builder(a).setTitle(sent?"Sent report":"Report saved locally").setMessage("Reference: "+report.optString("id")+"\nReason: "+report.optString("reason")+"\n\n"+report.optString("response")+(report.optString("prompt").isEmpty()?"":"\n\nIncluded question:\n"+report.optString("prompt"))).setNegativeButton("Close",null);
        if(!sent&&!BuildConfig.REPORTS_URL.isBlank())dialog.setPositiveButton("Review / retry",(d,w)->new ThemedDialog.Builder(a).setTitle("Send this saved report?").setMessage("Only the report shown will be sent to "+BuildConfig.PUBLISHER+". Your other app content is not attached.").setPositiveButton("Send",(x,y)->send(report)).setNegativeButton("Cancel",null).show());
        dialog.setNeutralButton(attempted?"Delete submitted report":"Delete local report",(d,w)->new ThemedDialog.Builder(a).setTitle("Delete this report?").setMessage(attempted?"Requests deletion from the developer and removes the local copy after confirmation. Internet is required. You can also contact "+BuildConfig.SUPPORT_EMAIL+" with the report reference.":"Removes this unsent report from this phone.").setPositiveButton("Delete",(x,y)->app.files.execute(()->{try{store.deleteRemote(report);app.main.post(()->a.toast("Report deleted."));}catch(Exception e){app.main.post(()->a.toast("Deletion was not confirmed. The report is kept here so you can retry. "+MainActivity.friendly(e)));}})).setNegativeButton("Cancel",null).show()).show();
    }
}
