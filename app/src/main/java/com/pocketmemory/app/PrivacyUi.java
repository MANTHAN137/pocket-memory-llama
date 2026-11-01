package com.pocketmemory.app;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.StatFs;
import android.text.InputType;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Offline-readable disclosures, first-use setup, encrypted backup and complete personal-data controls. */
final class PrivacyUi {
    static final String POLICY_VERSION="2026-11-01-llama";
    final MainActivity a;final MemoryApp app;
    PrivacyUi(MainActivity a){this.a=a;app=a.app;}
    static boolean accepted(Context c){return POLICY_VERSION.equals(c.getSharedPreferences("consent",0).getString("policy",""));}
    void setup(){
        a.title("Your memory.\nOn your phone.","Start with notes now. Add local AI whenever you’re ready.");
        LinearLayout c=a.card();a.add(c,a.bold("🔒 Know what stays local",19));a.gap(c,9);a.add(c,a.text("Notes, photos, chats, speech recognition and AI inference stay on this phone. Model downloads contact Hugging Face. OCR’s Google SDK can send device/app diagnostic metrics when online. Reports contact the publisher only when you choose Send. Optional paid models use Google Play checkout.",14,MainActivity.INK));a.add(a.content,c);a.gap(a.content,14);
        LinearLayout models=a.card();a.add(models,a.bold("✨ Download only what you need",19));a.gap(models,9);a.add(models,a.text("The recommended text model is about 696 MB. Meaning search adds about 334 MB; voice input adds about 148 MB. Wi-Fi is recommended. Notes and organizer features work without these downloads. Free storage: "+String.format(java.util.Locale.ROOT,"%.1f GB",new StatFs(a.getFilesDir().getPath()).getAvailableBytes()/1e9)+".",14,MainActivity.INK));a.add(a.content,models);a.gap(a.content,14);
        a.add(a.content,a.text("AI can make mistakes. Check cited memories and review OCR names and numbers. This is a personal organizer, not medical care or an emergency alarm system. Water goals are user-chosen tracking preferences.",13,MainActivity.MUTED));a.gap(a.content,12);
        a.paired(a.content,a.button("Privacy & data",false,this::privacy),a.button("Terms of use",false,()->document("terms.txt","Terms of use")));a.gap(a.content,12);
        a.add(a.content,a.text("Continuing accepts the terms of use and acknowledges these privacy disclosures. Downloads show each model’s terms separately. No account or automatic report submission is required.",12,MainActivity.MUTED));a.gap(a.content,12);
        a.add(a.content,a.button("Agree & choose a model",true,()->finish("Models")));a.gap(a.content,10);a.add(a.content,a.button("Agree & start with notes",false,()->finish("Today")));
    }
    void finish(String screen){app.getSharedPreferences("consent",0).edit().putString("policy",POLICY_VERSION).apply();a.go(screen);a.handleSharedText();}
    void privacy(){
        String[] items={"Privacy policy (available offline)","Public privacy policy","Publisher and support","My response reports","Export password-protected backup","Import backup","Erase personal data","Show setup again","Terms of use","Model licences and restrictions"};
        new ThemedDialog.Builder(a).setTitle("🔒 Privacy & data").setItems(items,(d,i)->{
            switch(i){case 0:document("privacy.txt","Privacy policy");break;case 1:if(BuildConfig.PRIVACY_URL.isBlank())a.toast("The public policy URL is pending. You can read the complete policy offline here.");else a.openUrl(BuildConfig.PRIVACY_URL);break;
                case 2:new ThemedDialog.Builder(a).setTitle(BuildConfig.PUBLISHER).setMessage("Support: "+BuildConfig.SUPPORT_EMAIL+"\n\nApp version "+BuildConfig.VERSION_NAME+"\nNo account is created. When contacting support, include only information you want the publisher to receive.").setPositiveButton("Copy email",(x,y)->a.copy(BuildConfig.SUPPORT_EMAIL)).setNegativeButton("Close",null).show();break;
                case 3:new ReportUi(a).history();break;case 4:chooseExport();break;case 5:a.pick(MainActivity.RESTORE,"*/*");break;case 6:erase();break;case 7:a.go("Setup");break;case 8:document("terms.txt","Terms of use");break;case 9:ModelTerms.catalog(a);break;default:break;
            }
        }).setNegativeButton("Close",null).show();
    }
    void document(String asset,String title){try(InputStream in=a.getAssets().open(asset)){
        TextView text=a.text(new String(in.readAllBytes(),StandardCharsets.UTF_8),15,MainActivity.INK);text.setTextIsSelectable(true);text.setPadding(a.dp(20),a.dp(12),a.dp(20),a.dp(20));android.text.util.Linkify.addLinks(text,android.text.util.Linkify.WEB_URLS|android.text.util.Linkify.EMAIL_ADDRESSES);text.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());ScrollView scroll=new ScrollView(a);scroll.addView(text);new ThemedDialog.Builder(a).setTitle(title).setView(scroll).setPositiveButton("Close",null).show();
    }catch(IOException e){a.toast("This document could not be opened.");}}
    void chooseExport(){Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,"PocketMemory-"+LifeStore.today()+".pmbackup");a.startActivityForResult(intent,MainActivity.EXPORT);}
    interface PasswordAction{void run(char[] password);}
    void password(String title,boolean confirm,PasswordAction action){
        LinearLayout form=a.column();form.setPadding(a.dp(20),a.dp(10),a.dp(20),a.dp(12));a.add(form,a.text(confirm?"Use at least 12 characters. Your password is never saved. Keep it safe: the backup cannot be recovered without it.":"Enter the password used for this backup. Your password is never saved.",14,MainActivity.INK));a.gap(form,12);
        EditText first=a.input("Backup password",false);first.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);a.add(form,first);EditText second=a.input("Confirm password",false);second.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);if(confirm){a.gap(form,10);a.add(form,second);}
        ScrollView scroll=new ScrollView(a);scroll.addView(form);AlertDialog dialog=new ThemedDialog.Builder(a).setTitle(title).setView(scroll).setPositiveButton(confirm?"Encrypt backup":"Unlock backup",null).setNegativeButton("Cancel",null).create();a.featureDialog=dialog;dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            char[] secret=new char[first.length()];android.text.TextUtils.getChars(first.getText(),0,first.length(),secret,0);boolean matches=!confirm||first.getText().toString().contentEquals(second.getText());
            if(secret.length<12||secret.length>256||!matches){Arrays.fill(secret,'\0');a.toast(matches?"Use a password of 12–256 characters.":"The passwords do not match.");return;}first.setText("");second.setText("");dialog.dismiss();action.run(secret);
        }));dialog.setOnDismissListener(d->{first.setText("");second.setText("");});dialog.show();
    }
    void export(Uri uri){password("Protect your backup",true,secret->app.files.execute(()->{try(OutputStream out=a.getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Could not create the backup.");EncryptedBackup.write(app.store,out,secret);app.main.post(()->a.toast("Password-protected backup exported. Keep its password safe."));}catch(Exception e){try{android.provider.DocumentsContract.deleteDocument(a.getContentResolver(),uri);}catch(Exception ignored){}app.main.post(()->a.toast(MainActivity.friendly(e)));}finally{Arrays.fill(secret,'\0');}}));}
    void restore(Uri uri){app.files.execute(()->{try(InputStream in=a.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Could not open backup.");boolean encrypted=EncryptedBackup.encrypted(in);app.main.post(()->{if(a.destroyed)return;if(encrypted)password("Unlock your backup",false,secret->importFile(uri,secret));else new ThemedDialog.Builder(a).setTitle("Import an unencrypted backup?").setMessage("This legacy backup contains readable personal information. It will merge with your existing data. Imported reminders stay off.").setPositiveButton("Import",(d,w)->importFile(uri,null)).setNegativeButton("Cancel",null).show();});}catch(Exception e){app.main.post(()->a.toast(MainActivity.friendly(e)));}});}
    void importFile(Uri uri,char[] secret){app.files.execute(()->{try(InputStream in=a.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Could not open backup.");int count=secret==null?BackupIO.restore(app.store,in):EncryptedBackup.restore(app.store,in,secret);app.worker.execute(app.engine::indexAll);TaskAssist.changed(a);app.main.post(()->{a.toast("Imported "+count+" new memories and merged organizer/chat history. Imported reminders are off until enabled.");if(!a.destroyed)a.go("Memory");});}catch(Exception e){app.main.post(()->a.toast(MainActivity.friendly(e)));}finally{if(secret!=null)Arrays.fill(secret,'\0');}});}
    void erase(){
        if(app.busy||app.ocrBusy||!app.downloadId.isEmpty()||app.maintenance){a.toast("Finish or stop the current task and download before erasing data.");return;}
        LinearLayout form=a.column();form.setPadding(a.dp(20),a.dp(10),a.dp(20),a.dp(12));a.add(form,a.text("Permanently erase this app’s notes, photos, versions, evidence snapshots, chats, drafts, tasks, journal, intake history, reminders and local report copies. All app alarms will stop. Local purchase receipts and complimentary unlocks are also cleared. Play purchases can be restored; gifted access needs another code. This does not refund purchases.\n\nExported backups and reports already sent to the developer are separate copies. Delete sent reports in My response reports or contact support before erasing their references. This does not guarantee forensic erasure of flash storage.",14,MainActivity.INK));CheckBox models=new CheckBox(a);models.setText("Also remove downloaded models");models.setMinHeight(a.dp(48));a.add(form,models);EditText confirmation=a.input("Type ERASE to confirm",false);a.add(form,confirmation);
        ScrollView scroll=new ScrollView(a);scroll.addView(form);AlertDialog dialog=new ThemedDialog.Builder(a).setTitle("Erase personal data?").setView(scroll).setPositiveButton("Erase",null).setNegativeButton("Cancel",null).create();a.featureDialog=dialog;dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(!confirmation.getText().toString().equals("ERASE")){a.toast("Type ERASE to confirm.");return;}if(app.busy||app.ocrBusy||!app.downloadId.isEmpty()||app.maintenance){a.toast("A task is running. Stop it and try again.");return;}app.maintenance=true;boolean remove=models.isChecked();dialog.dismiss();a.toast("Erasing personal data…");app.files.execute(()->{try{PersonalData.erase(app,remove);app.main.post(()->{a.photoUi=new PhotoUi(a);a.chatUi=new ChatUi(a);a.todayUi=new TodayUi(a);a.toast("Personal data erased.");a.go("Today");});}catch(Exception e){app.main.post(()->a.toast("Data deletion needs attention: "+MainActivity.friendly(e)));}finally{app.maintenance=false;app.changed();}});
        }));dialog.show();
    }
}
