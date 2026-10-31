package com.pocketmemory.app;

/** Fictional, explicitly loaded examples. No real customer information. */
final class ExampleData {
    static final String GOAL="Prepare for and follow up on the Lakeview client demo.";
    static void add(PlanStore store){
        store.saveNote(0,"Lakeview · arrival","The Lakeview client demo is on 2 November 2026. Arrive at Riverside Hub at 09:45 for the 10:00 demo.");
        store.saveNote(0,"Lakeview · demo kit","Bring the prototype tablet, USB-C hub and printed storyboard to the Lakeview client demo.");
        store.saveNote(0,"Lakeview · venue","Use the east entrance to reach room Cedar for the Lakeview client demo.");
        store.saveNote(0,"Lakeview · follow-up","After the Lakeview client demo, send Mira the revised storyboard for approval before production.");
        store.saveNote(0,"Lakeview · separate party","This is a different event, not the client demo. Bring blue balloons to the Lakeview team party on 20 November 2026.");
    }
}
