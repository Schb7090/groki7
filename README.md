# Groki – Android app a Grok automatizmusok eredményeihez

A Grok ütemezett feladatai (Task Extractor, Promó-figyelő, Pénzvadász, Napi e-mail-csekk) óránként két helyre dolgoznak:

- **Gmail:** a `noreply@x.ai` címről küldött értesítő levelekbe. Ezek csak csonkolt kivonatot tartalmaznak, „Continue reading” linkkel.
- **Google Drive:** `Action_Items_Next_Steps_Summary_<dátum>.md` Google Docs fájlokba. Ezekben van a teljes, prioritás szerint rendezett teendőlista.

A teendők jelenleg szétszórva, olvasatlan levelek tömegében érkeznek. Minden futás az összes nyitott tételt megismétli, így a valóban sürgős dolgok elvesznek. A Groki ebből egyetlen, telefonon kezelhető teendőlistát csinál.

## UX-elvek

| Probléma a mostani folyamatban | Megoldás a Grokiban |
|---|---|
| Óránként új levél ugyanazokkal a tételekkel | Az app a **legfrissebb** összefoglalót olvassa. A tételek stabil azonosítót kapnak, így a „kész” jelölés megmarad a következő futás után is. |
| A biztonsági riasztások elvesznek a promók között | Külön **Biztonság** kategória, piros kártyák a „Ma” nézet tetején, kiemelt értesítés. |
| A promók határideje szövegben van („until 2026-11-24 09:00 CET”) | A határidőt az app dátummá alakítja: **visszaszámláló** („még 5 óra”), és 24 órával a lejárat előtt értesít. |
| Döntést kérő tételek („Decide on…”, „explicit yes/no”) | Egy koppintással **Kérem / Nem kérem**. A „Nem kérem” lezárja a tételt, a „Kérem” megnyitja a hivatalos linket. Ha a szöveg bankkártyát említ, az app figyelmeztet. |
| Nincs mód jelezni, mi van kész | **Csúsztatás:** jobbra = kész, balra = holnapig halasztva. **Visszavonás** a snackbarban. Halasztás 3 órára, holnapig, 3 napra vagy 1 hétre. |
| Túl sok értesítés | Egy elemről legfeljebb egyszer jön értesítés, csatornánként csoportosítva (Biztonság, Határidő, Fiók). Az Android beállításaiban csatornánként kikapcsolható. |
| Netkapcsolat nélkül nem látszik semmi | Offline gyorsítótár. Indításkor azonnal megjelenik az utolsó állapot, a frissítés a háttérben fut. |
| A telefon feloldása után is több lépés | **Kezdőképernyő-widget** a 4 legsürgősebb tétellel. |

## Képernyők

1. **Ma:** három számláló csempe (Biztonság, Lejár 48 órán belül, Döntésre vár), összecsukható Grok-összefoglaló, valamint ezek a szakaszok: Biztonsági ellenőrzés, Hamarosan lejár, Sürgős, és a Grok javasolt következő lépései.
2. **Teendők:** teljes lista prioritás szerint csoportosítva, kereséssel, kategória-szűrő chipekkel és „Lezártak” nézettel (újranyitással).
3. **Promók:** ajánlatok határidő szerint rendezve, Kérem / Nem kérem / Megnyitás / Elintézve gombokkal.
4. **Levelek:** a Grok-értesítők automatizmus szerint szűrhetők. Az olvasatlanok félkövérek, koppintásra a Gmailben nyílnak meg.

Bármely tételre koppintva alsó lap nyílik: következő lépés kiemelve, részletek, kattintható linkek, és itt érhető el minden művelet, a vágólapra másolással együtt.

## Architektúra

```
Grok automatizmusok ──► Gmail (noreply@x.ai)  ──┐
                    └─► Drive Action_Items_*.md ─┤  csak olvasás
                                                 ▼
        FolderSource (SAF, Drive app) · BridgeSource (Apps Script) · GoogleApi (OAuth)
                                                 ▼
                       SummaryParser / MailParser ──► Snapshot (JSON, filesDir)
                                                 ▼                     ▲
                       Compose UI (ViewModel) · WorkManager (1 óra) · Glance widget · értesítések
```

- **Kotlin + Jetpack Compose + Material 3.** Android 12+ eszközön a háttérkép színeit veszi át (dynamic color). Sötét mód támogatott. minSdk 26, targetSdk 35.
- **Hozzáférés:** alapból a Storage Access Framework (nincs OAuth), tartaléknak Apps Script híd, haladó módban Google Identity `AuthorizationClient`. Nincs saját szerver.
- **Helyi állapot:** a kész, halasztott, elvetett jelöléseket és a promódöntéseket csak a telefon tárolja. A Drive-ra és a Gmailbe az app semmit nem ír.
- **Parser:** a Drive markdown-exportot és a sima szöveges exportot is kezeli. A „High/Medium/Low Priority” szakaszokból és az `Owner / Due / Details / Next step` mezőkből dolgozik, és a kategóriát kulcsszavak alapján állapítja meg. A tesztek az `app/src/test` alatt vannak, szintetikus mintával.

## Adatforrás – három lehetőség

Az app indításkor megkérdezi, honnan olvasson. A teendők állapota (kész, halasztás, döntés) forrásváltáskor is megmarad (⋮ → Forrás módosítása).

### 1. Drive-mappa kiválasztása (ajánlott, nincs beállítás)
A rendszer fájlválasztójában (☰ → Drive) azt a mappát választod ki, ahová a Grok az Action Items összefoglalókat menti. Az app a telefonon lévő Google Drive appon keresztül olvas (Storage Access Framework), ezért nem kell OAuth-kliens.
- A Google Docs fájlokat a Drive app csak konvertálva adja ki. Az app ezeket a formátumokat kezeli: markdown, sima szöveg, HTML, docx.
- A Grok minden futáshoz új almappát hoz létre. Az app a 6 legutóbb módosított almappába néz bele, legfeljebb 3 szint mélyen.
- Korlát: ebben a módban a Grok-levelek nem érhetők el. Előre nem ellenőrzött az is, hogy a Drive app minden eszközön engedi-e a mappa kiválasztását. Ha nem, használd a 2. pontot.

### 2. Apps Script híd (levelekkel együtt, Cloud Console nélkül)
Egy kis webapp, amely a saját Google-fiókodban fut, és JSON-ként adja vissza a legfrissebb összefoglalót és a Grok-leveleket. A kódja: [`app/src/main/assets/bridge.gs`](app/src/main/assets/bridge.gs). Az app gombnyomásra vágólapra másolja.
1. Nyisd meg: script.google.com → Új projekt, majd illeszd be a kódot, és ments.
2. Telepítés → Új telepítés → Webalkalmazás. Futtatás mint: **Én**, hozzáférés: **Bárki**.
3. Engedélyezd. A „Google nem ellenőrizte” ablakban: Speciális → Ugrás a projektre.
4. A `/exec` végű URL-t másold be az appba. Az URL titkos kulcsként működik, ne oszd meg.

### 3. Közvetlen Google-bejelentkezés (haladó)
Gmail és Drive API közvetlenül. Ehhez saját OAuth-kliens kell a Google Cloud Console-ban:
1. Engedélyezd a **Gmail API**-t és a **Google Drive API**-t.
2. A beleegyezési képernyő típusa legyen External, a státusza Testing, és vedd fel magad tesztfelhasználónak.
3. Hozz létre egy **Android** OAuth-klienst. Package name: `app.groki`. SHA-1: az APK aláíró kulcsáé; a kiadott debug APK-é `E9:5E:9A:4B:45:1F:1C:93:1D:98:3A:2D:0A:46:D5:DB:89:51:77:C3`.

## Build

```bash
./gradlew :app:testDebugUnitTest   # parser tesztek
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

JDK 17+ és Android SDK (platform 35) szükséges.

## Ismert korlátok és következő lépések

- A felület nem volt valódi eszközön vagy emulátoron kipróbálva, mert a build környezetben nincs emulátor. A fordítás és a parser tesztjei lefutnak.
- A kategorizálás kulcsszó alapú, ezért új típusú tételnél tévedhet. Javítási ötlet: a Grok-feladat promptja írjon minden tételhez explicit `Category:` mezőt. A parser ezt könnyen átvehetné.
- A Drive markdown-export pontos formája nem dokumentált garancia. Ha a Google megváltoztatja, a sima szöveges export a tartalék.
- A Promó-figyelő és a Pénzvadász saját Drive-fájljait az app még nem olvassa közvetlenül, csak a Task Extractor összefoglalóján keresztül, amely ezeket is tartalmazza.
- Tervezett: a „Kérem” döntés visszaírása (például egy Drive-fájlba, amelyet a Grok következő futása elolvas), hogy a bot lássa a döntéseidet. Ehhez írási jogosultság kell, ezért ez külön döntés.
