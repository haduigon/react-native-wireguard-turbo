#import <React/RCTBridgeModule.h>

#ifdef RCT_NEW_ARCH_ENABLED
#import "WireGuardSpecs.h"
#endif

@interface RCT_EXTERN_MODULE(WireGuard, NSObject)

RCT_EXTERN__BLOCKING_SYNCHRONOUS_METHOD(ping)

RCT_EXTERN_METHOD(connect:(NSString *)config
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(disconnect:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(getState:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(isAnyVpnActive:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(isMyVpnActive:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(setSubscriptionExpiry:(double)timestamp
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

@end

#ifdef RCT_NEW_ARCH_ENABLED
@interface WireGuardModule (TurboModuleConformance) <NativeWireGuardSpec>
@end

@implementation WireGuardModule (TurboModuleConformance)
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:(const facebook::react::ObjCTurboModule::InitParams &)params {
  return std::make_shared<facebook::react::NativeWireGuardSpecJSI>(params);
}
@end
#endif
