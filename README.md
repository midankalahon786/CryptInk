# CryptInk 🖋️🔐

**CryptInk** is a zero-knowledge, offline-first personal journal and diary app for Android built with Kotlin and Jetpack Compose.

Every entry is encrypted on-device before touching local storage. When opened normally, the app displays all journal entries in their raw, scrambled Base64 ciphertext form—revealing plaintext only in volatile memory after you enter your passphrase.

---

## ✨ Key Features

* **Scrambled by Default:** Opens directly to your journal feed without a blocking login screen, rendering all titles and bodies as unreadable ciphertext until unlocked.
* **Dual Unlock Modes (Shoulder-Surfing Protection):**
    * **Selective Unlock:** Tap a specific entry and enter your passphrase to decrypt *only* that entry while keeping the rest of your feed scrambled.
    * **Global Session Unlock:** Unlock all entries at once for seamless reading and writing during an active session.
* **True Zero-Knowledge Storage:** Plaintext is never written to Room, SharedPreferences, logs, or device backups (`android:allowBackup="false"`).
* **Built-in Password Verification Without Stored Hashes:** Uses authenticated encryption (AES-GCM), so an incorrect passphrase automatically fails integrity verification without needing a password hash stored on disk.
* **Aggressive Session Hardening:**
    * Derived encryption keys live strictly in volatile RAM (`StateFlow`) and are wiped immediately when locking or backgrounding the app (`ProcessLifecycleOwner`).
    * Screen capture and Recent Apps thumbnail previews are blocked via `WindowManager.LayoutParams.FLAG_SECURE`.

---

## 🛡️ Cryptographic Design

CryptInk relies solely on standard Java Cryptography Architecture (`javax.crypto`) APIs built into the Android SDK (API 26+):

1. **Key Derivation (`PBKDF2WithHmacSHA256`):**
    * Derives a 256-bit AES key from your passphrase and a unique, cryptographically secure 16-byte random **Salt** (`310,000+` iterations).
2. **Authenticated Encryption (`AES/GCM/NoPadding`):**
    * Encrypts every entry title and body using a fresh 12-byte random **Initialization Vector (IV)** on every save or edit.
    * Produces a 128-bit authentication tag; tampering with ciphertext or supplying the wrong passphrase throws an `AEADBadTagException`.
3. **Storage Format:**
    * Ciphertext, IV, and Salt are encoded as Base64 strings and persisted in Room alongside unencrypted entry timestamps.

---

## 🏗️ Tech Stack & Architecture

* **Language:** Kotlin 2.1+
* **UI:** Jetpack Compose + Material 3 (Monospace typography for ciphertext states)
* **Architecture:** MVVM + Clean Architecture with unidirectional data flow (`StateFlow` & Kotlin `Flow`)
* **Database:** Room (KSP)
* **Dependency Injection:** Hilt
* **Concurrency & Lifecycle:** Kotlin Coroutines, `androidx.lifecycle.process`

### Project Structure

```text
com.cryptink.app/
├── core/
│   ├── crypto/
│   │   ├── CryptoManager.kt         # PBKDF2WithHmacSHA256 + AES-256-GCM engine
│   │   ├── EncryptedPayload.kt      # Data holder for Base64 cipherText, iv, and salt
│   │   └── KeySessionManager.kt     # In-memory StateFlow holding SecretKey & unlock scope
│   └── security/
│       └── AppLockObserver.kt       # Wipes in-memory key when app moves to background
├── data/
│   ├── local/
│   │   ├── CryptInkDatabase.kt      # Room database configuration
│   │   ├── JournalDao.kt            # Reactive queries returning Flow<List<JournalEntity>>
│   │   └── JournalEntity.kt         # Encrypted DB schema (timestamps, salt, iv, cipher fields)
│   └── repository/
│       └── JournalRepository.kt     # Coordinates on-the-fly encryption and decryption
├── domain/
│   └── model/
│       └── JournalEntry.kt          # UI/Domain model representing locked vs. unlocked state
├── di/
│   └── AppModule.kt                 # Hilt dependency bindings
└── ui/
    ├── components/                  # JournalCard (ciphertext/plaintext) & UnlockBottomSheet
    ├── feed/                        # FeedScreen & FeedViewModel
    ├── editor/                      # EditorScreen & EditorViewModel
    └── theme/                       # Material 3 colors, theme, and typography