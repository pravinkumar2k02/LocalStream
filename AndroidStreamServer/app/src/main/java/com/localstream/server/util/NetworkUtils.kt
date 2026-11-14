package com.localstream.server.util

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

data class NetworkAddressInfo(
    val interfaceName: String,
    val displayName: String,
    val ipAddress: String,
    val isHotspot: Boolean
)

object NetworkUtils {

    /**
     * Enumerates all active IPv4 non-loopback addresses on the device.
     */
    fun getAvailableNetworkAddresses(): List<NetworkAddressInfo> {
        val list = mutableListOf<NetworkAddressInfo>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue

                val addresses = Collections.list(intf.inetAddresses)
                for (addr in addresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddr = addr.hostAddress ?: continue
                        val name = intf.name.lowercase()
                        val isHotspot = name.contains("ap") ||
                                        name.contains("softap") ||
                                        name.contains("rndis") ||
                                        hostAddr.startsWith("192.168.43.")

                        val display = when {
                            isHotspot -> "Device Hotspot (${intf.name})"
                            name.startsWith("wlan") -> "Wi-Fi (${intf.name})"
                            name.startsWith("eth") -> "Ethernet (${intf.name})"
                            name.startsWith("rmnet") -> "Mobile Network (${intf.name})"
                            else -> intf.displayName ?: intf.name
                        }

                        list.add(NetworkAddressInfo(intf.name, display, hostAddr, isHotspot))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sort so that Hotspot and Wi-Fi addresses appear first
        return list.sortedWith(compareByDescending<NetworkAddressInfo> { it.isHotspot }
            .thenByDescending { it.interfaceName.startsWith("wlan") })
    }

    /**
     * Gets the primary recommended IP address for streaming (hotspot or wifi).
     */
    fun getPrimaryIpAddress(): String {
        val addresses = getAvailableNetworkAddresses()
        // Prefer Hotspot address, then Wi-Fi, then first available
        val hotspot = addresses.firstOrNull { it.isHotspot }
        if (hotspot != null) return hotspot.ipAddress

        val wifi = addresses.firstOrNull { it.interfaceName.startsWith("wlan") }
        if (wifi != null) return wifi.ipAddress

        return addresses.firstOrNull()?.ipAddress ?: "192.168.43.1"
    }
}
