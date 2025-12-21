package com.igorkhlyupin.wireguard

import android.net.NetworkRequest
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

class WireGuardTunnelService : com.wireguard.android.backend.GoBackend.VpnService() {

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
  // vpn listener
  private val vpnCb = object : ConnectivityManager.NetworkCallback() {
  override fun onAvailable(n: Network) {
    val lp = cm.getLinkProperties(n)
    val ips = lp?.linkAddresses?.joinToString { it.address?.hostAddress ?: "?" } ?: "none"
    Log.d(TAG, "[VPN-ONLY] onAvailable n=$n ips=$ips")
  }
  override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) {
    val hasVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    Log.d(TAG, "[VPN-ONLY] onCaps n=$n hasVpn=$hasVpn caps=$caps")
  }
  override fun onLost(n: Network) {
    Log.d(TAG, "[VPN-ONLY] onLost n=$n")
  }
}

  private val cm by lazy {getSystemService(ConnectivityManager::class.java)}
  private fun isMyVpnActiveNow(): Boolean {
    val cfg = currentConfig ?: return false
    val myIps = try {
      cfg.`interface`.addresses.map { it.toString().substringBefore('/') }.toSet()
    } catch (_: Throwable) {
      emptySet()
    }
    if (myIps.isEmpty()) return false

    for (n in cm.allNetworks) {
      val caps = cm.getNetworkCapabilities(n) ?: continue
      if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
      val lp = cm.getLinkProperties(n) ?: continue
      val vpnIps = lp.linkAddresses.mapNotNull { it.address?.hostAddress }
      if (vpnIps.any { it in myIps }) return true
    }
    return false
  }
  private val netCb = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(n : Network) {
      // Log.d(TAG, "Network available -> rebind!!")
      // Log.d(TAG, "New log before")
      // Log.d(TAG, "[MINE] onAvailable=${isMyVpnActiveNow()}")
      // Log.d(TAG, "New log after")
      rebind(n)
    }
    override fun onLost(n : Network) {
      // Log.d(TAG, "Network lost -> rebind to default!!")
      // Log.d(TAG, "New log before")
      // Log.d(TAG, "[MINE] onLost=${isMyVpnActiveNow()}")
      // Log.d(TAG, "New log after")
      rebind(null)
    }
    override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) {
      // Log.d(TAG, "Network capabilities changed -> rebind!!")
      // Log.d(TAG, "New log before")
      // Log.d(TAG, "[MINE] onCaps=${isMyVpnActiveNow()}")
      // Log.d(TAG, "New log after")
      rebind(n)
    }
  }

  override fun onCreate() {
    Log.d("WireGuardSvc", "BUILD_MARK 2025-10-20T20:45");
    super.onCreate()
    Log.d("WireGuardSvc", "BUILD_MARK 2025-10-20T20:45");
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
        try { getSystemService(NotificationManager::class.java).cancel(N_ID) } catch (_: Throwable) {}
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

              try { getSystemService(NotificationManager::class.java).cancel(N_ID) } catch (_: Throwable) {}

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
        
        try {
          val vpnReqBuilder = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)   // ADDED
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vpnReqBuilder.setIncludeOtherUidNetworks(true)         // ADDED: see VPN agent despite owner-UID exclusion
          }
          val vpnReq = vpnReqBuilder.build()                        // ADDED
          cm.registerNetworkCallback(vpnReq, vpnCb)                 // ADDED
          Log.d(TAG, "[VPN-ONLY] listener registered (includeOtherUidNetworks=${Build.VERSION.SDK_INT >= Build.VERSION_CODES.S})") // ADDED
        } catch (t: Throwable) {
          Log.w(TAG, "[VPN-ONLY] register failed", t)
        }


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
        try { getSystemService(NotificationManager::class.java).cancel(N_ID) } catch (_: Throwable) {}
        stopSelf()
      } finally {
        starting.set(false)
      }
    }.start()
  }

  private fun stopTunnel() {
    try {
      try { cm.unregisterNetworkCallback(netCb) } catch (_: Throwable) {}
      try { cm.unregisterNetworkCallback(vpnCb) } catch (_: Throwable) {}
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
      try { getSystemService(NotificationManager::class.java).cancel(N_ID) } catch (_: Throwable) {}
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
    try { cm.unregisterNetworkCallback(vpnCb) } catch (_: Throwable) {}
    stopTunnel()
    try { getSystemService(NotificationManager::class.java).cancel(N_ID) } catch (_: Throwable) {}
    stopForeground(STOP_FOREGROUND_REMOVE)
    Log.d(TAG, "Service destroyed")
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
