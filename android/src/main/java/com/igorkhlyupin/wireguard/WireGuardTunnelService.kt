package com.igorkhlyupin.wireguard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.util.concurrent.atomic.AtomicBoolean
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class WireGuardTunnelService : VpnService() {

  companion object {
    private const val TAG = "WireGuardSvc"
    private const val CH_ID = "wg"
    private const val N_ID = 42

    const val ACTION_START = "com.igorkhlyupin.wireguard.action.START_TUNNEL"
    const val ACTION_STOP  = "com.igorkhlyupin.wireguard.action.STOP_TUNNEL"
    const val EXTRA_CONFIG = "cfg"

    @JvmStatic
    val tunnelIsRunning: AtomicBoolean = AtomicBoolean(false)
  }

  private var backend: GoBackend? = null
  private var tunnel: Tunnel? = null
  private val starting = AtomicBoolean(false)
  private var currentConfig: Config? = null

  private val cm by lazy {getSystemService(ConnectivityManager::class.java)}
  private val netCb = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(n : Network) {
      Log.d(TAG, "Network available -> rebind")
      rebind(n)
    }
    override fun onLost(n : Network) {
      Log.d(TAG, "Network lost -> rebind to default")
      rebind(null)
    }
    override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) {
      Log.d(TAG, "Network capabilities changed -> rebind")
      rebind(n)
    }
  }

  override fun onCreate() {
    super.onCreate()
    if (Build.VERSION.SDK_INT >= 26) {
      val nm = getSystemService(NotificationManager::class.java)
      nm.createNotificationChannel(
        NotificationChannel(CH_ID, "WireGuard", NotificationManager.IMPORTANCE_LOW)
      )
    }
    val notif = NotificationCompat.Builder(this, CH_ID)
      .setContentTitle("WireGuard")
      .setContentText("Preparing VPN…")
      .setSmallIcon(android.R.drawable.stat_sys_warning)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
    startForeground(N_ID, notif)
    Log.d(TAG, "Service created")

  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        Log.d(TAG, "Stop requested")
        stopTunnel()
        stopSelf()
        return Service.START_NOT_STICKY
      }
      ACTION_START, null -> {
        Log.d(TAG, "Start requested")
        val cfgText = intent?.getStringExtra(EXTRA_CONFIG)
        if (cfgText.isNullOrBlank()) {
          Log.e(TAG, "Missing EXTRA_CONFIG; stopping")
          stopSelf()
          return Service.START_NOT_STICKY
        }
        startTunnel(cfgText)
      }
    }
    return Service.START_NOT_STICKY
  }

  private fun startTunnel(cfgText: String) {
    if (!starting.compareAndSet(false, true)) {
      Log.d(TAG, "Start already in progress; ignoring")
      return
    }
    Thread {
      try {
        Log.d(TAG, "Bringing tunnel up…")
        val cfg = Config.parse(cfgText.byteInputStream())

        currentConfig = cfg

        val be = GoBackend(this)
        val tn = object : Tunnel {
          override fun getName(): String = "WireGuard"
          override fun onStateChange(state: Tunnel.State) {
            Log.d(TAG, "state=$state")
            if (state == Tunnel.State.DOWN) {
              tunnelIsRunning.set(false)
              try { cm.unregisterNetworkCallback(netCb) } catch (_: Throwable) {}
              try { if (Build.VERSION.SDK_INT >= 22) setUnderlyingNetworks(emptyArray()) } catch (_: Throwable) {}
              try { backend?.let { b -> tunnel?.let { t -> b.setState(t, Tunnel.State.DOWN, null) } } } catch (_: Throwable) {}
              if (Build.VERSION.SDK_INT >= 24) stopForeground(Service.STOP_FOREGROUND_REMOVE) else stopForeground(true)
              stopSelf()
            }
          }
        }

        cm.activeNetwork?.let { n ->
          try {
            setUnderlyingNetworks(arrayOf(n))
          } catch (t: Throwable) {
            Log.w(TAG, "Failed to set underlying networks", t)
          }
        }


        be.setState(tn, Tunnel.State.UP, cfg)
        cm.registerDefaultNetworkCallback(netCb)
        backend = be
        tunnel = tn
        Log.d(TAG, "Tunnel up")
        tunnelIsRunning.set(true)

        val nm = getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(this, CH_ID)
          .setContentTitle("WireGuard")
          .setContentText("Connected")
          .setSmallIcon(android.R.drawable.stat_sys_warning)
          .setOngoing(true)
          .setPriority(NotificationCompat.PRIORITY_LOW)
          .build()
        nm.notify(N_ID, notif)
      } catch (t: Throwable) {
        Log.e(TAG, "Tunnel failed", t)
        stopSelf()
      } finally {
        starting.set(false)
      }
    }.start()
  }

  private fun stopTunnel() {
    try {
      try { cm.unregisterNetworkCallback(netCb) } catch (_: Throwable) {}
      tunnelIsRunning.set(false)
      val be = backend
      val tn = tunnel
      if (be != null && tn != null) {
        Log.d(TAG, "Bringing tunnel down…")
        be.setState(tn, Tunnel.State.DOWN, null)
        Log.d(TAG, "Tunnel down")
      }
    } catch (t: Throwable) {
      Log.e(TAG, "Error stopping tunnel", t)
    } finally {
      backend = null
      tunnel = null
      currentConfig = null
    }
  }

  private fun rebind(network: Network?) {
    if (!tunnelIsRunning.get()) return
    val be = backend ?: return
    val tn = tunnel ?: return
    val cfg = currentConfig ?: return

    try {
      setUnderlyingNetworks(
        network?.let { arrayOf(it) } ?: emptyArray()
      )

      be.setState(tn, Tunnel.State.UP, cfg)
      Log.d(TAG, "Rebound tunnel on network=${network?.toString() ?: "default"}")
    } catch (t: Throwable) {
      Log.w(TAG, "Rebind failed; will rely on next event", t)
    }
  }

  override fun onDestroy() {
    Log.d(TAG, "Service destroyed from system")
    try { cm.unregisterNetworkCallback(netCb) } catch (_: Throwable) {}
    stopTunnel()
    stopForeground(STOP_FOREGROUND_REMOVE)
    Log.d(TAG, "Service destroyed")
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
