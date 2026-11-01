package com.pocketmemory.app;

import android.content.*;
import android.net.Uri;
import android.widget.Toast;

final class CompanionUi {
    static void open(MainActivity a){
        new ThemedDialog.Builder(a).setTitle("Browser companion")
            .setMessage("Browser agents you allow can search your memories and ask Llama while this session is open. The connection stays on this phone and turns off after 15 minutes. Your chosen browser agent may process returned content under its own provider’s policy. Memory edits and model downloads stay in the Android app.")
            .setNegativeButton("Cancel",null).setNeutralButton("Turn off",(d,w)->a.stopService(new Intent(a,CompanionService.class)))
            .setPositiveButton("Open companion",(d,w)->{a.startForegroundService(new Intent(a,CompanionService.class));launch(a,0);}).show();
    }
    private static void launch(MainActivity a,int attempt){
        CompanionServer server=CompanionService.server;
        if(server!=null){a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(server.openLink())));return;}
        if(attempt>=30){Toast.makeText(a,"Couldn't start the companion. Try again.",Toast.LENGTH_LONG).show();return;}
        a.app.main.postDelayed(()->{if(!a.isFinishing())launch(a,attempt+1);},100);
    }
}
