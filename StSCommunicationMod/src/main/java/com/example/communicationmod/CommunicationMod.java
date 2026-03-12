package com.example.communicationmod;

import basemod.BaseMod;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

import java.io.IOException;

@SpireInitializer
public class CommunicationMod implements PostInitializeSubscriber, PostUpdateSubscriber {
    
    public static void initialize() {
        new CommunicationMod();
    }

    public CommunicationMod() {
        BaseMod.subscribe(this);
    }

    @Override
    public void receivePostInitialize() {
        try {
            CommunicationServer server = new CommunicationServer();
            server.start(5000);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void receivePostUpdate() {
        // Update state if requested (this runs on game thread)
        StateController.updateState();
        
        // Process card info requests
        CardInfoController.processRequests();

        // Only process actions if we are in a valid game state (e.g., in combat)
        if (CardCrawlGame.isInARun() && AbstractDungeon.player != null && AbstractDungeon.currMapNode != null && AbstractDungeon.getCurrRoom() != null) {
            // Allow processing actions in any phase, not just combat (needed for rewards, events, shops, etc.)
            ActionController.processQueue();
        }
    }
}
