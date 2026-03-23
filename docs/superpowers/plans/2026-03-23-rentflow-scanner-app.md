# RentFlow Scanner App — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android scanner app for the CF-H906 UHF PDA that integrates with RentFlow's scanner-service for Check-In/Out, Inventur, and Equipment lookup workflows.

**Architecture:** Clean Architecture (MVVM) with Jetpack Compose UI, Hilt DI, Retrofit for API calls, Room for offline queue. Hardware scanner abstracted behind interface with mock implementation until SDK available.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Hilt, Retrofit + OkHttp, Room, CameraX + ML Kit, WorkManager, DataStore, EncryptedSharedPreferences, Coil

**Spec:** `docs/superpowers/specs/2026-03-23-rentflow-scanner-app-design.md`

---

## File Structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/rentflow/scanner/
│   ├── RentFlowScannerApp.kt                    # Application class (Hilt entry point)
│   ├── MainActivity.kt                           # Single Activity, Compose host
│   ├── data/
│   │   ├── api/
│   │   │   ├── AuthApi.kt                        # Retrofit interface: login, refresh, me
│   │   │   ├── ScannerApi.kt                     # Retrofit interface: scan, sessions, resolve
│   │   │   ├── ProjectApi.kt                     # Retrofit interface: project list
│   │   │   ├── WarehouseApi.kt                   # Retrofit interface: zones list
│   │   │   ├── AuthInterceptor.kt                # OkHttp interceptor: add JWT + Tenant-ID headers
│   │   │   ├── TokenAuthenticator.kt             # OkHttp authenticator: auto-refresh on 401
│   │   │   └── ApiResponse.kt                    # Generic API response wrapper
│   │   ├── db/
│   │   │   ├── ScannerDatabase.kt                # Room database
│   │   │   ├── PendingScanDao.kt                 # DAO for offline queue
│   │   │   └── PendingScanEntity.kt              # Room entity
│   │   ├── hardware/
│   │   │   ├── HardwareScanner.kt                # Interface for barcode + RFID
│   │   │   ├── MockHardwareScanner.kt            # Mock implementation for dev/testing
│   │   │   └── BroadcastScannerReceiver.kt       # BroadcastReceiver for hardware scan events
│   │   ├── repository/
│   │   │   ├── AuthRepository.kt                 # Auth logic: login, refresh, token storage
│   │   │   ├── ScannerRepository.kt              # Scanner operations + offline queue
│   │   │   ├── ProjectRepository.kt              # Project data
│   │   │   └── WarehouseRepository.kt            # Warehouse zones
│   │   └── preferences/
│   │       ├── TokenManager.kt                   # EncryptedSharedPreferences for JWT
│   │       └── SettingsDataStore.kt              # DataStore for app settings
│   ├── domain/
│   │   └── model/
│   │       ├── User.kt                           # User domain model
│   │       ├── Equipment.kt                      # Equipment domain model
│   │       ├── ScanResult.kt                     # Scan result domain model
│   │       ├── ScanSession.kt                    # Scan session domain model
│   │       ├── Project.kt                        # Project domain model
│   │       ├── WarehouseZone.kt                  # Warehouse zone domain model
│   │       └── PendingScan.kt                    # Pending scan domain model
│   ├── ui/
│   │   ├── navigation/
│   │   │   └── AppNavigation.kt                  # NavHost + routes
│   │   ├── theme/
│   │   │   ├── Color.kt                          # RentFlow colors
│   │   │   ├── Theme.kt                          # Material 3 dark theme
│   │   │   └── Type.kt                           # Typography
│   │   ├── components/
│   │   │   ├── ScanFab.kt                        # Floating scan button
│   │   │   ├── EquipmentCard.kt                  # Equipment info card
│   │   │   ├── StatusBadge.kt                    # Status badge (checked-in/out)
│   │   │   ├── PendingQueueBadge.kt              # Offline queue badge
│   │   │   ├── ErrorScreen.kt                    # Global error states
│   │   │   └── LoadingScreen.kt                  # Loading indicator
│   │   ├── login/
│   │   │   ├── LoginScreen.kt                    # Login UI
│   │   │   └── LoginViewModel.kt                 # Login logic
│   │   ├── home/
│   │   │   ├── HomeScreen.kt                     # Dashboard UI
│   │   │   └── HomeViewModel.kt                  # Dashboard logic
│   │   ├── scan/
│   │   │   ├── ScanScreen.kt                     # Scan UI (hardware + camera fallback)
│   │   │   └── ScanViewModel.kt                  # Scan logic
│   │   ├── checkout/
│   │   │   ├── CheckOutScreen.kt                 # Check-Out UI
│   │   │   └── CheckOutViewModel.kt              # Check-Out logic
│   │   ├── checkin/
│   │   │   ├── CheckInScreen.kt                  # Check-In UI
│   │   │   └── CheckInViewModel.kt               # Check-In logic
│   │   ├── inventory/
│   │   │   ├── InventoryScreen.kt                # Inventur UI
│   │   │   └── InventoryViewModel.kt             # Inventur logic
│   │   ├── equipment/
│   │   │   ├── EquipmentDetailScreen.kt          # Equipment detail UI
│   │   │   └── EquipmentDetailViewModel.kt       # Equipment detail logic
│   │   └── settings/
│   │       ├── SettingsScreen.kt                 # Settings UI
│   │       └── SettingsViewModel.kt              # Settings logic
│   ├── di/
│   │   ├── AppModule.kt                          # Hilt: OkHttp, Retrofit, Room, DataStore
│   │   ├── HardwareModule.kt                     # Hilt: HardwareScanner binding
│   │   └── RepositoryModule.kt                   # Hilt: Repository bindings
│   └── worker/
│       └── SyncWorker.kt                         # WorkManager: sync offline queue
├── res/
│   ├── values/strings.xml                        # German strings (default)
│   └── values-en/strings.xml                     # English strings
app/src/test/java/com/rentflow/scanner/
├── data/
│   ├── repository/AuthRepositoryTest.kt
│   ├── repository/ScannerRepositoryTest.kt
│   └── api/TokenAuthenticatorTest.kt
├── ui/
│   ├── login/LoginViewModelTest.kt
│   ├── scan/ScanViewModelTest.kt
│   ├── checkout/CheckOutViewModelTest.kt
│   ├── checkin/CheckInViewModelTest.kt
│   └── inventory/InventoryViewModelTest.kt
└── worker/SyncWorkerTest.kt
```

---

## Task 1: Project Setup — Kotlin + Compose + Hilt

**Files:**
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/rentflow/scanner/RentFlowScannerApp.kt`
- Create: `app/src/main/java/com/rentflow/scanner/MainActivity.kt`
- Create: `app/src/main/java/com/rentflow/scanner/ui/theme/Color.kt`
- Create: `app/src/main/java/com/rentflow/scanner/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/rentflow/scanner/ui/theme/Type.kt`
- Delete: `app/src/main/java/com/example/scanner/` (old package)

- [ ] **Step 1: Update `gradle/libs.versions.toml` with all dependencies**

```toml
[versions]
agp = "9.1.0"
kotlin = "2.1.10"
coreKtx = "1.15.0"
lifecycleRuntimeKtx = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.12.01"
hilt = "2.53.1"
hiltNavigationCompose = "1.2.0"
retrofit = "2.11.0"
okhttp = "4.12.0"
room = "2.6.1"
datastore = "1.1.1"
workmanager = "2.10.0"
camerax = "1.4.1"
mlkitBarcode = "17.3.0"
coil = "2.7.0"
securityCrypto = "1.1.0-alpha06"
navigation = "2.8.5"
junit = "4.13.2"
junitVersion = "1.2.1"
espressoCore = "3.6.1"
mockk = "1.13.13"
coroutinesTest = "1.9.0"
ksp = "2.1.10-1.0.31"
material = "1.12.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
workmanager = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workmanager" }
camerax-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
camerax-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
camerax-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
camerax-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }
mlkit-barcode = { group = "com.google.mlkit", name = "barcode-scanning", version.ref = "mlkitBarcode" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: Update root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 3: Update `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.rentflow.scanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rentflow.scanner"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Preferences
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)

    // Background
    implementation(libs.workmanager)

    // Camera
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)

    // Image Loading
    implementation(libs.coil.compose)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

- [ ] **Step 4: Delete old package, create new package structure**

```bash
rm -rf app/src/main/java/com/example
mkdir -p app/src/main/java/com/rentflow/scanner/{data/{api,db,hardware,repository,preferences},domain/model,ui/{navigation,theme,components,login,home,scan,checkout,checkin,inventory,equipment,settings},di,worker}
mkdir -p app/src/test/java/com/rentflow/scanner/{data/repository,ui/{login,scan,checkout,checkin,inventory},worker}
```

- [ ] **Step 5: Create `RentFlowScannerApp.kt`**

```kotlin
package com.rentflow.scanner

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RentFlowScannerApp : Application()
```

- [ ] **Step 6: Create theme files**

`Color.kt`:
```kotlin
package com.rentflow.scanner.ui.theme

import androidx.compose.ui.graphics.Color

val Cyan = Color(0xFF00D4FF)
val Purple = Color(0xFF8B5CF6)
val DarkBackground = Color(0xFF0A0F1A)
val CardBackground = Color(0xFF111827)
val TextPrimary = Color(0xFFE2E8F0)
val TextSecondary = Color(0xFF94A3B8)
val Success = Color(0xFF22C55E)
val Warning = Color(0xFFF59E0B)
val Error = Color(0xFFEF4444)
```

`Type.kt`:
```kotlin
package com.rentflow.scanner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
    bodyLarge = TextStyle(fontSize = 16.sp, color = TextPrimary),
    bodyMedium = TextStyle(fontSize = 14.sp, color = TextSecondary),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary),
)
```

`Theme.kt`:
```kotlin
package com.rentflow.scanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Cyan,
    secondary = Purple,
    background = DarkBackground,
    surface = CardBackground,
    onPrimary = DarkBackground,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = Error,
)

@Composable
fun RentFlowScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content,
    )
}
```

- [ ] **Step 7: Create `MainActivity.kt`**

```kotlin
package com.rentflow.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rentflow.scanner.ui.theme.RentFlowScannerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RentFlowScannerTheme {
                // Navigation will be added in Task 3
                androidx.compose.material3.Text("RentFlow Scanner")
            }
        }
    }
}
```

- [ ] **Step 8: Update `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:name=".RentFlowScannerApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Scanner"
        tools:targetApi="34">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait"
            android:theme="@style/Theme.Scanner">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 9: Create string resources**

`app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">RentFlow Scanner</string>
    <string name="login_title">Anmelden</string>
    <string name="login_email">E-Mail</string>
    <string name="login_password">Passwort</string>
    <string name="login_button">Anmelden</string>
    <string name="login_qr">QR-Code Login</string>
    <string name="home_title">Scanner</string>
    <string name="nav_home">Start</string>
    <string name="nav_scan">Scannen</string>
    <string name="nav_checkout">Check-Out</string>
    <string name="nav_checkin">Check-In</string>
    <string name="nav_inventory">Inventur</string>
    <string name="nav_settings">Einstellungen</string>
    <string name="scan_ready">Bereit zum Scannen</string>
    <string name="scan_camera_fallback">Kamera-Scanner</string>
    <string name="checkout_title">Check-Out</string>
    <string name="checkout_select_project">Projekt auswählen</string>
    <string name="checkout_scan_items">Equipment scannen</string>
    <string name="checkout_confirm">Check-Out abschließen</string>
    <string name="checkin_title">Check-In</string>
    <string name="checkin_condition">Zustand bewerten</string>
    <string name="checkin_notes">Anmerkungen</string>
    <string name="checkin_photo">Foto aufnehmen</string>
    <string name="checkin_confirm">Check-In abschließen</string>
    <string name="inventory_title">Inventur</string>
    <string name="inventory_select_zone">Lagerbereich wählen</string>
    <string name="inventory_found">Gefunden</string>
    <string name="inventory_missing">Fehlend</string>
    <string name="inventory_unexpected">Unerwartet</string>
    <string name="inventory_confirm">Inventur abschließen</string>
    <string name="equipment_detail_title">Equipment</string>
    <string name="equipment_write_rfid">RFID-Tag schreiben</string>
    <string name="equipment_not_found">Unbekannter Barcode: %1$s</string>
    <string name="settings_title">Einstellungen</string>
    <string name="settings_server_url">Server-URL</string>
    <string name="settings_language">Sprache</string>
    <string name="settings_hardware">Hardware</string>
    <string name="pending_scans">%1$d Scans ausstehend</string>
    <string name="error_no_connection">Keine Verbindung zum Server</string>
    <string name="error_unauthorized">Sitzung abgelaufen</string>
    <string name="error_server">Serverfehler</string>
    <string name="error_conflict">Equipment ist bereits ausgecheckt</string>
    <string name="offline_detail_unavailable">Keine Internetverbindung — Details nicht verfügbar</string>
    <string name="rfid_overwrite_title">Tag überschreiben?</string>
    <string name="rfid_write_failed">RFID-Schreibvorgang fehlgeschlagen</string>
    <string name="retry">Wiederholen</string>
    <string name="cancel">Abbrechen</string>
    <string name="confirm">Bestätigen</string>
    <string name="ok">OK</string>
</resources>
```

`app/src/main/res/values-en/strings.xml`:
```xml
<resources>
    <string name="app_name">RentFlow Scanner</string>
    <string name="login_title">Sign In</string>
    <string name="login_email">Email</string>
    <string name="login_password">Password</string>
    <string name="login_button">Sign In</string>
    <string name="login_qr">QR Code Login</string>
    <string name="home_title">Scanner</string>
    <string name="nav_home">Home</string>
    <string name="nav_scan">Scan</string>
    <string name="nav_checkout">Check-Out</string>
    <string name="nav_checkin">Check-In</string>
    <string name="nav_inventory">Inventory</string>
    <string name="nav_settings">Settings</string>
    <string name="scan_ready">Ready to scan</string>
    <string name="scan_camera_fallback">Camera Scanner</string>
    <string name="checkout_title">Check-Out</string>
    <string name="checkout_select_project">Select project</string>
    <string name="checkout_scan_items">Scan equipment</string>
    <string name="checkout_confirm">Complete Check-Out</string>
    <string name="checkin_title">Check-In</string>
    <string name="checkin_condition">Rate condition</string>
    <string name="checkin_notes">Notes</string>
    <string name="checkin_photo">Take photo</string>
    <string name="checkin_confirm">Complete Check-In</string>
    <string name="inventory_title">Inventory</string>
    <string name="inventory_select_zone">Select zone</string>
    <string name="inventory_found">Found</string>
    <string name="inventory_missing">Missing</string>
    <string name="inventory_unexpected">Unexpected</string>
    <string name="inventory_confirm">Complete Inventory</string>
    <string name="equipment_detail_title">Equipment</string>
    <string name="equipment_write_rfid">Write RFID Tag</string>
    <string name="equipment_not_found">Unknown barcode: %1$s</string>
    <string name="settings_title">Settings</string>
    <string name="settings_server_url">Server URL</string>
    <string name="settings_language">Language</string>
    <string name="settings_hardware">Hardware</string>
    <string name="pending_scans">%1$d scans pending</string>
    <string name="error_no_connection">No connection to server</string>
    <string name="error_unauthorized">Session expired</string>
    <string name="error_server">Server error</string>
    <string name="error_conflict">Equipment is already checked out</string>
    <string name="offline_detail_unavailable">No internet connection — details unavailable</string>
    <string name="rfid_overwrite_title">Overwrite tag?</string>
    <string name="rfid_write_failed">RFID write failed</string>
    <string name="retry">Retry</string>
    <string name="cancel">Cancel</string>
    <string name="confirm">Confirm</string>
    <string name="ok">OK</string>
</resources>
```

- [ ] **Step 10: Build and verify project compiles**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 11: Commit**

```bash
git add -A && git commit -m "feat: project setup — Kotlin, Compose, Hilt, all dependencies, RentFlow theme, i18n"
```

---

## Task 2: Domain Models + API Layer

**Files:**
- Create: `app/src/main/java/com/rentflow/scanner/domain/model/*.kt`
- Create: `app/src/main/java/com/rentflow/scanner/data/api/*.kt`

- [ ] **Step 1: Create domain models**

`domain/model/User.kt`:
```kotlin
package com.rentflow.scanner.domain.model

data class User(
    val id: String,
    val email: String,
    val name: String,
    val roles: List<String>,
    val tenantId: String,
)
```

`domain/model/Equipment.kt`:
```kotlin
package com.rentflow.scanner.domain.model

data class Equipment(
    val id: String,
    val barcode: String,
    val name: String,
    val category: String,
    val status: EquipmentStatus,
    val location: String?,
    val projectName: String?,
    val rfidTag: String?,
    val imageUrl: String?,
)

enum class EquipmentStatus {
    AVAILABLE, CHECKED_OUT, IN_MAINTENANCE, DAMAGED, RETIRED
}
```

`domain/model/ScanResult.kt`:
```kotlin
package com.rentflow.scanner.domain.model

data class ScanResult(
    val id: String,
    val barcode: String,
    val equipment: Equipment?,
    val timestamp: Long,
    val scanType: ScanType,
)

enum class ScanType { IN, OUT, INVENTORY, LOOKUP }
```

`domain/model/ScanSession.kt`:
```kotlin
package com.rentflow.scanner.domain.model

data class ScanSession(
    val id: String,
    val type: ScanType,
    val scannedItems: List<ScanResult>,
    val startedAt: Long,
)
```

`domain/model/Project.kt`:
```kotlin
package com.rentflow.scanner.domain.model

data class Project(
    val id: String,
    val name: String,
    val status: String,
    val color: String?,
)
```

`domain/model/WarehouseZone.kt`:
```kotlin
package com.rentflow.scanner.domain.model

data class WarehouseZone(
    val id: String,
    val name: String,
    val expectedItemCount: Int,
)
```

`domain/model/PendingScan.kt`:
```kotlin
package com.rentflow.scanner.domain.model

data class PendingScan(
    val id: Long = 0,
    val barcode: String,
    val scanType: String,
    val projectId: String?,
    val notes: String?,
    val timestamp: Long,
    val retryCount: Int = 0,
    val failed: Boolean = false,
)
```

- [ ] **Step 2: Create API response wrapper**

`data/api/ApiResponse.kt`:
```kotlin
package com.rentflow.scanner.data.api

data class ApiResponse<T>(
    val data: T?,
    val message: String?,
)

data class LoginResponse(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Int,
    val token_type: String,
)

data class LoginRequest(
    val email: String,
    val password: String,
)

data class RefreshRequest(
    val refresh_token: String,
)

data class ScanRequest(
    val barcode: String,
    val scan_type: String,
    val user_id: String,
    val device_id: String,
    val device_type: String = "scanner",
    val project_id: String? = null,
    val location_id: String? = null,
    val notes: String? = null,
)

data class SessionCreateRequest(
    val type: String,
    val project_id: String? = null,
    val location_id: String? = null,
)
```

- [ ] **Step 3: Create Retrofit API interfaces**

`data/api/AuthApi.kt`:
```kotlin
package com.rentflow.scanner.data.api

import com.rentflow.scanner.domain.model.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginResponse>>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<ApiResponse<LoginResponse>>

    @GET("api/v1/auth/me")
    suspend fun me(): Response<ApiResponse<User>>
}
```

`data/api/ScannerApi.kt`:
```kotlin
package com.rentflow.scanner.data.api

import com.rentflow.scanner.domain.model.Equipment
import com.rentflow.scanner.domain.model.ScanResult
import com.rentflow.scanner.domain.model.ScanSession
import retrofit2.Response
import retrofit2.http.*

interface ScannerApi {
    @GET("api/v1/scan/resolve/{barcode}")
    suspend fun resolveBarcode(@Path("barcode") barcode: String): Response<ApiResponse<Equipment>>

    @POST("api/v1/scan")
    suspend fun scan(@Body request: ScanRequest): Response<ApiResponse<ScanResult>>

    @POST("api/v1/scan/batch")
    suspend fun scanBatch(@Body requests: List<ScanRequest>): Response<ApiResponse<List<ScanResult>>>

    @POST("api/v1/scan/sync")
    suspend fun syncOffline(@Body requests: List<ScanRequest>): Response<ApiResponse<Unit>>

    @POST("api/v1/scanner/sessions")
    suspend fun createSession(@Body request: SessionCreateRequest): Response<ApiResponse<ScanSession>>

    @PUT("api/v1/scanner/sessions/{id}/end")
    suspend fun endSession(@Path("id") id: String): Response<ApiResponse<ScanSession>>

    @POST("api/v1/scanner/sessions/{id}/scan")
    suspend fun sessionScan(@Path("id") id: String, @Body request: ScanRequest): Response<ApiResponse<ScanResult>>

    @GET("api/v1/scanner/sessions/{id}/protocol")
    suspend fun sessionProtocol(@Path("id") id: String): Response<ApiResponse<List<ScanResult>>>
}
```

`data/api/ProjectApi.kt`:
```kotlin
package com.rentflow.scanner.data.api

import com.rentflow.scanner.domain.model.Project
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ProjectApi {
    @GET("api/v1/projects")
    suspend fun listProjects(@Query("search") search: String? = null): Response<ApiResponse<List<Project>>>
}
```

`data/api/WarehouseApi.kt`:
```kotlin
package com.rentflow.scanner.data.api

import com.rentflow.scanner.domain.model.WarehouseZone
import retrofit2.Response
import retrofit2.http.GET

interface WarehouseApi {
    @GET("api/v1/warehouse/zones")
    suspend fun listZones(): Response<ApiResponse<List<WarehouseZone>>>
}
```

- [ ] **Step 4: Build and verify**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: domain models and Retrofit API interfaces"
```

---

## Task 3: Token Management + Auth Interceptor + DI

**Files:**
- Create: `app/src/main/java/com/rentflow/scanner/data/preferences/TokenManager.kt`
- Create: `app/src/main/java/com/rentflow/scanner/data/preferences/SettingsDataStore.kt`
- Create: `app/src/main/java/com/rentflow/scanner/data/api/AuthInterceptor.kt`
- Create: `app/src/main/java/com/rentflow/scanner/data/api/TokenAuthenticator.kt`
- Create: `app/src/main/java/com/rentflow/scanner/di/AppModule.kt`
- Create: `app/src/main/java/com/rentflow/scanner/di/RepositoryModule.kt`
- Create: `app/src/main/java/com/rentflow/scanner/di/HardwareModule.kt`
- Test: `app/src/test/java/com/rentflow/scanner/data/api/TokenAuthenticatorTest.kt`

- [ ] **Step 1: Write test for TokenAuthenticator**

```kotlin
package com.rentflow.scanner.data.api

import com.rentflow.scanner.data.preferences.TokenManager
import io.mockk.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TokenAuthenticatorTest {
    private lateinit var server: MockWebServer
    private lateinit var tokenManager: TokenManager

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        tokenManager = mockk(relaxed = true)
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `returns null when no refresh token available`() {
        every { tokenManager.getRefreshToken() } returns null
        val authenticator = TokenAuthenticator(tokenManager, server.url("/").toString())
        val response = okhttp3.Response.Builder()
            .request(Request.Builder().url(server.url("/test")).build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

        val result = authenticator.authenticate(null, response)
        assertNull(result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew testDebugUnitTest --tests "com.rentflow.scanner.data.api.TokenAuthenticatorTest"
```
Expected: FAIL — class not found

- [ ] **Step 3: Create `TokenManager.kt`**

```kotlin
package com.rentflow.scanner.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "rentflow_tokens",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)
    fun getTenantId(): String? = prefs.getString(KEY_TENANT_ID, null)

    fun saveTokens(accessToken: String, refreshToken: String, tenantId: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_TENANT_ID, tenantId)
            .apply()
    }

    fun clearTokens() {
        prefs.edit().clear().apply()
    }

    fun hasTokens(): Boolean = getAccessToken() != null

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TENANT_ID = "tenant_id"
    }
}
```

- [ ] **Step 4: Create `SettingsDataStore.kt`**

```kotlin
package com.rentflow.scanner.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val serverUrl: Flow<String> = context.dataStore.data.map { it[KEY_SERVER_URL] ?: "" }
    val language: Flow<String> = context.dataStore.data.map { it[KEY_LANGUAGE] ?: "de" }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[KEY_SERVER_URL] = url }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = lang }
    }

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
    }
}
```

- [ ] **Step 5: Create `AuthInterceptor.kt`**

```kotlin
package com.rentflow.scanner.data.api

import com.rentflow.scanner.data.preferences.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        tokenManager.getAccessToken()?.let {
            request.addHeader("Authorization", "Bearer $it")
        }
        tokenManager.getTenantId()?.let {
            request.addHeader("X-Tenant-ID", it)
        }
        return chain.proceed(request.build())
    }
}
```

- [ ] **Step 6: Create `TokenAuthenticator.kt`**

```kotlin
package com.rentflow.scanner.data.api

import com.google.gson.Gson
import com.rentflow.scanner.data.preferences.TokenManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val baseUrl: String,
) : Authenticator {
    private val gson = Gson()
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val refreshToken = tokenManager.getRefreshToken() ?: return null

        synchronized(lock) {
            // Check if another thread already refreshed
            val currentToken = tokenManager.getAccessToken()
            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (currentToken != null && currentToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            // Do the refresh
            val refreshBody = gson.toJson(RefreshRequest(refreshToken))
                .toRequestBody("application/json".toMediaType())
            val refreshRequest = Request.Builder()
                .url("${baseUrl}api/v1/auth/refresh")
                .post(refreshBody)
                .build()

            val client = OkHttpClient()
            val refreshResponse = client.newCall(refreshRequest).execute()
            if (!refreshResponse.isSuccessful) {
                tokenManager.clearTokens()
                return null
            }

            val body = refreshResponse.body?.string() ?: return null
            val apiResponse = gson.fromJson(body, ApiResponse::class.java)
            // Parse the data field as LoginResponse
            val dataJson = gson.toJson(apiResponse.data)
            val loginResponse = gson.fromJson(dataJson, LoginResponse::class.java)

            tokenManager.saveTokens(
                loginResponse.access_token,
                loginResponse.refresh_token,
                tokenManager.getTenantId() ?: "",
            )

            return response.request.newBuilder()
                .header("Authorization", "Bearer ${loginResponse.access_token}")
                .build()
        }
    }
}
```

- [ ] **Step 7: Create `AppModule.kt`**

```kotlin
package com.rentflow.scanner.di

import android.content.Context
import androidx.room.Room
import com.rentflow.scanner.data.api.*
import com.rentflow.scanner.data.db.ScannerDatabase
import com.rentflow.scanner.data.preferences.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Default URL — will be overridden by SettingsDataStore value
    private const val DEFAULT_BASE_URL = "http://localhost:8005/"

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenManager: TokenManager,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .authenticator(TokenAuthenticator(tokenManager, DEFAULT_BASE_URL))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(DEFAULT_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideScannerApi(retrofit: Retrofit): ScannerApi = retrofit.create(ScannerApi::class.java)

    @Provides
    @Singleton
    fun provideProjectApi(retrofit: Retrofit): ProjectApi = retrofit.create(ProjectApi::class.java)

    @Provides
    @Singleton
    fun provideWarehouseApi(retrofit: Retrofit): WarehouseApi = retrofit.create(WarehouseApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ScannerDatabase {
        return Room.databaseBuilder(context, ScannerDatabase::class.java, "scanner_db").build()
    }

    @Provides
    fun providePendingScanDao(db: ScannerDatabase) = db.pendingScanDao()
}
```

- [ ] **Step 8: Create `HardwareModule.kt`**

```kotlin
package com.rentflow.scanner.di

import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.hardware.MockHardwareScanner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HardwareModule {
    @Binds
    @Singleton
    abstract fun bindHardwareScanner(impl: MockHardwareScanner): HardwareScanner
}
```

- [ ] **Step 9: Create `RepositoryModule.kt`**

```kotlin
package com.rentflow.scanner.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule
// Repositories use @Inject constructor, no manual bindings needed
```

- [ ] **Step 10: Run test**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew testDebugUnitTest --tests "com.rentflow.scanner.data.api.TokenAuthenticatorTest"
```
Expected: PASS

- [ ] **Step 11: Commit**

```bash
git add -A && git commit -m "feat: token management, auth interceptor, DI modules"
```

---

## Task 4: Room Database + Offline Queue

**Files:**
- Create: `app/src/main/java/com/rentflow/scanner/data/db/PendingScanEntity.kt`
- Create: `app/src/main/java/com/rentflow/scanner/data/db/PendingScanDao.kt`
- Create: `app/src/main/java/com/rentflow/scanner/data/db/ScannerDatabase.kt`
- Create: `app/src/main/java/com/rentflow/scanner/worker/SyncWorker.kt`

- [ ] **Step 1: Create `PendingScanEntity.kt`**

```kotlin
package com.rentflow.scanner.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_scans")
data class PendingScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String,
    val scanType: String,
    val projectId: String?,
    val notes: String?,
    val timestamp: Long,
    val retryCount: Int = 0,
    val failed: Boolean = false,
    val userId: String,
    val deviceId: String,
)
```

- [ ] **Step 2: Create `PendingScanDao.kt`**

```kotlin
package com.rentflow.scanner.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingScanDao {
    @Insert
    suspend fun insert(scan: PendingScanEntity): Long

    @Query("SELECT * FROM pending_scans WHERE failed = 0 ORDER BY timestamp ASC")
    suspend fun getPending(): List<PendingScanEntity>

    @Query("SELECT * FROM pending_scans ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<PendingScanEntity>>

    @Query("SELECT COUNT(*) FROM pending_scans WHERE failed = 0")
    fun observePendingCount(): Flow<Int>

    @Query("UPDATE pending_scans SET retryCount = retryCount + 1, failed = CASE WHEN retryCount >= 4 THEN 1 ELSE 0 END WHERE id = :id")
    suspend fun incrementRetry(id: Long)

    @Delete
    suspend fun delete(scan: PendingScanEntity)

    @Query("DELETE FROM pending_scans WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_scans SET failed = 0, retryCount = 0 WHERE id = :id")
    suspend fun resetRetry(id: Long)
}
```

- [ ] **Step 3: Create `ScannerDatabase.kt`**

```kotlin
package com.rentflow.scanner.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PendingScanEntity::class], version = 1, exportSchema = false)
abstract class ScannerDatabase : RoomDatabase() {
    abstract fun pendingScanDao(): PendingScanDao
}
```

- [ ] **Step 4: Create `SyncWorker.kt`**

```kotlin
package com.rentflow.scanner.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.rentflow.scanner.data.api.ScanRequest
import com.rentflow.scanner.data.api.ScannerApi
import com.rentflow.scanner.data.db.PendingScanDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scannerApi: ScannerApi,
    private val pendingScanDao: PendingScanDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending = pendingScanDao.getPending()
        if (pending.isEmpty()) return Result.success()

        val requests = pending.map { scan ->
            ScanRequest(
                barcode = scan.barcode,
                scan_type = scan.scanType,
                user_id = scan.userId,
                device_id = scan.deviceId,
                project_id = scan.projectId,
                notes = scan.notes,
            )
        }

        return try {
            val response = scannerApi.syncOffline(requests)
            if (response.isSuccessful) {
                pending.forEach { pendingScanDao.delete(it) }
                Result.success()
            } else {
                pending.forEach { pendingScanDao.incrementRetry(it.id) }
                Result.retry()
            }
        } catch (e: Exception) {
            pending.forEach { pendingScanDao.incrementRetry(it.id) }
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync_scans",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
```

- [ ] **Step 5: Build and verify**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: Room database, offline queue, sync worker"
```

---

## Task 5: Hardware Scanner Interface + Mock

**Files:**
- Create: `app/src/main/java/com/rentflow/scanner/data/hardware/HardwareScanner.kt`
- Create: `app/src/main/java/com/rentflow/scanner/data/hardware/MockHardwareScanner.kt`
- Create: `app/src/main/java/com/rentflow/scanner/data/hardware/BroadcastScannerReceiver.kt`

- [ ] **Step 1: Create `HardwareScanner.kt` interface**

```kotlin
package com.rentflow.scanner.data.hardware

import kotlinx.coroutines.flow.Flow

data class BarcodeScanEvent(val barcode: String, val format: String)
data class RfidReadEvent(val epc: String, val rssi: Int)
data class RfidWriteResult(val success: Boolean, val error: String? = null)

interface HardwareScanner {
    val barcodeScanEvents: Flow<BarcodeScanEvent>
    val rfidReadEvents: Flow<RfidReadEvent>

    fun startBarcodeScan()
    fun stopBarcodeScan()
    fun startRfidRead()
    fun startRfidBulkRead()
    fun stopRfid()
    suspend fun writeRfidTag(epc: String): RfidWriteResult
    fun isRfidAvailable(): Boolean
    fun destroy()
}
```

- [ ] **Step 2: Create `MockHardwareScanner.kt`**

```kotlin
package com.rentflow.scanner.data.hardware

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject

class MockHardwareScanner @Inject constructor() : HardwareScanner {
    private val _barcodeScanEvents = MutableSharedFlow<BarcodeScanEvent>()
    override val barcodeScanEvents: Flow<BarcodeScanEvent> = _barcodeScanEvents

    private val _rfidReadEvents = MutableSharedFlow<RfidReadEvent>()
    override val rfidReadEvents: Flow<RfidReadEvent> = _rfidReadEvents

    private val mockBarcodes = listOf("EQ-001", "EQ-002", "EQ-003", "EQ-004", "EQ-005")
    private val mockRfidTags = listOf("E200001", "E200002", "E200003")
    private var scanIndex = 0

    override fun startBarcodeScan() {
        // In mock mode, call simulateBarcodeScan() to emit events
    }

    override fun stopBarcodeScan() {}

    override fun startRfidRead() {}

    override fun startRfidBulkRead() {}

    override fun stopRfid() {}

    override suspend fun writeRfidTag(epc: String): RfidWriteResult {
        delay(500) // Simulate write time
        return RfidWriteResult(success = true)
    }

    override fun isRfidAvailable(): Boolean = true

    override fun destroy() {}

    // Test helpers
    suspend fun simulateBarcodeScan(barcode: String? = null) {
        val code = barcode ?: mockBarcodes[scanIndex++ % mockBarcodes.size]
        _barcodeScanEvents.emit(BarcodeScanEvent(code, "QR_CODE"))
    }

    suspend fun simulateRfidRead(epc: String? = null, rssi: Int = -45) {
        val tag = epc ?: mockRfidTags.random()
        _rfidReadEvents.emit(RfidReadEvent(tag, rssi))
    }
}
```

- [ ] **Step 3: Create `BroadcastScannerReceiver.kt`**

```kotlin
package com.rentflow.scanner.data.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Receives barcode scan events from CF-H906 hardware via broadcast.
 * Action strings and extras will be updated once the SDK is available.
 */
class BroadcastScannerReceiver : BroadcastReceiver() {
    private val _scanEvents = MutableSharedFlow<BarcodeScanEvent>(extraBufferCapacity = 10)
    val scanEvents: Flow<BarcodeScanEvent> = _scanEvents

    override fun onReceive(context: Context?, intent: Intent?) {
        // TODO: Update action/extra keys based on CF-H906 SDK documentation
        val barcode = intent?.getStringExtra("SCAN_BARCODE_DATA") ?: return
        val format = intent.getStringExtra("SCAN_BARCODE_TYPE") ?: "UNKNOWN"
        _scanEvents.tryEmit(BarcodeScanEvent(barcode, format))
    }

    fun getIntentFilter(): IntentFilter {
        // TODO: Update with actual CF-H906 broadcast action
        return IntentFilter("com.cfh906.scanner.BARCODE_SCAN")
    }
}
```

- [ ] **Step 4: Build and verify**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: hardware scanner interface, mock implementation, broadcast receiver"
```

---

## Task 6: Repositories

**Files:**
- Create: `app/src/main/java/com/rentflow/scanner/data/repository/AuthRepository.kt`
- Create: `app/src/main/java/com/rentflow/scanner/data/repository/ScannerRepository.kt`
- Create: `app/src/main/java/com/rentflow/scanner/data/repository/ProjectRepository.kt`
- Create: `app/src/main/java/com/rentflow/scanner/data/repository/WarehouseRepository.kt`
- Test: `app/src/test/java/com/rentflow/scanner/data/repository/AuthRepositoryTest.kt`
- Test: `app/src/test/java/com/rentflow/scanner/data/repository/ScannerRepositoryTest.kt`

- [ ] **Step 1: Write AuthRepository test**

```kotlin
package com.rentflow.scanner.data.repository

import com.rentflow.scanner.data.api.*
import com.rentflow.scanner.data.preferences.TokenManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AuthRepositoryTest {
    private lateinit var authApi: AuthApi
    private lateinit var tokenManager: TokenManager
    private lateinit var repo: AuthRepository

    @Before
    fun setup() {
        authApi = mockk()
        tokenManager = mockk(relaxed = true)
        repo = AuthRepository(authApi, tokenManager)
    }

    @Test
    fun `login saves tokens on success`() = runTest {
        val loginResponse = LoginResponse("access123", "refresh123", 3600, "Bearer")
        coEvery { authApi.login(any()) } returns Response.success(ApiResponse(loginResponse, "ok"))

        val result = repo.login("test@test.com", "pass")

        assertTrue(result.isSuccess)
        verify { tokenManager.saveTokens("access123", "refresh123", any()) }
    }

    @Test
    fun `login returns failure on error`() = runTest {
        coEvery { authApi.login(any()) } returns Response.error(401, okhttp3.ResponseBody.create(null, ""))

        val result = repo.login("test@test.com", "wrong")

        assertTrue(result.isFailure)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew testDebugUnitTest --tests "com.rentflow.scanner.data.repository.AuthRepositoryTest"
```
Expected: FAIL

- [ ] **Step 3: Create `AuthRepository.kt`**

```kotlin
package com.rentflow.scanner.data.repository

import com.rentflow.scanner.data.api.AuthApi
import com.rentflow.scanner.data.api.LoginRequest
import com.rentflow.scanner.data.api.RefreshRequest
import com.rentflow.scanner.data.preferences.TokenManager
import com.rentflow.scanner.domain.model.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager,
) {
    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            val response = authApi.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body()?.data != null) {
                val data = response.body()!!.data!!
                // Extract tenant_id from JWT claims (simplified — use JWT decode)
                tokenManager.saveTokens(data.access_token, data.refresh_token, "")
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentUser(): Result<User> {
        return try {
            val response = authApi.me()
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception("Failed to get user"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isLoggedIn(): Boolean = tokenManager.hasTokens()

    fun logout() {
        tokenManager.clearTokens()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew testDebugUnitTest --tests "com.rentflow.scanner.data.repository.AuthRepositoryTest"
```
Expected: PASS

- [ ] **Step 5: Create `ScannerRepository.kt`**

```kotlin
package com.rentflow.scanner.data.repository

import android.content.Context
import com.rentflow.scanner.data.api.ScanRequest
import com.rentflow.scanner.data.api.ScannerApi
import com.rentflow.scanner.data.api.SessionCreateRequest
import com.rentflow.scanner.data.db.PendingScanDao
import com.rentflow.scanner.data.db.PendingScanEntity
import com.rentflow.scanner.data.preferences.TokenManager
import com.rentflow.scanner.domain.model.Equipment
import com.rentflow.scanner.domain.model.ScanResult
import com.rentflow.scanner.domain.model.ScanSession
import com.rentflow.scanner.worker.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScannerRepository @Inject constructor(
    private val scannerApi: ScannerApi,
    private val pendingScanDao: PendingScanDao,
    private val tokenManager: TokenManager,
    @ApplicationContext private val context: Context,
) {
    suspend fun resolveBarcode(barcode: String): Result<Equipment> {
        return try {
            val response = scannerApi.resolveBarcode(barcode)
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Equipment not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scan(barcode: String, scanType: String, projectId: String? = null, notes: String? = null): Result<ScanResult> {
        val request = ScanRequest(
            barcode = barcode,
            scan_type = scanType,
            user_id = "", // filled from JWT on server
            device_id = android.os.Build.SERIAL,
            project_id = projectId,
            notes = notes,
        )
        return try {
            val response = scannerApi.scan(request)
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                queueOffline(barcode, scanType, projectId, notes)
                Result.failure(Exception(response.body()?.message ?: "Scan failed"))
            }
        } catch (e: Exception) {
            queueOffline(barcode, scanType, projectId, notes)
            Result.failure(e)
        }
    }

    suspend fun createSession(type: String, projectId: String? = null): Result<ScanSession> {
        return try {
            val response = scannerApi.createSession(SessionCreateRequest(type, projectId))
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception("Failed to create session"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun endSession(sessionId: String): Result<ScanSession> {
        return try {
            val response = scannerApi.endSession(sessionId)
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception("Failed to end session"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sessionScan(sessionId: String, barcode: String, scanType: String): Result<ScanResult> {
        val request = ScanRequest(
            barcode = barcode,
            scan_type = scanType,
            user_id = "",
            device_id = android.os.Build.SERIAL,
        )
        return try {
            val response = scannerApi.sessionScan(sessionId, request)
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception("Session scan failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observePendingCount(): Flow<Int> = pendingScanDao.observePendingCount()
    fun observePendingScans() = pendingScanDao.observeAll()

    suspend fun retryPendingScan(id: Long) {
        pendingScanDao.resetRetry(id)
        SyncWorker.enqueue(context)
    }

    suspend fun deletePendingScan(id: Long) {
        pendingScanDao.deleteById(id)
    }

    private suspend fun queueOffline(barcode: String, scanType: String, projectId: String?, notes: String?) {
        pendingScanDao.insert(
            PendingScanEntity(
                barcode = barcode,
                scanType = scanType,
                projectId = projectId,
                notes = notes,
                timestamp = System.currentTimeMillis(),
                userId = "",
                deviceId = android.os.Build.SERIAL,
            )
        )
        SyncWorker.enqueue(context)
    }
}
```

- [ ] **Step 6: Create `ProjectRepository.kt`**

```kotlin
package com.rentflow.scanner.data.repository

import com.rentflow.scanner.data.api.ProjectApi
import com.rentflow.scanner.domain.model.Project
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectApi: ProjectApi,
) {
    suspend fun listProjects(search: String? = null): Result<List<Project>> {
        return try {
            val response = projectApi.listProjects(search)
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception("Failed to load projects"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

- [ ] **Step 7: Create `WarehouseRepository.kt`**

```kotlin
package com.rentflow.scanner.data.repository

import com.rentflow.scanner.data.api.WarehouseApi
import com.rentflow.scanner.domain.model.WarehouseZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarehouseRepository @Inject constructor(
    private val warehouseApi: WarehouseApi,
) {
    suspend fun listZones(): Result<List<WarehouseZone>> {
        return try {
            val response = warehouseApi.listZones()
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception("Failed to load zones"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

- [ ] **Step 8: Build + run tests**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew testDebugUnitTest
```
Expected: All tests PASS

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "feat: repositories — auth, scanner, project, warehouse"
```

---

## Task 7: Navigation + Login Screen

**Files:**
- Create: `app/src/main/java/com/rentflow/scanner/ui/navigation/AppNavigation.kt`
- Create: `app/src/main/java/com/rentflow/scanner/ui/components/ErrorScreen.kt`
- Create: `app/src/main/java/com/rentflow/scanner/ui/components/LoadingScreen.kt`
- Create: `app/src/main/java/com/rentflow/scanner/ui/login/LoginViewModel.kt`
- Create: `app/src/main/java/com/rentflow/scanner/ui/login/LoginScreen.kt`
- Modify: `app/src/main/java/com/rentflow/scanner/MainActivity.kt`
- Test: `app/src/test/java/com/rentflow/scanner/ui/login/LoginViewModelTest.kt`

- [ ] **Step 1: Write LoginViewModel test**

```kotlin
package com.rentflow.scanner.ui.login

import com.rentflow.scanner.data.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        authRepository = mockk()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `login success navigates to home`() = runTest {
        coEvery { authRepository.login(any(), any()) } returns Result.success(Unit)
        viewModel = LoginViewModel(authRepository)

        viewModel.onEmailChange("test@test.com")
        viewModel.onPasswordChange("pass123")
        viewModel.onLoginClick()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.loginSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `login failure shows error`() = runTest {
        coEvery { authRepository.login(any(), any()) } returns Result.failure(Exception("Invalid credentials"))
        viewModel = LoginViewModel(authRepository)

        viewModel.onEmailChange("test@test.com")
        viewModel.onPasswordChange("wrong")
        viewModel.onLoginClick()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew testDebugUnitTest --tests "com.rentflow.scanner.ui.login.LoginViewModelTest"
```
Expected: FAIL

- [ ] **Step 3: Create shared UI components**

`ui/components/LoadingScreen.kt`:
```kotlin
package com.rentflow.scanner.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
```

`ui/components/ErrorScreen.kt`:
```kotlin
package com.rentflow.scanner.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rentflow.scanner.R

@Composable
fun ErrorScreen(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            if (onRetry != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}
```

- [ ] **Step 4: Create `LoginViewModel.kt`**

```kotlin
package com.rentflow.scanner.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun onLoginClick() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "Email und Passwort erforderlich") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = authRepository.login(state.email, state.password)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isLoading = false, loginSuccess = true)
                } else {
                    it.copy(isLoading = false, error = result.exceptionOrNull()?.message ?: "Login fehlgeschlagen")
                }
            }
        }
    }
}
```

- [ ] **Step 5: Create `LoginScreen.kt`**

```kotlin
package com.rentflow.scanner.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentflow.scanner.R
import com.rentflow.scanner.ui.theme.Cyan

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.loginSuccess) {
        if (state.loginSuccess) onLoginSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "RentFlow",
            style = MaterialTheme.typography.headlineLarge,
            color = Cyan,
        )
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp),
        )

        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text(stringResource(R.string.login_email)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text(stringResource(R.string.login_password)) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp))
        }

        Button(
            onClick = viewModel::onLoginClick,
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(R.string.login_button), style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { /* TODO: QR Login - Task 12 */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.login_qr))
        }
    }
}
```

- [ ] **Step 6: Create `AppNavigation.kt`**

```kotlin
package com.rentflow.scanner.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rentflow.scanner.ui.login.LoginScreen

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val SCAN = "scan"
    const val CHECKOUT = "checkout"
    const val CHECKIN = "checkin"
    const val INVENTORY = "inventory"
    const val EQUIPMENT_DETAIL = "equipment/{barcode}"
    const val SETTINGS = "settings"

    fun equipmentDetail(barcode: String) = "equipment/$barcode"
}

@Composable
fun AppNavigation(startDestination: String = Routes.LOGIN) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }
        composable(Routes.HOME) {
            // Placeholder — Task 8
            androidx.compose.material3.Text("Home")
        }
        composable(Routes.SCAN) {
            androidx.compose.material3.Text("Scan")
        }
        composable(Routes.CHECKOUT) {
            androidx.compose.material3.Text("Check-Out")
        }
        composable(Routes.CHECKIN) {
            androidx.compose.material3.Text("Check-In")
        }
        composable(Routes.INVENTORY) {
            androidx.compose.material3.Text("Inventur")
        }
        composable(Routes.EQUIPMENT_DETAIL) {
            androidx.compose.material3.Text("Equipment Detail")
        }
        composable(Routes.SETTINGS) {
            androidx.compose.material3.Text("Settings")
        }
    }
}
```

- [ ] **Step 7: Update `MainActivity.kt`**

```kotlin
package com.rentflow.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rentflow.scanner.data.repository.AuthRepository
import com.rentflow.scanner.ui.navigation.AppNavigation
import com.rentflow.scanner.ui.navigation.Routes
import com.rentflow.scanner.ui.theme.RentFlowScannerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startDest = if (authRepository.isLoggedIn()) Routes.HOME else Routes.LOGIN
        setContent {
            RentFlowScannerTheme {
                AppNavigation(startDestination = startDest)
            }
        }
    }
}
```

- [ ] **Step 8: Run tests**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew testDebugUnitTest --tests "com.rentflow.scanner.ui.login.LoginViewModelTest"
```
Expected: PASS

- [ ] **Step 9: Build full project**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add -A && git commit -m "feat: navigation, login screen with email/password auth"
```

---

## Task 8: Home Screen

**Files:**
- Create: `app/src/main/java/com/rentflow/scanner/ui/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/rentflow/scanner/ui/home/HomeScreen.kt`
- Create: `app/src/main/java/com/rentflow/scanner/ui/components/PendingQueueBadge.kt`
- Modify: `app/src/main/java/com/rentflow/scanner/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Create `PendingQueueBadge.kt`**

```kotlin
package com.rentflow.scanner.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rentflow.scanner.R

@Composable
fun PendingQueueBadge(count: Int) {
    if (count > 0) {
        Badge(containerColor = MaterialTheme.colorScheme.error) {
            Text(stringResource(R.string.pending_scans, count))
        }
    }
}
```

- [ ] **Step 2: Create `HomeViewModel.kt`**

```kotlin
package com.rentflow.scanner.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.repository.AuthRepository
import com.rentflow.scanner.data.repository.ScannerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val userName: String = "",
    val pendingScanCount: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val scannerRepository: ScannerRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        viewModelScope.launch {
            authRepository.getCurrentUser().onSuccess { user ->
                _uiState.update { it.copy(userName = user.name) }
            }
        }
        viewModelScope.launch {
            scannerRepository.observePendingCount().collect { count ->
                _uiState.update { it.copy(pendingScanCount = count) }
            }
        }
    }

    fun logout() {
        authRepository.logout()
    }
}
```

- [ ] **Step 3: Create `HomeScreen.kt`**

```kotlin
package com.rentflow.scanner.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentflow.scanner.R
import com.rentflow.scanner.ui.components.PendingQueueBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToScan: () -> Unit,
    onNavigateToCheckOut: () -> Unit,
    onNavigateToCheckIn: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    PendingQueueBadge(state.pendingScanCount)
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                    IconButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.userName.isNotBlank()) {
                Text("Hallo, ${state.userName}", style = MaterialTheme.typography.headlineMedium)
            }

            WorkflowCard(Icons.Default.QrCodeScanner, stringResource(R.string.nav_scan), onClick = onNavigateToScan)
            WorkflowCard(Icons.Default.Output, stringResource(R.string.nav_checkout), onClick = onNavigateToCheckOut)
            WorkflowCard(Icons.Default.Input, stringResource(R.string.nav_checkin), onClick = onNavigateToCheckIn)
            WorkflowCard(Icons.Default.Inventory, stringResource(R.string.nav_inventory), onClick = onNavigateToInventory)
        }
    }
}

@Composable
private fun WorkflowCard(icon: ImageVector, label: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Text(label, style = MaterialTheme.typography.titleLarge)
        }
    }
}
```

- [ ] **Step 4: Update `AppNavigation.kt` — wire Home screen**

Replace the `composable(Routes.HOME)` placeholder with:
```kotlin
composable(Routes.HOME) {
    HomeScreen(
        onNavigateToScan = { navController.navigate(Routes.SCAN) },
        onNavigateToCheckOut = { navController.navigate(Routes.CHECKOUT) },
        onNavigateToCheckIn = { navController.navigate(Routes.CHECKIN) },
        onNavigateToInventory = { navController.navigate(Routes.INVENTORY) },
        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
        onLogout = {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        },
    )
}
```

- [ ] **Step 5: Build and verify**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: home screen with workflow cards and pending queue badge"
```

---

## Task 9: Scan Screen

**Files:**
- Create: `app/src/main/java/com/rentflow/scanner/ui/scan/ScanViewModel.kt`
- Create: `app/src/main/java/com/rentflow/scanner/ui/scan/ScanScreen.kt`
- Create: `app/src/main/java/com/rentflow/scanner/ui/components/EquipmentCard.kt`
- Create: `app/src/main/java/com/rentflow/scanner/ui/components/StatusBadge.kt`
- Test: `app/src/test/java/com/rentflow/scanner/ui/scan/ScanViewModelTest.kt`
- Modify: `app/src/main/java/com/rentflow/scanner/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Write ScanViewModel test**

```kotlin
package com.rentflow.scanner.ui.scan

import com.rentflow.scanner.data.hardware.BarcodeScanEvent
import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.repository.ScannerRepository
import com.rentflow.scanner.domain.model.Equipment
import com.rentflow.scanner.domain.model.EquipmentStatus
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var scannerRepository: ScannerRepository
    private lateinit var hardwareScanner: HardwareScanner
    private val barcodeFlow = MutableSharedFlow<BarcodeScanEvent>()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        scannerRepository = mockk()
        hardwareScanner = mockk(relaxed = true)
        every { hardwareScanner.barcodeScanEvents } returns barcodeFlow
        every { hardwareScanner.rfidReadEvents } returns MutableSharedFlow()
    }

    @After
    fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `barcode scan resolves equipment`() = runTest {
        val equipment = Equipment("1", "EQ-001", "Speaker", "Audio", EquipmentStatus.AVAILABLE, null, null, null, null)
        coEvery { scannerRepository.resolveBarcode("EQ-001") } returns Result.success(equipment)

        val viewModel = ScanViewModel(scannerRepository, hardwareScanner)
        advanceUntilIdle()

        barcodeFlow.emit(BarcodeScanEvent("EQ-001", "QR_CODE"))
        advanceUntilIdle()

        assertEquals(equipment, viewModel.uiState.value.equipment)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew testDebugUnitTest --tests "com.rentflow.scanner.ui.scan.ScanViewModelTest"
```
Expected: FAIL

- [ ] **Step 3: Create `StatusBadge.kt`**

```kotlin
package com.rentflow.scanner.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.rentflow.scanner.domain.model.EquipmentStatus
import com.rentflow.scanner.ui.theme.*

@Composable
fun StatusBadge(status: EquipmentStatus) {
    val (color, label) = when (status) {
        EquipmentStatus.AVAILABLE -> Success to "Verfügbar"
        EquipmentStatus.CHECKED_OUT -> Warning to "Ausgecheckt"
        EquipmentStatus.IN_MAINTENANCE -> Cyan to "Wartung"
        EquipmentStatus.DAMAGED -> Error to "Beschädigt"
        EquipmentStatus.RETIRED -> TextSecondary to "Ausgemustert"
    }
    Badge(containerColor = color) { Text(label, color = Color.White) }
}
```

- [ ] **Step 4: Create `EquipmentCard.kt`**

```kotlin
package com.rentflow.scanner.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rentflow.scanner.domain.model.Equipment

@Composable
fun EquipmentCard(
    equipment: Equipment,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(equipment.name, style = MaterialTheme.typography.titleLarge)
                StatusBadge(equipment.status)
            }
            Spacer(Modifier.height(8.dp))
            Text("Barcode: ${equipment.barcode}", style = MaterialTheme.typography.bodyMedium)
            Text("Kategorie: ${equipment.category}", style = MaterialTheme.typography.bodyMedium)
            equipment.location?.let {
                Text("Standort: $it", style = MaterialTheme.typography.bodyMedium)
            }
            equipment.projectName?.let {
                Text("Projekt: $it", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
```

- [ ] **Step 5: Create `ScanViewModel.kt`**

```kotlin
package com.rentflow.scanner.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.repository.ScannerRepository
import com.rentflow.scanner.domain.model.Equipment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanUiState(
    val equipment: Equipment? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastBarcode: String? = null,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val hardwareScanner: HardwareScanner,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState

    init {
        viewModelScope.launch {
            hardwareScanner.barcodeScanEvents.collect { event ->
                onBarcodeScanned(event.barcode)
            }
        }
    }

    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, lastBarcode = barcode) }
            scannerRepository.resolveBarcode(barcode).fold(
                onSuccess = { equipment ->
                    _uiState.update { it.copy(isLoading = false, equipment = equipment) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                },
            )
        }
    }

    fun clearResult() {
        _uiState.update { ScanUiState() }
    }
}
```

- [ ] **Step 6: Create `ScanScreen.kt`**

```kotlin
package com.rentflow.scanner.ui.scan

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentflow.scanner.R
import com.rentflow.scanner.ui.components.EquipmentCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onCheckOut: (String) -> Unit,
    onCheckIn: (String) -> Unit,
    onEquipmentDetail: (String) -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_scan)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(32.dp))
            } else if (state.equipment != null) {
                val eq = state.equipment!!
                EquipmentCard(eq, onClick = { onEquipmentDetail(eq.barcode) })
                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { onCheckOut(eq.barcode) },
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Icon(Icons.Default.Output, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.nav_checkout))
                    }
                    Button(
                        onClick = { onCheckIn(eq.barcode) },
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Icon(Icons.Default.Input, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.nav_checkin))
                    }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { viewModel.clearResult() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(stringResource(R.string.scan_ready))
                }
            } else {
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.scan_ready), style = MaterialTheme.typography.headlineMedium)
                state.error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
```

- [ ] **Step 7: Update `AppNavigation.kt` — wire Scan screen**

Replace `composable(Routes.SCAN)` placeholder with:
```kotlin
composable(Routes.SCAN) {
    ScanScreen(
        onBack = { navController.popBackStack() },
        onCheckOut = { navController.navigate(Routes.CHECKOUT) },
        onCheckIn = { navController.navigate(Routes.CHECKIN) },
        onEquipmentDetail = { barcode -> navController.navigate(Routes.equipmentDetail(barcode)) },
    )
}
```

- [ ] **Step 8: Run tests**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew testDebugUnitTest --tests "com.rentflow.scanner.ui.scan.ScanViewModelTest"
```
Expected: PASS

- [ ] **Step 9: Build and verify**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add -A && git commit -m "feat: scan screen with hardware scanner integration and equipment lookup"
```

---

## Task 10: Check-Out Screen

**Files:**
- Create: `app/src/main/java/com/rentflow/scanner/ui/checkout/CheckOutViewModel.kt`
- Create: `app/src/main/java/com/rentflow/scanner/ui/checkout/CheckOutScreen.kt`
- Test: `app/src/test/java/com/rentflow/scanner/ui/checkout/CheckOutViewModelTest.kt`
- Modify: `app/src/main/java/com/rentflow/scanner/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Write CheckOutViewModel test**

```kotlin
package com.rentflow.scanner.ui.checkout

import com.rentflow.scanner.data.hardware.BarcodeScanEvent
import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.repository.ProjectRepository
import com.rentflow.scanner.data.repository.ScannerRepository
import com.rentflow.scanner.domain.model.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckOutViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var scannerRepo: ScannerRepository
    private lateinit var projectRepo: ProjectRepository
    private lateinit var hardwareScanner: HardwareScanner
    private val barcodeFlow = MutableSharedFlow<BarcodeScanEvent>()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        scannerRepo = mockk(relaxed = true)
        projectRepo = mockk()
        hardwareScanner = mockk(relaxed = true)
        every { hardwareScanner.barcodeScanEvents } returns barcodeFlow
        every { hardwareScanner.rfidReadEvents } returns MutableSharedFlow()
        coEvery { projectRepo.listProjects(any()) } returns Result.success(listOf(
            Project("p1", "Festival 2026", "active", "#00d4ff"),
        ))
    }

    @After
    fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `scanned items are added to list`() = runTest {
        val session = ScanSession("s1", ScanType.OUT, emptyList(), System.currentTimeMillis())
        coEvery { scannerRepo.createSession(any(), any()) } returns Result.success(session)
        val equipment = Equipment("1", "EQ-001", "Speaker", "Audio", EquipmentStatus.AVAILABLE, null, null, null, null)
        coEvery { scannerRepo.resolveBarcode("EQ-001") } returns Result.success(equipment)
        val scanResult = ScanResult("sr1", "EQ-001", equipment, System.currentTimeMillis(), ScanType.OUT)
        coEvery { scannerRepo.sessionScan(any(), any(), any()) } returns Result.success(scanResult)

        val viewModel = CheckOutViewModel(scannerRepo, projectRepo, hardwareScanner)
        advanceUntilIdle()

        viewModel.selectProject(Project("p1", "Festival 2026", "active", "#00d4ff"))
        advanceUntilIdle()

        barcodeFlow.emit(BarcodeScanEvent("EQ-001", "QR_CODE"))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.scannedItems.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew testDebugUnitTest --tests "com.rentflow.scanner.ui.checkout.CheckOutViewModelTest"
```
Expected: FAIL

- [ ] **Step 3: Create `CheckOutViewModel.kt`**

```kotlin
package com.rentflow.scanner.ui.checkout

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.repository.ProjectRepository
import com.rentflow.scanner.data.repository.ScannerRepository
import com.rentflow.scanner.domain.model.Equipment
import com.rentflow.scanner.domain.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CheckOutUiState(
    val projects: List<Project> = emptyList(),
    val selectedProject: Project? = null,
    val scannedItems: List<Equipment> = emptyList(),
    val sessionId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val completed: Boolean = false,
)

@HiltViewModel
class CheckOutViewModel @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val projectRepository: ProjectRepository,
    private val hardwareScanner: HardwareScanner,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CheckOutUiState())
    val uiState: StateFlow<CheckOutUiState> = _uiState

    init {
        loadProjects()
        viewModelScope.launch {
            hardwareScanner.barcodeScanEvents.collect { event ->
                onBarcodeScanned(event.barcode)
            }
        }
    }

    private fun loadProjects() {
        viewModelScope.launch {
            projectRepository.listProjects().fold(
                onSuccess = { _uiState.update { s -> s.copy(projects = it) } },
                onFailure = { _uiState.update { s -> s.copy(error = it.message) } },
            )
        }
    }

    fun selectProject(project: Project) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedProject = project, isLoading = true) }
            scannerRepository.createSession("out", project.id).fold(
                onSuccess = { session ->
                    _uiState.update { it.copy(sessionId = session.id, isLoading = false) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                },
            )
        }
    }

    private fun onBarcodeScanned(barcode: String) {
        val state = _uiState.value
        if (state.sessionId == null) return
        if (state.scannedItems.any { it.barcode == barcode }) return // duplicate

        viewModelScope.launch {
            scannerRepository.resolveBarcode(barcode).fold(
                onSuccess = { equipment ->
                    scannerRepository.sessionScan(state.sessionId, barcode, "out")
                    _uiState.update { it.copy(scannedItems = it.scannedItems + equipment) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = e.message) }
                },
            )
        }
    }

    fun removeItem(equipment: Equipment) {
        _uiState.update { it.copy(scannedItems = it.scannedItems - equipment) }
    }

    fun completeCheckOut() {
        val state = _uiState.value
        if (state.sessionId == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            scannerRepository.endSession(state.sessionId).fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, completed = true) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }
}
```

- [ ] **Step 4: Create `CheckOutScreen.kt`**

```kotlin
package com.rentflow.scanner.ui.checkout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentflow.scanner.R
import com.rentflow.scanner.ui.components.EquipmentCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckOutScreen(
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    viewModel: CheckOutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.completed) {
        if (state.completed) onCompleted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.checkout_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            if (state.selectedProject == null) {
                Text(stringResource(R.string.checkout_select_project), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.projects) { project ->
                        Card(onClick = { viewModel.selectProject(project) }, modifier = Modifier.fillMaxWidth()) {
                            Text(project.name, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            } else {
                Text("${stringResource(R.string.checkout_title)}: ${state.selectedProject!!.name}", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.checkout_scan_items), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.scannedItems) { item ->
                        EquipmentCard(item) {
                            IconButton(onClick = { viewModel.removeItem(item) }) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                if (state.scannedItems.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = viewModel::completeCheckOut,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text("${stringResource(R.string.checkout_confirm)} (${state.scannedItems.size})")
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 5: Update `AppNavigation.kt` — wire Check-Out**

Replace `composable(Routes.CHECKOUT)` placeholder with:
```kotlin
composable(Routes.CHECKOUT) {
    CheckOutScreen(
        onBack = { navController.popBackStack() },
        onCompleted = {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.HOME) { inclusive = true }
            }
        },
    )
}
```

- [ ] **Step 6: Run tests**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew testDebugUnitTest --tests "com.rentflow.scanner.ui.checkout.CheckOutViewModelTest"
```
Expected: PASS

- [ ] **Step 7: Build and verify**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat: check-out screen with project selection and barcode scanning"
```

---

## Task 11: Check-In Screen

**Files:**
- Create: `app/src/main/java/com/rentflow/scanner/ui/checkin/CheckInViewModel.kt`
- Create: `app/src/main/java/com/rentflow/scanner/ui/checkin/CheckInScreen.kt`
- Modify: `app/src/main/java/com/rentflow/scanner/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Create `CheckInViewModel.kt`**

```kotlin
package com.rentflow.scanner.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.repository.ScannerRepository
import com.rentflow.scanner.domain.model.Equipment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CheckInItem(
    val equipment: Equipment,
    val condition: Int = 5, // 1-5 stars
    val notes: String = "",
    val photoPath: String? = null,
)

data class CheckInUiState(
    val items: List<CheckInItem> = emptyList(),
    val sessionId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val completed: Boolean = false,
)

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val hardwareScanner: HardwareScanner,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState

    init {
        startSession()
        viewModelScope.launch {
            hardwareScanner.barcodeScanEvents.collect { event ->
                onBarcodeScanned(event.barcode)
            }
        }
    }

    private fun startSession() {
        viewModelScope.launch {
            scannerRepository.createSession("in").fold(
                onSuccess = { _uiState.update { s -> s.copy(sessionId = it.id) } },
                onFailure = { _uiState.update { s -> s.copy(error = it.message) } },
            )
        }
    }

    private fun onBarcodeScanned(barcode: String) {
        val state = _uiState.value
        if (state.items.any { it.equipment.barcode == barcode }) return

        viewModelScope.launch {
            scannerRepository.resolveBarcode(barcode).fold(
                onSuccess = { equipment ->
                    _uiState.update { it.copy(items = it.items + CheckInItem(equipment)) }
                    state.sessionId?.let { scannerRepository.sessionScan(it, barcode, "in") }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = e.message) }
                },
            )
        }
    }

    fun updateCondition(barcode: String, condition: Int) {
        _uiState.update { state ->
            state.copy(items = state.items.map {
                if (it.equipment.barcode == barcode) it.copy(condition = condition) else it
            })
        }
    }

    fun updateNotes(barcode: String, notes: String) {
        _uiState.update { state ->
            state.copy(items = state.items.map {
                if (it.equipment.barcode == barcode) it.copy(notes = notes) else it
            })
        }
    }

    fun completeCheckIn() {
        val state = _uiState.value
        if (state.sessionId == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            scannerRepository.endSession(state.sessionId).fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, completed = true) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }
}
```

- [ ] **Step 2: Create `CheckInScreen.kt`**

```kotlin
package com.rentflow.scanner.ui.checkin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentflow.scanner.R
import com.rentflow.scanner.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    viewModel: CheckInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.completed) {
        if (state.completed) onCompleted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.checkin_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.checkout_scan_items), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(item.equipment.name, style = MaterialTheme.typography.titleLarge)
                            Text("Barcode: ${item.equipment.barcode}", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))

                            Text(stringResource(R.string.checkin_condition), style = MaterialTheme.typography.bodyMedium)
                            Row {
                                (1..5).forEach { star ->
                                    IconButton(onClick = { viewModel.updateCondition(item.equipment.barcode, star) }) {
                                        Icon(
                                            if (star <= item.condition) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = null,
                                            tint = Warning,
                                            modifier = Modifier.size(32.dp),
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = item.notes,
                                onValueChange = { viewModel.updateNotes(item.equipment.barcode, it) },
                                label = { Text(stringResource(R.string.checkin_notes)) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                            )
                        }
                    }
                }
            }

            if (state.items.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = viewModel::completeCheckIn,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text("${stringResource(R.string.checkin_confirm)} (${state.items.size})")
                }
            }
        }
    }
}
```

- [ ] **Step 3: Update `AppNavigation.kt` — wire Check-In**

Replace `composable(Routes.CHECKIN)` placeholder with:
```kotlin
composable(Routes.CHECKIN) {
    CheckInScreen(
        onBack = { navController.popBackStack() },
        onCompleted = {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.HOME) { inclusive = true }
            }
        },
    )
}
```

- [ ] **Step 4: Build and verify**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: check-in screen with condition rating and notes"
```

---

## Task 12: Inventory Screen

**Files:**
- Create: `app/src/main/java/com/rentflow/scanner/ui/inventory/InventoryViewModel.kt`
- Create: `app/src/main/java/com/rentflow/scanner/ui/inventory/InventoryScreen.kt`
- Modify: `app/src/main/java/com/rentflow/scanner/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Create `InventoryViewModel.kt`**

```kotlin
package com.rentflow.scanner.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.repository.ScannerRepository
import com.rentflow.scanner.data.repository.WarehouseRepository
import com.rentflow.scanner.domain.model.Equipment
import com.rentflow.scanner.domain.model.WarehouseZone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryUiState(
    val zones: List<WarehouseZone> = emptyList(),
    val selectedZone: WarehouseZone? = null,
    val sessionId: String? = null,
    val scannedItems: List<Equipment> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val completed: Boolean = false,
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val warehouseRepository: WarehouseRepository,
    private val hardwareScanner: HardwareScanner,
) : ViewModel() {
    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState

    init {
        loadZones()
        viewModelScope.launch {
            hardwareScanner.barcodeScanEvents.collect { event ->
                onBarcodeScanned(event.barcode)
            }
        }
    }

    private fun loadZones() {
        viewModelScope.launch {
            warehouseRepository.listZones().fold(
                onSuccess = { _uiState.update { s -> s.copy(zones = it) } },
                onFailure = { _uiState.update { s -> s.copy(error = it.message) } },
            )
        }
    }

    fun selectZone(zone: WarehouseZone) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedZone = zone, isLoading = true) }
            scannerRepository.createSession("inventory", zone.id).fold(
                onSuccess = { session ->
                    _uiState.update { it.copy(sessionId = session.id, isLoading = false) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                },
            )
        }
    }

    private fun onBarcodeScanned(barcode: String) {
        val state = _uiState.value
        if (state.sessionId == null) return
        if (state.scannedItems.any { it.barcode == barcode }) return

        viewModelScope.launch {
            scannerRepository.resolveBarcode(barcode).fold(
                onSuccess = { equipment ->
                    scannerRepository.sessionScan(state.sessionId, barcode, "inventory")
                    _uiState.update { it.copy(scannedItems = it.scannedItems + equipment) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = e.message) }
                },
            )
        }
    }

    fun completeInventory() {
        val state = _uiState.value
        if (state.sessionId == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            scannerRepository.endSession(state.sessionId).fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, completed = true) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }
}
```

- [ ] **Step 2: Create `InventoryScreen.kt`**

```kotlin
package com.rentflow.scanner.ui.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentflow.scanner.R
import com.rentflow.scanner.ui.components.EquipmentCard
import com.rentflow.scanner.ui.theme.Success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    viewModel: InventoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.completed) {
        if (state.completed) onCompleted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inventory_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            if (state.selectedZone == null) {
                Text(stringResource(R.string.inventory_select_zone), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.zones) { zone ->
                        Card(onClick = { viewModel.selectZone(zone) }, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(zone.name, style = MaterialTheme.typography.bodyLarge)
                                Text("${zone.expectedItemCount} Items", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            } else {
                Text("${state.selectedZone!!.name}", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("${stringResource(R.string.inventory_found)}: ${state.scannedItems.size}", color = Success)
                    Text("${stringResource(R.string.inventory_missing)}: ${(state.selectedZone!!.expectedItemCount - state.scannedItems.size).coerceAtLeast(0)}", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.scannedItems) { item ->
                        EquipmentCard(item)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = viewModel::completeInventory,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(stringResource(R.string.inventory_confirm))
                }
            }
        }
    }
}
```

- [ ] **Step 3: Update `AppNavigation.kt` — wire Inventory**

Replace `composable(Routes.INVENTORY)` placeholder with:
```kotlin
composable(Routes.INVENTORY) {
    InventoryScreen(
        onBack = { navController.popBackStack() },
        onCompleted = {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.HOME) { inclusive = true }
            }
        },
    )
}
```

- [ ] **Step 4: Build and verify**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: inventory screen with zone selection and item scanning"
```

---

## Task 13: Equipment Detail + Settings Screen

**Files:**
- Create: `app/src/main/java/com/rentflow/scanner/ui/equipment/EquipmentDetailViewModel.kt`
- Create: `app/src/main/java/com/rentflow/scanner/ui/equipment/EquipmentDetailScreen.kt`
- Create: `app/src/main/java/com/rentflow/scanner/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/rentflow/scanner/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/rentflow/scanner/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Create `EquipmentDetailViewModel.kt`**

```kotlin
package com.rentflow.scanner.ui.equipment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.hardware.RfidWriteResult
import com.rentflow.scanner.data.repository.ScannerRepository
import com.rentflow.scanner.domain.model.Equipment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EquipmentDetailUiState(
    val equipment: Equipment? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val rfidWriteResult: RfidWriteResult? = null,
    val isWritingRfid: Boolean = false,
    val rfidAvailable: Boolean = false,
)

@HiltViewModel
class EquipmentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scannerRepository: ScannerRepository,
    private val hardwareScanner: HardwareScanner,
) : ViewModel() {
    private val barcode: String = savedStateHandle["barcode"] ?: ""
    private val _uiState = MutableStateFlow(EquipmentDetailUiState(rfidAvailable = hardwareScanner.isRfidAvailable()))
    val uiState: StateFlow<EquipmentDetailUiState> = _uiState

    init {
        loadEquipment()
    }

    private fun loadEquipment() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            scannerRepository.resolveBarcode(barcode).fold(
                onSuccess = { eq -> _uiState.update { it.copy(equipment = eq, isLoading = false) } },
                onFailure = { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } },
            )
        }
    }

    fun writeRfidTag() {
        val eq = _uiState.value.equipment ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isWritingRfid = true, rfidWriteResult = null) }
            val result = hardwareScanner.writeRfidTag(eq.barcode)
            _uiState.update { it.copy(isWritingRfid = false, rfidWriteResult = result) }
        }
    }
}
```

- [ ] **Step 2: Create `EquipmentDetailScreen.kt`**

```kotlin
package com.rentflow.scanner.ui.equipment

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentflow.scanner.R
import com.rentflow.scanner.ui.components.EquipmentCard
import com.rentflow.scanner.ui.components.LoadingScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentDetailScreen(
    onBack: () -> Unit,
    viewModel: EquipmentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.equipment_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(padding))
            state.error != null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                }
            }
            state.equipment != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                ) {
                    EquipmentCard(state.equipment!!)
                    Spacer(Modifier.height(16.dp))

                    state.equipment!!.rfidTag?.let {
                        Text("RFID: $it", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))
                    }

                    if (state.rfidAvailable) {
                        Button(
                            onClick = viewModel::writeRfidTag,
                            enabled = !state.isWritingRfid,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.equipment_write_rfid))
                        }

                        state.rfidWriteResult?.let { result ->
                            Spacer(Modifier.height(8.dp))
                            if (result.success) {
                                Text("RFID-Tag erfolgreich geschrieben", color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text(result.error ?: stringResource(R.string.rfid_write_failed), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Create `SettingsViewModel.kt`**

```kotlin
package com.rentflow.scanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.preferences.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val language: String = "de",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(settingsDataStore.serverUrl, settingsDataStore.language) { url, lang ->
                SettingsUiState(serverUrl = url, language = lang)
            }.collect { _uiState.value = it }
        }
    }

    fun saveServerUrl(url: String) {
        viewModelScope.launch { settingsDataStore.setServerUrl(url) }
    }

    fun saveLanguage(lang: String) {
        viewModelScope.launch { settingsDataStore.setLanguage(lang) }
    }
}
```

- [ ] **Step 4: Create `SettingsScreen.kt`**

```kotlin
package com.rentflow.scanner.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentflow.scanner.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var serverUrl by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Server URL
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text(stringResource(R.string.settings_server_url)) },
                placeholder = { Text("https://rentflow.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { viewModel.saveServerUrl(serverUrl) }) {
                Text(stringResource(R.string.confirm))
            }

            HorizontalDivider()

            // Language
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = state.language == "de",
                    onClick = { viewModel.saveLanguage("de") },
                    label = { Text("Deutsch") },
                )
                FilterChip(
                    selected = state.language == "en",
                    onClick = { viewModel.saveLanguage("en") },
                    label = { Text("English") },
                )
            }

            HorizontalDivider()

            // Hardware info
            Text(stringResource(R.string.settings_hardware), style = MaterialTheme.typography.titleLarge)
            Text("Gerät: ${android.os.Build.MODEL}", style = MaterialTheme.typography.bodyMedium)
            Text("Android: ${android.os.Build.VERSION.RELEASE}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
```

- [ ] **Step 5: Update `AppNavigation.kt` — wire remaining screens**

Replace Equipment Detail and Settings placeholders:
```kotlin
composable(Routes.EQUIPMENT_DETAIL) {
    EquipmentDetailScreen(onBack = { navController.popBackStack() })
}
composable(Routes.SETTINGS) {
    SettingsScreen(onBack = { navController.popBackStack() })
}
```

- [ ] **Step 6: Build full project**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Run all tests**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew testDebugUnitTest
```
Expected: All tests PASS

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat: equipment detail with RFID write, settings screen"
```

---

## Task 14: Final Integration + Audio/Vibration Feedback

**Files:**
- Modify: `app/src/main/java/com/rentflow/scanner/ui/scan/ScanViewModel.kt` (add feedback)
- Modify: `app/src/main/java/com/rentflow/scanner/ui/navigation/AppNavigation.kt` (final wiring)

- [ ] **Step 1: Add audio/vibration feedback to ScanViewModel**

Add to `ScanViewModel.kt`:
```kotlin
// Add to class, inject via constructor:
@ApplicationContext private val context: Context

// Add method:
private fun playSuccessFeedback() {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 150)
        toneGen.release()
    } catch (_: Exception) {}
}

// Call playSuccessFeedback() after successful barcode resolution in onBarcodeScanned()
```

- [ ] **Step 2: Verify complete `AppNavigation.kt` has all screens wired**

Ensure all composable routes reference their real screens (no placeholders remaining).

- [ ] **Step 3: Full build + all tests**

```bash
cd C:/Users/jecke/AndroidStudioProjects/Scanner && ./gradlew testDebugUnitTest && ./gradlew assembleDebug
```
Expected: All tests PASS, BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: audio/vibration feedback, final navigation wiring"
```

- [ ] **Step 5: Tag release**

```bash
git tag v0.1.0-alpha
git log --oneline
```
