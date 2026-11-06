# WCore (Fabric, nur Client)

Befehle im Spiel (Minecraft-Befehle beginnen mit `/`, nicht mit `./`):

- `/wcore ignore <spieler>` blendet den Spieler aus. Nochmal eingeben, dann ist er wieder da.
- `/wcore skinchange <spieler> <skin>` ersetzt den Skin von `<spieler>` durch den Skin von `<skin>`.
- `/wcore skinchange <spieler>` setzt den Skin zurueck.
- `/wcore list` zeigt alle aktiven Einstellungen.

Alles passiert nur bei dir lokal, andere Spieler sehen nichts davon.

## Bauen

Voraussetzung: JDK 21 und Gradle (oder der Gradle-Wrapper aus der Fabric-Vorlage).

    gradle build

Die fertige Mod liegt danach unter `build/libs/wcore-1.0.0.jar` (nicht die `-sources.jar`).
Sie gehoert in den `mods`-Ordner, zusammen mit Fabric Loader und Fabric API fuer Minecraft 1.21.4.

Andere Minecraft-Version: Werte in `gradle.properties` anpassen (siehe https://fabricmc.net/develop)
und `fabric.mod.json` (`minecraft`-Version). Bei neueren Versionen koennen sich Klassen-/Methodennamen
in den Mixins aendern.
