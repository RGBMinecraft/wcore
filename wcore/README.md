# WCore (Fabric, nur Client, Minecraft 1.21.11)

Befehle im Spiel (mit `/`, nicht mit `./`):

- `/wcore ignore <spieler>` blendet den Spieler aus (auch Chat und Simple-Voice-Chat). Nochmal eingeben = wieder sichtbar.
- `/wcore skinchange <spieler> <skin>` ersetzt den Skin von `<spieler>` durch den Skin von `<skin>`.
- `/wcore skinchange <spieler>` setzt den Skin zurueck.
- `/wcore nickname <name>` aendert deinen Anzeigenamen (ohne Namen = zuruecksetzen).
- `/wcore rename <spieler> <name>` aendert den Anzeigenamen eines anderen Spielers (nur bei dir).
- `/wcore settings` oeffnet die Einstellungen (auch ueber Mod Menu).
- `/wcore list` zeigt alle aktiven Einstellungen.

Alles passiert nur bei dir lokal. Nur dein Nickname wird (wenn aktiviert) ueber unsichtbare Chatnachrichten
an andere WCore-Nutzer gesendet. Einstellungen liegen in `config/wcore.json`.

Bauen: `gradle build` (JDK 25 und Gradle 9.x). Ergebnis: `build/libs/wcore-<version>.jar`.
