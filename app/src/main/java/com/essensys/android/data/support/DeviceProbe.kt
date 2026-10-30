package com.essensys.android.data.support

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.essensys.android.BuildConfig
import java.util.Locale
import java.util.TimeZone

enum class NetworkType(val label: String) {
    WIFI("Wi-Fi"),
    CELLULAR("Données mobiles"),
    VPN("VPN"),
    OTHER("Autre réseau"),
    NONE("Aucun réseau"),
}

/** Informations sur le téléphone et l'appli, lues sans permission particulière. */
interface DeviceProbe {
    val appVersion: String
    val appBuild: String
    val osVersion: String
    val model: String
    val locale: String get() = Locale.getDefault().toLanguageTag()
    val timeZone: String get() = TimeZone.getDefault().id
    fun networkType(): NetworkType
}

/** Téléphone réel (`ACCESS_NETWORK_STATE` est déjà déclaré). */
class AndroidDeviceProbe(context: Context) : DeviceProbe {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    override val appVersion = BuildConfig.VERSION_NAME
    override val appBuild = BuildConfig.VERSION_CODE.toString()
    override val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    override val model = "${Build.MANUFACTURER} ${Build.MODEL}"

    override fun networkType(): NetworkType {
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork) ?: return NetworkType.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            else -> NetworkType.OTHER
        }
    }
}

/** Valeurs fixes : tests sur JVM et previews. */
class StaticDeviceProbe(
    override val appVersion: String = BuildConfig.VERSION_NAME,
    override val appBuild: String = BuildConfig.VERSION_CODE.toString(),
    override val osVersion: String = "test",
    override val model: String = "test",
    private val network: NetworkType = NetworkType.WIFI,
) : DeviceProbe {
    override fun networkType() = network
}
