package com.igorkhlyupin.wireguard

import android.content.Intent
import android.os.Build
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.turbomodule.core.interfaces.TurboModule
import com.igorkhlyupin.wireguard.WireGuardTunnelService
import com.igorkhlyupin.wireguard.NativeWireGuardSpec

class WireGuardModule(private val ctx: ReactApplicationContext) :
  NativeWireGuardSpec(ctx) {

  companion object { const val NAME = "WireGuard" }

  override fun getName() = NAME

  override fun ping(): String = "pong"

  override fun connect(config: String, promise: Promise) {
    try {
      val i = Intent(ctx, WireGuardTunnelService::class.java)
        .setAction(WireGuardTunnelService.ACTION_START)
        .putExtra(WireGuardTunnelService.EXTRA_CONFIG, config)

      if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
      promise.resolve(null)
    } catch (t: Throwable) {
      promise.reject("E_WG_CONNECT", t)
    }
  }

  override fun disconnect(promise: Promise) {
    try {
      val i = Intent(ctx, WireGuardTunnelService::class.java)
        .setAction(WireGuardTunnelService.ACTION_STOP)
      ctx.startService(i)
      promise.resolve(null)
    } catch (t: Throwable) {
      promise.reject("E_WG_DISCONNECT", t)
    }
  }

  override fun getState(promise: Promise) {
    val state = if (WireGuardTunnelService.tunnelIsRunning.get()) "UP" else "DOWN"
    promise.resolve(state)
}
}
