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

## ⚡ React Native WireGuard (IOS)

Installation on iOS is pretty much the same, but you need to do a few additional things:

You need your `teamId` and `appGroupId`. Fill them in in `app.json`.

Then, on developer.apple.com:

You need to create:

1. App Group
   Go to Certificates > Identifiers > App Groups
   Create: `your_name`

2. Main App ID (`your_name`)
   Capabilities to enable: App Groups + Network Extensions
   Link the app group you created above.

3. Extension App ID (`your_name.WireGuardNetworkExtension`)
   Capabilities to enable: App Groups + Network Extensions (Packet Tunnel Provider)
   Link the same app group.

The linking happens inside each App ID—when you enable the App Groups capability, it lets you select which groups to attach. Both the main app and the extension must have the same group attached so they can share UserDefaults.

After that, Xcode's automatic signing picks it all up when you build.

For the main app and the extension App ID, enable only these two:

App Groups
Network Extensions

What to toggle inside Network Extensions:

Enable Packet Tunnel—that's the only one you need.

After creating both App IDs, open each one and, under the App Groups capability, click Configure—it will show your app groups list. Select `your_name` and save.

Do this for both App IDs. That's the link.

If you want to change, let's say, the server address or description from “WireGuard,” open `WireGuardModule.swift`, find these lines:

```swift
proto.serverAddress = "WireGuard"
manager.localizedDescription = "WireGuard"
```

and change them!

You also need a plugin to create and manage targets. This is an example (or use AI—they are very good at creating plugins):


```javascript
const {
  withXcodeProject,
  withEntitlementsPlist,
  withDangerousMod,
} = require("@expo/config-plugins");
const path = require("path");
const fs = require("fs");

const EXTENSION_NAME = "WireGuardNetworkExtension";

function withWireGuard(config, options = {}) {
  const appGroupId =
    options.appGroupId || `group.${config.ios?.bundleIdentifier}`;

  config = withMainAppEntitlements(config, appGroupId);
  config = withExtensionFiles(config, appGroupId);
  config = withExtensionTarget(config, appGroupId);
  config = withMainAppSigning(config);

  return config;
}

function withMainAppEntitlements(config, appGroupId) {
  return withEntitlementsPlist(config, (c) => {
    c.modResults["com.apple.developer.networking.networkextension"] = [
      "packet-tunnel-provider",
    ];
    c.modResults["com.apple.security.application-groups"] = [appGroupId];
    return c;
  });
}

function withExtensionFiles(config, appGroupId) {
  return withDangerousMod(config, [
    "ios",
    async (c) => {
      const iosRoot = c.modRequest.platformProjectRoot;
      const extDir = path.join(iosRoot, EXTENSION_NAME);
      const extensionBundleId = `${c.ios?.bundleIdentifier}.${EXTENSION_NAME}`;

      if (!fs.existsSync(extDir)) {
        fs.mkdirSync(extDir, { recursive: true });
      }

      const src = path.join(
        c.modRequest.projectRoot,
        "node_modules/react-native-wireguard-turbo/ios/WireGuardTunnelProvider.swift",
      );
      fs.copyFileSync(src, path.join(extDir, "PacketTunnelProvider.swift"));

      const wgQuickSrc = path.join(
        c.modRequest.projectRoot,
        "node_modules/react-native-wireguard-turbo/ios/TunnelConfiguration+WgQuickConfig.swift",
      );
      const stringSrc = path.join(
        c.modRequest.projectRoot,
        "node_modules/react-native-wireguard-turbo/ios/String+ArrayConversion.swift",
      );
      fs.copyFileSync(
        wgQuickSrc,
        path.join(extDir, "TunnelConfiguration+WgQuickConfig.swift"),
      );

      fs.copyFileSync(
        stringSrc,
        path.join(extDir, "String+ArrayConversion.swift"),
      );

      fs.writeFileSync(
        path.join(extDir, `${EXTENSION_NAME}-Info.plist`),
        `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleDisplayName</key>
  <string>${EXTENSION_NAME}</string>
  <key>CFBundleExecutable</key>
  <string>$(EXECUTABLE_NAME)</string>
  <key>CFBundleIdentifier</key>
  <string>${extensionBundleId}</string>
  <key>CFBundleInfoDictionaryVersion</key>
  <string>6.0</string>
  <key>CFBundleName</key>
  <string>$(PRODUCT_NAME)</string>
  <key>CFBundlePackageType</key>
  <string>XPC!</string>
  <key>CFBundleShortVersionString</key>
  <string>$(MARKETING_VERSION)</string>
  <key>CFBundleVersion</key>
  <string>1</string>
  <key>NSExtension</key>
  <dict>
    <key>NSExtensionPointIdentifier</key>
    <string>com.apple.networkextension.packet-tunnel</string>
    <key>NSExtensionPrincipalClass</key>
    <string>$(PRODUCT_MODULE_NAME).PacketTunnelProvider</string>
  </dict>
</dict>
</plist>`,
      );

      fs.writeFileSync(
        path.join(extDir, `${EXTENSION_NAME}.entitlements`),
        `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>com.apple.developer.networking.networkextension</key>
  <array>
    <string>packet-tunnel-provider</string>
  </array>
  <key>com.apple.security.application-groups</key>
  <array>
    <string>${appGroupId}</string>
  </array>
</dict>
</plist>`,
      );

      return c;
    },
  ]);
}

function withExtensionTarget(config, appGroupId) {
  return withXcodeProject(config, (c) => {
    const project = c.modResults;
    const extensionBundleId = `${c.ios?.bundleIdentifier}.${EXTENSION_NAME}`;

    const targets = project.pbxNativeTargetSection();
    const exists = Object.values(targets).some(
      (t) => t && t.name === EXTENSION_NAME,
    );
    if (exists) return c;

    const target = project.addTarget(
      EXTENSION_NAME,
      "app_extension",
      EXTENSION_NAME,
      extensionBundleId,
    );

    const extGroup = project.addPbxGroup([], EXTENSION_NAME, EXTENSION_NAME);
    const mainGroupUuid = project.getFirstProject().firstProject.mainGroup;
    project.addToPbxGroup(extGroup.uuid, mainGroupUuid);

    project.addBuildPhase(
      [
        `${EXTENSION_NAME}/PacketTunnelProvider.swift`,
        `${EXTENSION_NAME}/TunnelConfiguration+WgQuickConfig.swift`,
        `${EXTENSION_NAME}/String+ArrayConversion.swift`,
      ],
      "PBXSourcesBuildPhase",
      "Sources",
      target.uuid,
    );

    project.addBuildPhase(
      [
        "../node_modules/react-native-wireguard-turbo/ios/WireGuardKit.xcframework",
      ],
      "PBXFrameworksBuildPhase",
      "Frameworks",
      target.uuid,
    );

    const buildConfigs = project.pbxXCBuildConfigurationSection();
    Object.keys(buildConfigs).forEach((key) => {
      const cfg = buildConfigs[key];
      if (
        cfg.buildSettings &&
        cfg.buildSettings.PRODUCT_NAME === `"${EXTENSION_NAME}"`
      ) {
        cfg.buildSettings.SWIFT_VERSION = "5.5";
        cfg.buildSettings.IPHONEOS_DEPLOYMENT_TARGET = "15.0";
        cfg.buildSettings.INFOPLIST_FILE = `${EXTENSION_NAME}/${EXTENSION_NAME}-Info.plist`;
        cfg.buildSettings.CODE_SIGN_ENTITLEMENTS = `${EXTENSION_NAME}/${EXTENSION_NAME}.entitlements`;
        cfg.buildSettings.DEVELOPMENT_TEAM = c.ios?.appleTeamId || "";
        cfg.buildSettings.CODE_SIGN_STYLE = "Automatic";
        cfg.buildSettings.MARKETING_VERSION = "1.0.0";
        cfg.buildSettings.CURRENT_PROJECT_VERSION = "1";
      }
    });

    return c;
  });
}

function withMainAppSigning(config) {
  return withXcodeProject(config, (c) => {
    const project = c.modResults;

    const nativeTargets = project.pbxNativeTargetSection();
    const mainTarget = Object.values(nativeTargets).find(
      (t) => t && t.productType === '"com.apple.product-type.application"',
    );
    if (!mainTarget) return c;

    const configList =
      project.pbxXCConfigurationList()[mainTarget.buildConfigurationList];
    if (!configList) return c;

    const buildConfigs = project.pbxXCBuildConfigurationSection();
    configList.buildConfigurations.forEach(({ value: configId }) => {
      const cfg = buildConfigs[configId];
      if (cfg && cfg.buildSettings) {
        cfg.buildSettings.CODE_SIGN_STYLE = "Automatic";
        cfg.buildSettings.DEVELOPMENT_TEAM = c.ios?.appleTeamId || "";
      }
    });

    return c;
  });
}

module.exports = withWireGuard;
```

It would be very helpful if `EXTENSION_NAME = "WireGuardNetworkExtension";` matched your Extension App ID (`your_name.WireGuardNetworkExtension`).

There is also a method, `setSubscriptionExpiry(ms: number)`, and you can use it:

```typescript
await WG.setSubscriptionExpiry(Date.now() + 1000 * 60)
```

If the user launches the VPN from VPN Settings, not from the application, it checks whether this date has expired before connecting and rejects the connection if it has.

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