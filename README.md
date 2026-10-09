# Study Launcher

An Android home-screen launcher that keeps students on task, plus an admin panel to control it.

- `web/` – Admin panel + API (Next.js on **Vercel**, database on **Supabase**)
- `android/` – The launcher app (Kotlin, Jetpack Compose)

## What it does

| Area | Behaviour |
|---|---|
| Study / break cycle | 3 h study → 1 h break, repeating. Lengths are per student. Runs on the **server clock**, so changing the phone's date/time does nothing. |
| Study time | Only admin-chosen apps open (dialer, contacts, calculator, camera, payment apps…). The emergency dialer, system UI and keyboards are always allowed. Notifications from other apps are silenced. |
| Break time | All apps available. |
| Focus tools | Pomodoro (adjustable, long break every 4), to-do list with priorities, daily stats and streak. |
| Admin panel | Add students, pick allowed apps from the phone's real app list, set schedule, force study/break, change the secret password, view the tamper log, remotely release a phone. |
| Removal | Needs the **secret password** (checked on the server, 5 wrong tries = 15 min lockout), or a 6-digit one-time code the admin reads out when the phone is offline. |

## Admin panel

- **Dashboard** – students with online/offline, current study/break mode, screen time, focus minutes, alert counts; search and filters (Online, Offline, Pending, Alerts).
- **Student page** – screen-time and focus charts (14 days), most-used apps, device info and setup progress, allowed-apps picker (with copy-from-another-student), schedule and force study/break, announcement shown on the phone, private notes, activity log, usage CSV export.
- **New students** – a student can set the launcher up themselves ("New student" on the phone, using `ENROLL_CODE`). They appear as *pending* on the dashboard and in **Alerts & approvals** until you confirm the guardian e-mail and approve.
- **Passwords** – set/reset the removal password, or generate a random one and e-mail it to the guardian; clear lockouts; offline one-time code.
- **Forgot password (on the phone)** – 🔒 → *Forgot password?* e-mails an 8-digit code (15 min, 5 tries, 3 requests/hour) through your SMTP server to the **guardian e-mail the admin approved** – never to an address the student controls. The guardian shares the code if they agree and the student sets a new password.
- **Alerts** – protection lost, device admin removed, clock changes, lockouts, blocked Settings screens, new enrolments.

SMTP env vars (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `SMTP_FROM`) and `ENROLL_CODE` are in `web/.env.example`. Gmail needs an App Password. Use **Settings → Send test e-mail** to check.

Screen time is the total time apps were in the foreground per day (from Android usage access; the launcher itself is excluded). It is uploaded every 5 minutes while the phone is online.

## Anti-cheat layers (no Device Owner)

1. **Device Administrator** – Settings greys out *Uninstall* while it is active.
2. **Accessibility service** – on any Settings / installer / Play Store / permission screen it looks at the window class and on-screen text (English + Hindi keywords) and bounces to home when it sees uninstall, device-admin, accessibility, default-home, force-stop, clear-data, developer-options, factory-reset, or this app's name.
3. **Usage-stats poller** – an independent foreground-app check in case the accessibility service is off.
4. **Integrity watchdog** – if any permission is switched off, the launcher forces itself back to a locked setup screen, logs `protection_lost`, and keeps Settings blocked except for 90 s windows opened by its own setup buttons.
5. **Foreground service + 60 s alarm watchdog + boot receiver** – restarts itself if killed or after reboot.
6. **Trusted clock** – server time advanced by the monotonic clock. After a reboot with no network the clock is untrusted and the launcher **fails closed into study mode**. Clock changes are reported.
7. **Wipe-proof** – if the app's data is cleared while admin/accessibility are still on, the phone stays locked down (study mode, no allowed apps) until the admin re-pairs it.
8. **Server audit log** – wrong passwords, lockouts, clock changes, blocked screens, permission loss, boot.

## Honest limits

Without Device Owner mode, Android cannot make an app truly unremovable. Known gaps:

- **Safe mode**, **ADB / USB debugging** and **factory reset from recovery** bypass everything. (Reset also wipes the phone; the admin panel will show it stop reporting.)
- Settings detection is by text/class name. A different system language, or an OEM settings app with unusual wording, can slip past. Test on the student's actual phone and add keywords in `Protection.kt` (`Sensitive`).
- A second user profile / guest account is not covered.
- Someone who guesses the offline 6-digit code gets in, but is limited to 5 tries then a 15-minute local lockout.
- Strongest practical setup: also set a **Google/Samsung account PIN** the student doesn't know, and disable USB debugging + OEM unlocking yourself.

If you ever can factory-reset the phone once, Device Owner provisioning would close most of these gaps.

## Deploy the backend

1. **Supabase**: create a project → SQL Editor → run `web/supabase/schema.sql`. Copy the project URL and the **service_role** key (Project settings → API).
2. **Vercel**: import the repo, set *Root Directory* to `web`, and add env vars from `web/.env.example`:
   `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `SESSION_SECRET` (32+ random chars).
   (Render also works: build `npm run build`, start `npm start`, same env vars.)
3. Open the deployed URL, sign in, **Add a student** (this sets the secret removal password), and note the 8-character pairing code.

Local run: `cd web && cp .env.example .env.local && npm install && npm run dev`.

## Build and install the app

1. Open `android/` in **Android Studio** (it creates the Gradle wrapper on first sync).
2. Set `serverUrl` in `android/gradle.properties` to your Vercel URL.
3. Build and install the APK (Build → Build APK, or run on the phone).
4. Open the app → enter the pairing code → grant each item on the setup screen:
   home app, device administrator, accessibility, usage access, display over apps, notification access, unrestricted battery.
   On Android 13+, if Accessibility or Notification access is greyed out: *App info → ⋮ → Allow restricted settings*.
5. Back in the admin panel the student shows as paired and their installed apps appear in the picker. Choose the allowed apps and **Save**.

## Removing the launcher (admin)

On the phone: tap 🔒 on the Home tab → enter the secret password (or the offline code from the student's page) → **Remove launcher protection** → uninstall normally.
Or in the admin panel: **Release phone**; the phone drops protection the next time it syncs (within a minute).

## Status

The backend type-checks and builds (`npm run build`). The Android project was written without an Android SDK available, so it has **not been compiled or run on a device yet** – expect to fix a few compile errors on first build, then test the Settings-blocking on the real phone.
