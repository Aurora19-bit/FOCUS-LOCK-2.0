# FocusLock — personal app blocker (Android)

A self-built Android app for locking yourself out of everything except an
allow-list of apps for a set duration.

## How it works
- **Accessibility Service** watches which app comes to the foreground. If a
  focus session is running and that app isn't on your allowed list, it
  immediately sends you home and shows a full-screen "Locked" interstitial.
- **Device Admin** is used only so the app can't be uninstalled without first
  deactivating admin rights in Settings — and Settings is itself blocked
  while a session is active.
- **Foreground notification** shows the countdown and can't be swiped away.
- Session end-time is stored as an absolute timestamp, so a reboot mid-session
  does not clear it — the accessibility service resumes enforcing as soon as
  Android restarts services after boot.

## Build & install
1. Install **Android Studio** (this needs a real Android toolchain — it can't
   run as a web page).
2. Open the `FocusLock/` folder as a project.
3. Let Gradle sync (it will download the Android Gradle Plugin/Kotlin plugin
   the first time — needs internet).
4. Connect your phone via USB with Developer Options + USB debugging on, or
   build an APK (`Build > Build Bundle(s)/APK(s) > Build APK(s)`) and copy it
   to your phone via any method, then install it (you'll need to allow
   "install unknown apps" for whichever app you use to open the APK).
5. Open FocusLock on your phone, tap **Grant required permissions** three
   times in a row (Accessibility → Overlay → Device Admin), following the
   system screens each time it jumps you there.
6. Check the apps you want to remain usable during a session.
7. Enter a duration in minutes and tap **Start focus session**.

## Scheduled breaks
Before starting a session, you can add break windows: "starts at +30 min,
lasts 10 min", added one at a time to a list shown under the input fields.
Once you tap **Start focus session**, that list is baked into the session —
there is no button anywhere to add, extend, or trigger a break once a
session is running. During a scheduled window, all apps are unlocked
automatically and the notification says so; outside those windows,
enforcement resumes on its own with no action needed. This is deliberate:
the decision about when breaks happen is made once, in advance, by the
version of you that isn't mid-scroll.

## If enforcement stops working
- The status line on the main screen now says **"Enforcement service NOT running"**
  when Android has stopped the accessibility service, and Start refuses to run
  until it's back. Fix: Settings > Accessibility > FocusLock, toggle off then on.
- Phones from Xiaomi, Oppo, Vivo, Realme, OnePlus and Samsung often kill background
  services to save battery. Set FocusLock's battery usage to **Unrestricted** (Settings >
  Apps > FocusLock > Battery), turn off any "pause app activity if unused" option, and
  lock the app in Recents if your phone offers that.
- Enforcement has two layers: instant window-change events plus a once-a-second check
  of the app on screen, so a dropped event no longer lets an app through.

## Uninstalling on purpose
There's an **Uninstall FocusLock** button on the main screen (disabled while
a session is active). Tapping it opens a math gate: you need **5 correct
answers in a row**, getting harder as you go (more terms, bigger numbers). A
wrong answer resets your streak to zero and gives you a new problem — so it
can't be brute-forced by mashing digits, only actually solved. Passing it
deactivates Device Admin and hands off to Android's own uninstall
confirmation (you still tap "Uninstall" on the system dialog — that last
step is Android's, not something an app can skip).

## What this cannot fully guarantee, and why
Because it's your own phone and you have owner-level control over it, no
non-rooted app can make a lock 100% unbreakable. The two remaining escape
hatches are:
- **Safe Mode** (long-press power → long-press "Power off" until it offers
  Safe Mode, or a hardware-button combo depending on your phone) — this
  disables all third-party apps including FocusLock's accessibility service.
- **Factory reset** — wipes the phone entirely.

Both are intentionally slow, disruptive, and hard to do "in the moment"
without thinking about it — which is the actual point of a commitment
device. Anything that claimed to be bypass-proof against Safe Mode/factory
reset on an unrooted, user-owned device would not be telling you the truth.

## Suggested hardening you can add yourself later
- Require typing a long random string (that you write down and hand to
  someone else, or generate and immediately forget) before "Start session"
  will let you shorten or cancel a session early — remove the "end early"
  capability from the UI entirely if you don't trust yourself with it.
- Add a startup check that nags (via notification) if Accessibility/Device
  Admin get revoked outside of a session, so you notice tampering.
