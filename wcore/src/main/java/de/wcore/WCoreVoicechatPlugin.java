package de.wcore;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

/** Schaltet ausgeblendete Spieler im Simple Voice Chat stumm (nur bei dir). */
public class WCoreVoicechatPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return "wcore";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, event -> {
            if (WCoreClient.shouldMute(event.getEntityId())) {
                event.cancel();
            }
        });
        registration.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, event -> {
            if (WCoreClient.shouldMute(event.getId())) {
                event.cancel();
            }
        });
        registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class, event -> {
            if (WCoreClient.shouldMute(event.getId())) {
                event.cancel();
            }
        });
    }
}
