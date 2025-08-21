package com.igorkhlyupin.wireguard

import com.facebook.react.TurboReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class WireGuardPackage : TurboReactPackage() {

  override fun getModule(name: String, context: ReactApplicationContext): NativeModule? {
    return if (name == WireGuardModule.NAME) WireGuardModule(context) else null
  }

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
    val map = HashMap<String, ReactModuleInfo>()
    map[WireGuardModule.NAME] = ReactModuleInfo(
      /* name */ WireGuardModule.NAME,
      /* className */ WireGuardModule::class.java.name,
      /* canOverrideExistingModule */ false,
      /* needsEagerInit */ false,
      /* hasConstants */ false,
      /* isCxxModule */ false,
      /* isTurboModule */ true
    )
    return ReactModuleInfoProvider { map }
  }
}
