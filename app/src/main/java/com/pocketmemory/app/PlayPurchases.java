package com.pocketmemory.app;
/** This edition includes its sole Llama model without a purchase flow. */
final class PlayPurchases {
    String message="";
    PlayPurchases(MemoryApp app){}
    boolean configured(){return false;}
    void start(){}
    void refresh(){}
    String price(Catalog.Model model){return "Included";}
    boolean canBuy(Catalog.Model model){return false;}
    void buy(android.app.Activity activity,Catalog.Model model){}
    void erase(){}
}
