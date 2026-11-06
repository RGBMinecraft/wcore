package de.wcore;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Zeigt in Mod Menu den "Einstellungen"-Knopf fuer WCore. */
public class WCoreModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return WCoreScreen::new;
    }
}
