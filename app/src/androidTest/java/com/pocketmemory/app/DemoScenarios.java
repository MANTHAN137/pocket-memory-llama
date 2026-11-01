package com.pocketmemory.app;

import android.os.SystemClock;
import android.view.KeyEvent;
import org.json.*;
import java.nio.file.Files;
import java.io.File;
import java.util.Locale;

/** Demo footage fixtures only. Does not ship in the application APK. */
final class DemoScenarios {
    final EditionInstrumentation t;final MemoryApp app;final MainActivity a;
    final JSONArray evidence=new JSONArray();
    DemoScenarios(EditionInstrumentation test){t=test;app=t.app;a=t.activity;}
    long save(String title,String body){long id=0;for(MemoryStore.Note n:app.store.notes(title))if(n.title.equals(title)&&n.context.equals("Fictional demo fixture.")){id=n.id;break;}return app.store.save(id,title,body,"Fictional demo fixture.");}
    void idle()throws Exception{long end=SystemClock.elapsedRealtime()+180000;while(app.busy&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);t.check(!app.busy,"demo reply finishes within its limit");}
    String ask(String key,String question,String title)throws Exception{
        t.main(()->{a.go("Ask");a.question.setText(question);a.ask();});idle();String result=app.answer;
        t.log.append("ACTUAL ").append(key).append(" ANSWER: ").append(result).append('\n');
        boolean sourceFallback=result.contains("could not verify a generated answer");
        t.check(!app.taskFailed&&!app.sources.isEmpty()&&(!sourceFallback||key.equals("client")),key+(sourceFallback?" clearly returns the source-only safety fallback":" uses a generated, checked local Llama answer"));
        t.check(app.sources.stream().anyMatch(p->p.title.equals(title)),key+" cites the intended saved memory");
        evidence.put(new JSONObject().put("case",key).put("question",question).put("answer",result).put("source",title).put("mode",sourceFallback?"source-only safety fallback":"checked generated answer"));t.shot("demo-"+key);return result.toLowerCase(Locale.ROOT);
    }
    void run()throws Exception{
        String travel="Weekend stay · spare keys",client="Cedar Studio · video brief";
        save(travel,"The spare keys for the weekend stay are in the blue pouch inside the front pocket of my grey backpack. Check-in starts after 3 pm.");
        long clientId=save(client,"Cedar Studio wants the product video by Friday morning with English captions. Do not add background music.");
        app.store.save(clientId,client,"Cedar Studio updated the video brief. Deliver the product video by Monday evening with Hindi captions. Do not add background music.","Fictional demo fixture.");
        t.main(()->a.go("Memory"));t.shot("demo-memories");
        String result=ask("travel","Where did I keep the spare keys for the weekend stay?",travel);
        t.check(result.contains("blue pouch")&&result.contains("backpack"),"travel answer preserves where the keys are kept");
        result=ask("client","Which day should I deliver Cedar Studio's video, and which caption language should I use?",client);
        t.check(result.contains("hindi")&&result.contains("monday"),"client answer uses the corrected caption language and deadline");
        t.check(!result.contains("english")&&!result.contains("friday"),"client answer does not revive the old brief");
        String original="hi team, i confirm the updated brief: a product video with hindi captions and no background music, due monday evening. thanks.";
        t.main(()->{a.go("Writing");a.writing.setText(original);a.write("Make professional");});idle();String written=app.writingOutput;
        t.check(!app.writingFailed&&!written.isBlank(),"the client reply is generated with actual local Llama");
        String lower=written.toLowerCase(Locale.ROOT);t.check(lower.contains("monday")&&lower.contains("hindi")&&lower.contains("music"),"the writing result preserves the supplied commitments");
        t.log.append("ACTUAL WRITING RESULT: ").append(written).append('\n');evidence.put(new JSONObject().put("case","writing").put("operation","Make professional").put("original",original).put("result",written).put("mode",written.equals(original)?"unchanged draft":"edited draft"));
        t.shot("demo-writing-screen");t.main(()->a.writingUi.compare());t.shot("demo-writing");t.main(()->a.featureDialog.dismiss());
        t.main(()->a.noteHistory(clientId));t.shot("demo-client-history");t.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);t.waitForIdleSync();
        t.main(()->a.go("Models"));t.shot("demo-model");
        Files.write(new File(app.getFilesDir(),"demo-scenarios.json").toPath(),new JSONObject().put("fictional",true).put("cases",evidence).toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    void webExample()throws Exception{
        String question="How do I download an area in Google Maps for offline use on Android? Use Google's official help.";
        app.getSharedPreferences("settings",0).edit().putBoolean("chat_web",true).commit();app.chatId=app.chats.create();
        try{t.main(()->{a.go("Chat");a.chatUi.input.setText(question);a.chatUi.send();});long end=SystemClock.elapsedRealtime()+195000;while(app.busy&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);
            t.check(!app.busy&&app.webResult!=null&&app.webResult.question.equals(question),"the travel research scene receives a real MCP and local Llama answer");
            t.check(app.webResult.cited.length>0,"the travel research answer has checked source IDs");t.log.append("ACTUAL MAPS ANSWER: ").append(app.webResult.answer).append('\n');
            Files.write(new File(app.getFilesDir(),"demo-web.json").toPath(),app.webResult.json().toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));t.main(()->a.go("Chat"));t.shot("demo-maps");
        }finally{app.getSharedPreferences("settings",0).edit().putBoolean("chat_web",false).commit();}
    }
    void captureSavedAnswers()throws Exception{
        for(String[] item:new String[][]{{"travel","Where did I keep the spare keys for the weekend stay?"},{"client","Which day should I deliver Cedar Studio's video, and which caption language should I use?"}}){
            JSONArray history=app.store.conversations(item[1],0,0);JSONObject found=null;for(int i=0;i<history.length();i++){JSONObject row=history.getJSONObject(i);if(row.optString("question").equals(item[1])){found=row;break;}}
            t.check(found!=null,"the recorded "+item[0]+" answer is available in actual app history");final JSONObject saved=found;t.main(()->a.openConversation(saved));t.main(()->a.scroll.fullScroll(android.view.View.FOCUS_DOWN));t.shot("demo-"+item[0]+"-answer-detail");
        }
    }
}
