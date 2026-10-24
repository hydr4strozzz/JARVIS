# JARVIS — Android Voice Assistant

## Setup in Codespace

1. Push this repo to GitHub.
2. Repo → Settings → Secrets and variables → Actions:
   - `GEMINI_API_KEY` — from https://aistudio.google.com/apikey
   - `GROQ_API_KEY`   — from https://console.groq.com/keys
3. Actions tab → "Build JARVIS APK" → Run workflow.
4. Download artifact `jarvis-debug-apk` from the completed run.
5. Install the APK on the phone (enable "Install unknown apps").

## After install

- Open app → grant Microphone, Notifications, Phone, SMS, Contacts.
- Tap **Enable Accessibility** → JARVIS service → Allow.
- Tap **Battery optimization off** → allow.
- Tap **Start JARVIS**.

Say **"Jarvis"** once to wake. Then speak your command in any language.
