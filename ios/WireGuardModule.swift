import Foundation
import NetworkExtension
import React

@objc(WireGuard)
class WireGuard: NSObject {

  // Derives the extension bundle ID automatically from the host app.
  // User must name their Network Extension target: {appBundleId}.WireGuardNetworkExtension
  private var extensionBundleId: String {
    Bundle.main.bundleIdentifier! + ".WireGuardNetworkExtension"
  }

  // Derives the App Group name automatically from the host app.
  // Used to share subscription expiry date with the PacketTunnelProvider.
  private var appGroupId: String {
    "group." + Bundle.main.bundleIdentifier!
  }

  @objc static func requiresMainQueueSetup() -> Bool { return false }

  // ping — simple health check, JS calls this to verify module is loaded
  @objc func ping() -> String {
    return "pong"
  }

  // connect — parses wg-quick config string, saves VPN profile, starts tunnel
  // First call shows iOS VPN permission dialog to the user
  @objc func connect(_ config: String,
                     resolve: @escaping RCTPromiseResolveBlock,
                     reject: @escaping RCTPromiseRejectBlock) {
    NETunnelProviderManager.loadAllFromPreferences { managers, error in
      if let error = error {
        reject("E_LOAD", error.localizedDescription, error); return
      }

      // Reuse existing manager if one exists, otherwise create new one
      let manager = managers?.first ?? NETunnelProviderManager()

      let proto = NETunnelProviderProtocol()
      // Must match the bundle ID of the Network Extension target in the host app
      proto.providerBundleIdentifier = self.extensionBundleId
      proto.serverAddress = "WireGuard"
      // Store raw wg-quick config — PacketTunnelProvider reads this key
      proto.providerConfiguration = ["wgConfig": config]

      manager.protocolConfiguration = proto
      manager.localizedDescription = "WireGuard"
      manager.isEnabled = true

      // Save to iOS VPN preferences — triggers permission dialog on first call
      manager.saveToPreferences { error in
        if let error = error {
          reject("E_SAVE", error.localizedDescription, error); return
        }
        // Must reload after saving before starting
        manager.loadFromPreferences { error in
          if let error = error {
            reject("E_LOAD", error.localizedDescription, error); return
          }
          do {
            try manager.connection.startVPNTunnel()
            resolve(nil)
          } catch {
            reject("E_START", error.localizedDescription, error)
          }
        }
      }
    }
  }

  // disconnect — stops the tunnel but keeps the VPN profile in iOS Settings
  @objc func disconnect(_ resolve: @escaping RCTPromiseResolveBlock,
                        reject: @escaping RCTPromiseRejectBlock) {
    NETunnelProviderManager.loadAllFromPreferences { managers, error in
      if let error = error {
        reject("E_LOAD", error.localizedDescription, error); return
      }
      managers?.first?.connection.stopVPNTunnel()
      resolve(nil)
    }
  }

  // getState — returns current VPN status as string matching Android states
  @objc func getState(_ resolve: @escaping RCTPromiseResolveBlock,
                      reject: @escaping RCTPromiseRejectBlock) {
    NETunnelProviderManager.loadAllFromPreferences { managers, error in
      if let error = error {
        reject("E_LOAD", error.localizedDescription, error); return
      }
      guard let manager = managers?.first else {
        resolve("DOWN"); return
      }
      switch manager.connection.status {
      case .connected:     resolve("UP")
      case .disconnected:  resolve("DOWN")
      case .invalid:       resolve("ERROR")
      default:             resolve("UNKNOWN")
      }
    }
  }

  // isAnyVpnActive — checks if ANY VPN is active on the device (not just ours)
  @objc func isAnyVpnActive(_ resolve: @escaping RCTPromiseResolveBlock,
                             reject: @escaping RCTPromiseRejectBlock) {
    NETunnelProviderManager.loadAllFromPreferences { managers, error in
      if let error = error {
        reject("E_LOAD", error.localizedDescription, error); return
      }
      let active = managers?.contains { $0.connection.status == .connected } ?? false
      resolve(active)
    }
  }

  // isMyVpnActive — checks if specifically OUR WireGuard tunnel is active
  @objc func isMyVpnActive(_ resolve: @escaping RCTPromiseResolveBlock,
                            reject: @escaping RCTPromiseRejectBlock) {
    NETunnelProviderManager.loadAllFromPreferences { [weak self] managers, error in
      guard let self = self else { return }
      if let error = error {
        reject("E_LOAD", error.localizedDescription, error); return
      }
      let mine = managers?.first {
        ($0.protocolConfiguration as? NETunnelProviderProtocol)?
          .providerBundleIdentifier == self.extensionBundleId
      }
      resolve(mine?.connection.status == .connected)
    }
  }

  // setSubscriptionExpiry — saves expiry timestamp to App Group shared storage
  // PacketTunnelProvider reads this before every connection attempt
  @objc func setSubscriptionExpiry(_ timestamp: Double,
                                   resolve: @escaping RCTPromiseResolveBlock,
                                   reject: @escaping RCTPromiseRejectBlock) {
    let date = Date(timeIntervalSince1970: timestamp / 1000)
    UserDefaults(suiteName: appGroupId)?.set(date, forKey: "subscriptionExpiry")
    resolve(nil)
  }
}
