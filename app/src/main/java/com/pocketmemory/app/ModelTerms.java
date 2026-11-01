package com.pocketmemory.app;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Model-specific agreements are available offline and accepted before download, import or inference. */
final class ModelTerms {
    static final String VERSION="2026-10-22";
    static String key(Catalog.Model model){return model.id+":"+model.sha256+":"+VERSION;}
    static boolean accepted(Context c,Catalog.Model model){return model!=null&&c.getSharedPreferences("model_terms",0).getBoolean(key(model),false);}
    static String asset(Catalog.Model model){return "licenses/"+(!model.licenseAsset.isEmpty()?model.licenseAsset:(model.id.equals("lfm-1.2b")?"lfm":model.id.equals("embeddinggemma")?"gemma":model.kind.equals("voice")?"whisper":"apache-2.0")+".txt");}
    static void installNotices(Context c,Catalog.Model model)throws IOException{
        try(InputStream in=c.getAssets().open(asset(model))){Files.copy(in,new File(Catalog.directory(c),model.id+".LICENSE.txt").toPath(),StandardCopyOption.REPLACE_EXISTING);}
        if(asset(model).equals("licenses/gemma.txt")){
            try(InputStream in=c.getAssets().open("licenses/gemma-prohibited-uses.txt")){Files.copy(in,new File(Catalog.directory(c),model.id+".PROHIBITED-USES.txt").toPath(),StandardCopyOption.REPLACE_EXISTING);}
            Files.write(new File(Catalog.directory(c),model.id+".NOTICE.txt").toPath(),("Gemma is provided under and subject to the Gemma Terms of Use found at ai.google.dev/gemma/terms\nThe upstream "+model.repo+" repository converted/quantized this model to GGUF. Pocket Memory downloads the pinned upstream file unchanged.\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        if(model.upstream.startsWith("meta-llama/Llama-3.2-")){
            try(InputStream in=c.getAssets().open("licenses/llama-3.2-use-policy.txt")){Files.copy(in,new File(Catalog.directory(c),model.id+".USE-POLICY.txt").toPath(),StandardCopyOption.REPLACE_EXISTING);}
            Files.write(new File(Catalog.directory(c),model.id+".NOTICE.txt").toPath(),("Built with Llama.\nLlama 3.2 is licensed under the Llama 3.2 Community License, Copyright © Meta Platforms, Inc. All Rights Reserved.\nGGUF quantization: "+model.repo+". File downloaded unchanged.\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    static String details(Catalog.Model model){
        if(model.id.equals("lfm-1.2b"))return "LFM Open License 1.0 includes use restrictions and a US$10 million annual-revenue threshold. Review Section 5 and obtain separate commercial permission if the threshold applies to you. The bundled complete licence governs.";
        if(asset(model).equals("licenses/gemma.txt"))return "Gemma is provided under and subject to the Gemma Terms of Use found at ai.google.dev/gemma/terms. Its Prohibited Use Policy is incorporated into your agreement to use this model. The complete terms and prohibited uses are bundled below.";
        if(model.upstream.startsWith("meta-llama/Llama-3.2-"))return "Built with Llama. The Llama 3.2 Community License and Acceptable Use Policy apply. Keep Meta’s attribution and notice. The licence includes additional commercial terms above its 700 million monthly-active-user threshold. GGUF source: "+model.repo+".";
        if(!model.available())return model.unavailableReason+"\n\nUpstream: "+model.upstream+". The complete upstream licence governs.";
        return "This model is provided under "+model.license+". Keep the required copyright, licence and attribution notices when redistributing it. Review the complete licence below.";
    }
    static void ensure(MainActivity a,Catalog.Model model,Runnable next){if(accepted(a,model)){next.run();return;}
        LinearLayout form=a.column();form.setPadding(a.dp(20),a.dp(10),a.dp(20),a.dp(16));a.add(form,a.bold(model.name,19));a.gap(form,10);a.add(form,a.text(details(model),14,MainActivity.INK));a.gap(form,10);a.add(form,a.button("Read complete licence",false,()->new PrivacyUi(a).document(asset(model),model.license)));if(asset(model).equals("licenses/gemma.txt")){a.gap(form,8);a.add(form,a.button("Gemma prohibited uses",false,()->new PrivacyUi(a).document("licenses/gemma-prohibited-uses.txt","Gemma prohibited uses")));}if(model.upstream.startsWith("meta-llama/Llama-3.2-")){a.gap(form,8);a.add(form,a.button("Llama acceptable use",false,()->new PrivacyUi(a).document("licenses/llama-3.2-use-policy.txt","Llama acceptable use")));}
        a.gap(form,12);a.add(form,a.text(model.deviceDetails(),13,MainActivity.INK));a.gap(form,10);a.add(form,a.text("A download contacts Hugging Face and its download hosts, which receive your network address and the requested file. This app does not send your notes or chats to them. Wi-Fi is recommended. Files can also be imported locally.",13,MainActivity.MUTED));CheckBox agree=new CheckBox(a);agree.setText("I accept this model’s licence and use restrictions.");agree.setMinHeight(a.dp(48));a.add(form,agree);
        ScrollView scroll=new ScrollView(a);scroll.addView(form);AlertDialog dialog=new ThemedDialog.Builder(a).setTitle("Model terms").setView(scroll).setPositiveButton("Accept & continue",null).setNegativeButton("Cancel",null).create();a.featureDialog=dialog;dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(!agree.isChecked()){a.toast("Review and accept the model’s terms to continue.");return;}try{if(model.installed(a))installNotices(a,model);}catch(IOException e){a.toast("Model licence notices could not be saved. Please free storage and retry.");return;}a.app.getSharedPreferences("model_terms",0).edit().putBoolean(key(model),true).apply();dialog.dismiss();next.run();}));dialog.show();
    }
    static void catalog(MainActivity a){List<Catalog.Model> models=a.app.catalog.models;String[] names=new String[models.size()];for(int i=0;i<names.length;i++)names[i]=models.get(i).name+" · "+models.get(i).license;new ThemedDialog.Builder(a).setTitle("Model licences").setItems(names,(d,i)->{Catalog.Model model=models.get(i);new ThemedDialog.Builder(a).setTitle(model.name).setMessage(details(model)).setPositiveButton("Complete licence",(x,y)->new PrivacyUi(a).document(asset(model),model.license)).setNeutralButton("Source",(x,y)->a.openUrl("https://huggingface.co/"+model.repo+"/tree/"+model.revision)).setNegativeButton("Close",null).show();}).setNegativeButton("Close",null).show();}
}
