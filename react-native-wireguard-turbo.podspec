require "json"
package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "react-native-wireguard-turbo"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.author = { "Igor Khlyupin" => "haduigon@gmail.com" }
  s.platforms    = { :ios => "15.0" }
  s.source       = { :git => package["repository"]["url"], :tag => "v#{s.version}" }

  # Swift + ObjC source files in ios/ folder
  s.source_files = "ios/**/*.{swift,mm,h}"

  # Exclude WireGuardTunnelProvider — user adds this to their extension target manually
  s.exclude_files = "ios/WireGuardTunnelProvider.swift", "ios/TunnelConfiguration+WgQuickConfig.swift", "ios/String+ArrayConversion.swift"


  # Prebuilt WireGuardKit XCFramework
  s.vendored_frameworks = "ios/WireGuardKit.xcframework"

  # Apple's NetworkExtension framework
  s.frameworks = "NetworkExtension"

  s.dependency "React-Core"

  s.pod_target_xcconfig = {
    "SWIFT_VERSION" => "5.5"
  }
end
