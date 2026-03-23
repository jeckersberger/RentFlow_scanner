# RentFlow Scanner — Android App Design

> Native Android App für den CF-H906 UHF PDA zur Lagerverwaltung in RentFlow

## Übersicht

- **Plattform**: Android (min SDK 28 / Android 9)
- **Gerät**: CF-H906 UHF PDA (exklusiv — Handys nutzen weiter die PWA)
- **Sprache**: Kotlin + Jetpack Compose
- **Namespace**: `com.rentflow.scanner`
- **Mehrsprachig**: DE/EN (i18n, Default: Deutsch, Wechsel über Einstellungen mit App-Restart)
- **Kommunikation**: REST API über Internet via Traefik → auth-service + scanner-service

## Screens

1. **Login** — Email/Passwort + QR-Code Quick-Login
2. **Home/Dashboard** — Schnellzugriff auf alle Workflows
3. **Scan** — Zentraler Screen: Barcode/RFID scannen → Equipment anzeigen
4. **Check-Out** — Equipment einem Projekt zuweisen (einzeln oder Bulk via RFID)
5. **Check-In** — Equipment zurücknehmen + Zustandsbewertung (1-5 Sterne + Notiz + Foto)
6. **Inventur** — Lagerbereich scannen, Soll/Ist abgleichen
7. **Equipment-Detail** — Info, Historie, Status, RFID-Tag schreiben
8. **Einstellungen** — Server-URL, Sprache, Hardware-Konfiguration

**Kern-Flow:** Scan → Equipment erkannt → Action wählen (Check-In/Out/Detail) → Fertig

## Architektur

**Clean Architecture (MVVM):**

```
app/
├── data/           # Retrofit API, Room DB, SharedPreferences
│   ├── api/        # RentFlow API Interfaces
│   ├── db/         # Room (Offline-Queue)
│   └── hardware/   # CF-H906 SDK Wrapper (Barcode + RFID)
├── domain/         # Use Cases, Models
├── ui/             # Jetpack Compose Screens + ViewModels
│   ├── login/
│   ├── home/
│   ├── scan/
│   ├── checkout/
│   ├── checkin/
│   ├── inventory/
│   ├── equipment/
│   └── settings/
└── di/             # Dependency Injection (Hilt)
```

## Tech-Stack

| Komponente | Technologie |
|------------|-------------|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Netzwerk | Retrofit + OkHttp (JWT Interceptor) |
| Offline-Queue | Room |
| Kamera-Barcode | CameraX + ML Kit (Fallback) |
| Token-Speicher | EncryptedSharedPreferences (JWT + Refresh Token) |
| Einstellungen | DataStore (Server-URL, Sprache, Hardware-Config) |
| Bilder | Coil |
| Background-Sync | WorkManager |

**Storage-Aufteilung:**
- `EncryptedSharedPreferences`: Access Token, Refresh Token, Tenant-ID — sicherheitsrelevante Daten
- `DataStore`: Server-URL, Sprache, Hardware-Einstellungen — Preferences
- Server-URL wird beim App-Start gelesen und als OkHttp BaseUrl gesetzt. Änderung erfordert App-Neustart.

## Android Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## Hardware-Integration

### Barcode-Scanner (CF-H906)

- Empfang über `BroadcastReceiver` — CF-H906 sendet Scan-Ergebnisse als Android Intent/Broadcast
- Hardware-Buttons (Scan + Pistol Grip) triggern Scanner automatisch
- Fallback: CameraX + ML Kit Barcode-Scanning (eigener Camera-Preview im Scan-Screen, nur sichtbar wenn Fallback aktiv)

### UHF RFID

- Integration über Hersteller-SDK (JAR/AAR — noch zu besorgen)
- Lesen: Single-Tag oder Bulk-Scan (Inventur)
- Schreiben: EPC-Daten auf Tags schreiben (Equipment taggen)
- Signalstärke (RSSI) anzeigen zum Orten von Equipment

### RFID-Tag Write

- **EPC-Wert**: Wird vom Server generiert (`POST /api/v1/scan/devices` oder Equipment-Endpunkt)
- **Schreibvorgang**: App schreibt EPC auf Tag, meldet Erfolg an Server
- **Konflikt**: Tag hat bereits Daten → Dialog: "Tag überschreiben?" mit Anzeige des aktuellen EPC
- **Fehler**: Write fehlgeschlagen (Tag out of range, Write-Error) → Retry-Dialog mit klarer Fehlermeldung
- **Verifikation**: Nach Write sofort Read-Back zur Bestätigung

### SDK-Abstraction

Das CF-H906 SDK ist noch nicht vorhanden. Design mit austauschbarer Abstraktion:

1. **Interface `HardwareScanner`** — `startBarcodeScan()`, `stopBarcodeScan()`, `startRfidRead()`, `startRfidBulkRead()`, `writeRfidTag(epc: String)`, `stopRfid()`, `getRssi(): Int`
2. **Mock-Implementierung** — zum Entwickeln und Testen (generiert Fake-Barcodes/RFID-Tags)
3. **Echte Implementierung** — sobald SDK vom Hersteller verfügbar

**Fallback-Plan falls SDK nicht erhältlich:**
- Barcode: CameraX + ML Kit als primärer Scanner
- RFID: Feature deaktiviert, nur Barcode-Workflows verfügbar
- App ist auch ohne SDK voll nutzbar für Barcode-basierte Workflows

## Offline-Queue

- **Room-Tabelle `pending_scans`** — speichert Scan-Aktionen bei Netzwerkausfall
- **WorkManager** — prüft Konnektivität, sendet Queue automatisch ab
- **Retry-Policy**: ExponentialBackoff, max 5 Retries, Faktor 2 (30s → 60s → 120s → 240s → 480s)
- **Nach 5 Fehlschlägen**: Item bleibt in Queue, wird als "fehlgeschlagen" markiert
- **Status-Anzeige** — Badge im Header: "3 Scans ausstehend"
- **Queue-Ansicht**: Nutzer kann ausstehende/fehlgeschlagene Items einsehen, manuell retrien oder löschen
- **Kein Full-Sync** — Equipment-Daten werden nicht lokal gecacht, nur die Scan-Queue
- **Offline-UX**: Wenn Equipment-Detail offline abgefragt wird → Hinweis: "Keine Internetverbindung — Details nicht verfügbar. Scan wurde in Warteschlange gespeichert."

## Auth

### Standard-Login

- Email + Passwort → `POST /api/v1/auth/login` → Access Token + Refresh Token
- **Access Token**: 1 Stunde Gültigkeit, RS256 signiert
- **Refresh Token**: 7 Tage Gültigkeit, in Response-Body und als HttpOnly Cookie
- Access Token + Refresh Token in EncryptedSharedPreferences gespeichert
- App-Start: Access Token prüfen → wenn abgelaufen, Refresh Token nutzen → direkt zum Home-Screen
- OkHttp Authenticator: Bei 401-Response automatisch Refresh ausführen, Request wiederholen
- Synchronized Refresh: Nur ein Refresh gleichzeitig, parallele Requests warten auf neues Token

**Login Request:**
```json
POST /api/v1/auth/login
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Login Response:**
```json
{
  "data": {
    "access_token": "eyJhbGc...",
    "refresh_token": "eyJhbGc...",
    "expires_in": 3600,
    "token_type": "Bearer"
  },
  "message": "Login successful"
}
```

**Refresh Request:**
```json
POST /api/v1/auth/refresh
{
  "refresh_token": "eyJhbGc..."
}
```

### QR-Code Quick-Login

Neuer Endpoint im auth-service erforderlich (noch zu implementieren):

1. **Frontend**: `POST /api/v1/auth/scanner-token` → generiert temporären Scanner-Auth-Token (60s gültig)
2. **QR-Code enthält**: JSON `{"url": "https://<domain>", "token": "<temp-token>", "tenant_id": "<id>"}`
3. **App**: Scannt QR-Code → `POST /api/v1/auth/scanner-login` mit `{"scanner_token": "<temp-token>"}` → erhält Access + Refresh Token
4. Praktisch bei Schichtwechsel oder geteiltem Scanner

### Session-Timeout

- Bei 30 Minuten Inaktivität: App sperrt sich, zeigt Lock-Screen (Quick-Login oder Passwort)
- Hintergrund > 4 Stunden: Voller Re-Login erforderlich
- Konfigurierbar in Einstellungen

## UI-Design

- **Material 3** mit RentFlow-Farben:
  - Primary: `#00d4ff` (Cyan)
  - Accent: `#8b5cf6` (Purple)
  - Background: `#0a0f1a`
  - Card: `#111827`
  - Text: `#e2e8f0`
- **Große Touch-Targets** (min 48dp) — Bedienung mit Handschuhen
- **Hoher Kontrast** — Lager kann dunkel sein
- **Audio + Vibration Feedback** bei erfolgreichem Scan
- **Nur Portrait** — Landscape wird gesperrt (CF-H906 im Lager immer hochkant genutzt)
- **Release Build**: minifyEnabled + R8 Shrinking aktiv (Security Baseline)

### Error Handling

Globale Error-States für alle Screens:
- **Netzwerk-Timeout**: "Keine Verbindung zum Server. Scans werden lokal gespeichert."
- **401 Unauthorized**: Automatischer Token-Refresh, bei Fehlschlag → Login-Screen
- **404 Equipment nicht gefunden**: "Unbekannter Barcode: [code]. Equipment anlegen?"
- **409 Conflict** (z.B. bereits ausgecheckt): "Equipment ist bereits an Projekt [X] ausgecheckt."
- **500 Server Error**: "Serverfehler. Bitte später erneut versuchen."

## API-Endpunkte

Bestehende scanner-service Endpunkte (alle mit `Authorization: Bearer <token>` + `X-Tenant-ID` Header):

### Auth
| Methode | Endpunkt | Beschreibung |
|---------|----------|-------------|
| POST | `/api/v1/auth/login` | Login → Access + Refresh Token |
| POST | `/api/v1/auth/refresh` | Token erneuern |
| GET | `/api/v1/auth/me` | Aktueller User |

### Scanner
| Methode | Endpunkt | Beschreibung |
|---------|----------|-------------|
| GET | `/api/v1/scan/resolve/{barcode}` | Barcode → Equipment-Info |
| POST | `/api/v1/scan` | Einzelnen Scan verarbeiten |
| POST | `/api/v1/scan/batch` | Batch-Scans (RFID Bulk) |
| POST | `/api/v1/scan/sync` | Offline-Queue syncen |
| GET | `/api/v1/scan/history` | Scan-Historie |
| POST | `/api/v1/scan/devices` | Gerät registrieren |

### Scanner Sessions
| Methode | Endpunkt | Beschreibung |
|---------|----------|-------------|
| POST | `/api/v1/scanner/sessions` | Scan-Session starten (Check-In/Out/Inventur) |
| PUT | `/api/v1/scanner/sessions/{id}/end` | Session beenden |
| POST | `/api/v1/scanner/sessions/{id}/scan` | Scan in Session |
| GET | `/api/v1/scanner/sessions/{id}/protocol` | Session-Protokoll |

### Scan Request Body
```json
{
  "barcode": "ABC123",
  "scan_type": "in|out",
  "user_id": "user-uuid",
  "device_id": "device-uuid",
  "device_type": "scanner",
  "project_id": "optional-project-id",
  "location_id": "optional-location-id",
  "notes": "optional notes"
}
```

## Workflows

### Check-Out
1. Projekt auswählen (Suche/Liste via project-service)
2. Scanner-Session starten (`POST /sessions` mit type: "out")
3. Equipment scannen (Barcode einzeln oder RFID Bulk)
4. Gescannte Items in Liste anzeigen, Items entfernen möglich
5. Check-Out abschließen → Session beenden (`PUT /sessions/{id}/end`)

### Check-In
1. Scanner-Session starten (`POST /sessions` mit type: "in")
2. Equipment scannen
3. Zustandsbewertung pro Item (1-5 Sterne)
4. Optional: Notiz + Foto bei Schäden
5. Check-In abschließen → Session beenden

**Foto-Capture:**
- System Camera Intent (kein eigener Camera-Preview nötig)
- Foto wird als JPEG komprimiert (max 1920px, ~80% Qualität)
- Upload via `POST /api/v1/scan/sessions/{id}/scan` als Multipart oder Base64 im Body
- Bei Offline: Foto lokal in App-Cache, Upload mit Queue-Sync

### Inventur
1. Lagerbereich/Zone auswählen (Liste via `GET /api/v1/warehouse/zones`)
2. Scanner-Session starten (`POST /sessions` mit type: "inventory")
3. Alle Items scannen (RFID Bulk ideal)
4. Soll/Ist Abgleich anzeigen:
   - **Gefunden** (grün): Gescannt und erwartet
   - **Fehlend** (rot): Erwartet aber nicht gescannt
   - **Unerwartet** (gelb): Gescannt aber nicht in Zone erwartet
5. Inventur abschließen → Session beenden

### Equipment-Detail
1. Item scannen
2. Info anzeigen: Name, Kategorie, Status, Standort, Historie (via `/api/v1/scan/resolve/{barcode}`)
3. Optional: RFID-Tag schreiben/aktualisieren
