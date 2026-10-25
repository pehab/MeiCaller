# Updates nach Installationsquelle

Die App fragt für ihr eigenes Paket den von Android gespeicherten Installer ab. `com.android.vending` wählt Google Play; direkt installierte APKs und ADB-Installationen wählen GitHub. Debug/Release entscheidet nicht mehr über die Updatequelle. Wenn Android die Quelle nicht auslesen lässt, werden keine GitHub-Abfragen gestartet.

- **Google Play:** vorhandene Play-In-App-Updates bleiben erhalten, soweit die App sie unterstützt. Manuelle Updates öffnen Google Play. Es gibt keinen Rückfall auf GitHub bei Play-Fehlern.
- **GitHub:** abschaltbare tägliche Prüfung beim Öffnen/Fortsetzen, zusätzlich manuell unter **Einstellungen → App-Updates**. Download und Installation werden bestätigt. Ohne Verbindung bleibt die App benutzbar.
- APKs werden auf neueren VersionCode, Größe, SHA-256, Paketname, Versionsname und Übereinstimmung der Signatur mit der installierten App geprüft. Der Installer erhält nur eine lesbare URI für die geprüfte Cache-Datei. Bestehende Nutzerdaten werden nicht gelöscht.
- Play-Release-Bundles fordern keine Berechtigung zum Installieren fremder APKs an. Wenn eine solche Release-APK direkt installiert wurde, kann sie ein verifiziertes GitHub-Update über den Browser beziehen. GitHub-Debug-APKs besitzen den direkten Android-Installationsweg.

## Versionen und Signatur

Vor jeder Veröffentlichung `versionCode` erhöhen. Einen bestehenden APK-Signierschlüssel weiterverwenden, niemals zur Behebung eines Updatefehlers einen neuen Schlüssel erzeugen. Der Play-App-Signing-Schlüssel kann vom Upload- oder GitHub-Schlüssel abweichen. Die App lehnt unpassende Signaturen ab.

Die CI unterstützt ein privates Repository-Actions-Secret `MEI_SIGNING_JSON` mit `keystore` (Base64 des bestehenden PKCS12/JKS), `password`, `alias` und optional `keyPassword`. Vorhandene projektspezifische Signierung von MeiOCRWorkout/MeiTüftler wird weiterverwendet und hat Vorrang. Schlüsselmaterial gehört niemals ins Repository.

Ohne konfigurierten dauerhaften Schlüssel bleiben PR-Builds und Tests möglich; es wird kein neuer APK-Updatefeed beworben. Nach erfolgreichem Build und allen vorhandenen Gerätetests veröffentlicht der Job `github-updates` eine unveränderliche APK unter `github-v<versionCode>` und einen Feed unter `releases/download/updates/REPO-update.json` (REPO ist der Repositoryname). Wiederholte Builds überschreiben keine bereits veröffentlichte Version.

## Prüfung

`bash scripts/test-update-contract.sh` prüft die Quellenauswahl einschließlich nicht ermittelbarer Quelle, feindliche APK-URLs, Prüfsummen, abgeschnittene und geänderte Dateien sowie Versionsgrenzen. Die vorhandenen Build-, Lint-, Unit- und Gerätetests bleiben aktiv. Ein tatsächlicher Play-In-App-Download benötigt weiterhin eine installierte Play-Version und ein veröffentlichtes Update im zugehörigen Play-Track.
