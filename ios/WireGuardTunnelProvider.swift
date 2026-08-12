import NetworkExtension
import WireGuardKit
import UserNotifications

class PacketTunnelProvider: NEPacketTunnelProvider {

  private var adapter: WireGuardAdapter?

  // App Group name must match what the main app uses
  private var appGroupId: String {
    "group." + Bundle.main.bundleIdentifier!
      .replacingOccurrences(of: ".WireGuardNetworkExtension", with: "")
  }

  override func startTunnel(options: [String: NSObject]?,
                            completionHandler: @escaping (Error?) -> Void) {

    // Check subscription before doing anything
    let defaults = UserDefaults(suiteName: appGroupId)
    let expiry = defaults?.object(forKey: "subscriptionExpiry") as? Date

    guard let expiry = expiry else {
      completionHandler(ProviderError.notSubscribed); return
    }

    guard expiry > Date() else {
      let content = UNMutableNotificationContent()
      content.title = "VPN Disconnected"
      content.body = "Your subscription has expired."
      let request = UNNotificationRequest(identifier: "sub_expired", content: content, trigger: nil)
      UNUserNotificationCenter.current().add(request)
      completionHandler(ProviderError.subscriptionExpired); return
    }

    // Read wg-quick config string saved by WireGuardModule.connect()
    guard
      let proto = protocolConfiguration as? NETunnelProviderProtocol,
      let configString = proto.providerConfiguration?["wgConfig"] as? String
    else {
      completionHandler(ProviderError.missingConfig); return
    }

    // Parse wg-quick format into TunnelConfiguration using WireGuardKit
    let tunnelConfig: TunnelConfiguration
    do {
      tunnelConfig = try TunnelConfiguration(fromWgQuickConfig: configString, called: "wg0")
    } catch {
      completionHandler(error); return
    }

    // Start the actual WireGuard tunnel
    adapter = WireGuardAdapter(with: self) { _, message in
      NSLog("WireGuard: \(message)")
    }
    adapter?.start(tunnelConfiguration: tunnelConfig, completionHandler: completionHandler)
  }

  override func stopTunnel(with reason: NEProviderStopReason,
                           completionHandler: @escaping () -> Void) {
    adapter?.stop { _ in completionHandler() }
  }
}

enum ProviderError: Error {
  case notSubscribed
  case subscriptionExpired
  case missingConfig
}
