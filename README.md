## ⚡ React Native WireGuard (Android) — Codegen + TurboModule

Let me introduce WireGuard VPN library for Android using React Native Codegen (TurboModule).

RN 0.80+ required.

Methods: ```connect, disconnect, getStatus, ping```

Auto: foreground system notification while connected (Play Store-compliant). Disappears on disconnect.

Note: Android requires a native VPN consent dialog. RN can’t trigger it by itself — you must add a small change in MainActivity (bare RN) or use the provided Expo plugin.

If you need iOS/macOS/Windows: not supported here. Android only.

### ⚙️ Requirements

React Native 0.80+

Android minSdk 24+, target/compile 35 recommended

Kotlin 1.9+

Java 17+ (works with 22 as well)

WireGuard config string (standard wg quick format)

### 📦 Install

npm

```bash
npm i react-native-wireguard-turbo
```

yarn

```bash
yarn add react-native-wireguard-turbo
```

### 📲 Android setup (bare RN)

Trigger VPN consent from MainActivity (once; before first connect)

```MainActivity.kt```

```kotlin
package your.app.package

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.facebook.react.ReactActivity

class MainActivity : ReactActivity() {

  private lateinit var vpnConsentLauncher: ActivityResultLauncher<Intent>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    vpnConsentLauncher = registerForActivityResult(
      ActivityResultContracts.StartActivityForResult()
    ) { /* your logic goes here */ }

    ensureVpnConsent()
  }

  private fun ensureVpnConsent() {
    val intent = VpnService.prepare(this)
    if (intent != null) {
      vpnConsentLauncher.launch(intent) // shows the system dialog
    }
    // else: already granted previously
  }

  override fun getMainComponentName(): String = "App"
}
```

### 🔑 Permissions (Android Manifest)

These are typically merged by the library. If your app is strict, ensure:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

The library runs a foreground VPN service when connected and removes the notification when disconnected. No extra service wiring should be needed if manifest merging is on.

### 🧩 Expo setup (prebuild / custom dev client)

Add the plugin so VPN consent and any native bits are injected automatically.

```app.json or app.config.js```

```json
{
  "expo": {
    "plugins": [
      ["<your-package-name>/expo-plugin", {}]
    ]
  }
}
```

Write you plugin. Check Expo plugin example below:

<details> <summary><strong>See Expo plugin</strong></summary>

```javascript
// plugins/ensure-vpn-consent.js
const { withMainActivity } = require("@expo/config-plugins");

module.exports = (config) =>
  withMainActivity(config, (cfg) => {
    let src = cfg.modResults.contents;
    if (cfg.modResults.language !== "kt") throw new Error("Expected Kotlin MainActivity.kt");

    // 0) Do NOT touch the anchor; bail if it’s missing.
    if (!src.includes("super.onCreate(null)")) return cfg;

    // Ensure imports (idempotent)
    const addImport = (imp) => {
      if (!src.includes(`import ${imp}`)) {
        src = src.replace(/(package .*?\n)/, `$1import ${imp}\n`);
      }
    };
    addImport("android.content.Intent");
    addImport("android.net.VpnService");
    addImport("androidx.activity.result.ActivityResultLauncher");
    addImport("androidx.activity.result.contract.ActivityResultContracts");

    // Add field inside class (once)
    if (!src.includes("vpnConsentLauncher: ActivityResultLauncher<Intent>")) {
      src = src.replace(
        /(class\s+MainActivity\s*:\s*ReactActivity\(\)\s*\{\s*)/,
        `$1\n  private lateinit var vpnConsentLauncher: ActivityResultLauncher<Intent>\n`
      );
    }

    // Inject launcher + consent call immediately AFTER the anchor line (once)
    if (!src.includes("// VPN_CONSENT_START")) {
      src = src.replace(
        /super\.onCreate\(null\)\s*\n/,
        `super.onCreate(null)\n` +
          `    // VPN_CONSENT_START\n` +
          `    vpnConsentLauncher = registerForActivityResult(\n` +
          `      ActivityResultContracts.StartActivityForResult()\n` +
          `    ) { /* handle result if needed */ }\n` +
          `    VpnService.prepare(this)?.let { vpnConsentLauncher.launch(it) }\n` +
          `    // VPN_CONSENT_END\n`
      );
    }

    cfg.modResults.contents = src;
    return cfg;
  });
```

</details>

Then:

```bash
npx expo prebuild
npx expo run:android
```

This requires a custom dev client. It will not work on Expo Go.

### Usage

```javascript
import { WG } from 'react-native-wireguard-turbo'

const cfg = `
[Interface]
PrivateKey = YourVeeRYSeCRETkeY=
Address = 10.0.0.2/32
DNS = 1.1.1.1

[Peer]
PublicKey = YourVeeRYSeCRETkeY=
AllowedIPs = 0.0.0.0/0, ::/0
Endpoint = your.server.com:51820
PersistentKeepalive = 25
`.trim()

async function startVpn() {
  // Make sure user granted VPN consent first (see MainActivity / plugin)
  await WG.connect(cfg) // throws on invalid config or failure
}

async function stopVpn() {
  await WG.disconnect()
}

async function status() {
  const s = await WG.getStatus()
  // 'DOWN' | 'UP' | 'ERROR' | 'UNKNOWN'
  console.log(s)
}

async function heartbeat() {
  const ms = await WG.ping() // round-trip latency estimate (ms) or -1 if unknown
  console.log('ping', ms)
}
```

🧩 API

```javascript
connect(config: string): Promise<void>
```

Starts the WireGuard tunnel from a full wg config string.
Foreground notification appears while connected.

```javascript
disconnect(): Promise<void>
```

Stops the tunnel.
Notification is removed.

```javascript
getStatus(): Promise<'DOWN' | 'UP' | 'ERROR' | 'UNKNOWN'>
```

Just ping to test status.

```javascript
ping(): Promise<string> // "pong"
```

The tunnel rebinds automatically if the traffic source changed (for example from Wi-Fi to mobile network).

Foreground notification (Play Store rule)

Shown automatically while VPN is connected (system channel e.g., "wg").

Removed on disconnect() and on service stop.

Network change handling

Handled automatically with rebind(). Call it if you detect manual network switches.

### 🐞 Troubleshooting

Consent dialog never appears: You didn’t call VpnService.prepare(...) from MainActivity (or didn’t add the Expo plugin).

Connected but no traffic: Check AllowedIPs includes 0.0.0.0/0, ::/0 (or the routes you actually want). Also verify server allows your peer, and MTU is sane.

Notification missing on Android 13+: Request POST_NOTIFICATIONS permission or you won’t see it.

### 🛜 Example

```javascript
import { View, TouchableOpacity, Text } from 'react-native';
import WG from 'react-native-wireguard-turbo';

const cfg = `[Interface]
Address = xxx.xxx.xx.xx/xx
PrivateKey = YourVeeRYSeCRETkeY=
DNS = 8.8.8.8

[Peer]
PublicKey = YourVeeRYSeCRETkeY=
AllowedIPs = 0.0.0.0/0
Endpoint = xx.xxx.xxx.xx:51820
PersistentKeepalive = 25`;

export default function HomeScreen() {
  return (
    <View style={{ flex: 1 }}>
      <TouchableOpacity
        style={{
          marginTop: 50,
        }}
        onPress={async () => {
          console.log('Button pressed');
          try {
            await WG.connect(cfg);
            console.log('Manual connect SUCCESS');
          } catch (e) {
            console.log('Manual connect FAILED', e);
          }
        }}
      >
        <Text>Connect</Text>
      </TouchableOpacity>
   </View>
  );
}
```

### 📖 More details

I’ve written a full article describing the process of creating this library:  
👉 [Read on Medium](https://medium.com/@igor.khlyupin/react-native-wireguard-turbo-module-5f2817a24eff)

### 📜 License

GPL-2.0.
Include your LICENSE file in the repo and npm package.

If you found any bug or just have something to tell about this library — feel free to contact me:

```javascript
igor.khlyupin@gmail.com
```