package com.openclaw.clawchat.network

import android.content.Context
import com.openclaw.clawchat.util.AppLog
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.inject.Inject

/**
 * Tailscale 网络管理器
 * 
 * 负责检测和监控 Tailscale 连接状态，支持 MagicDNS 解析
 */
class TailscaleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TailscaleManager"
        
        // Tailscale 网络接口名称
        private val TAILSCALE_INTERFACES = listOf("tun0", "tailscale0", "tun-tailscale")
    }
    
    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    
    /**
     * 检查 Tailscale 是否已连接
     * 
     * @return true 如果 Tailscale 网络接口可用
     */
    fun isTailscaleConnected(): Boolean {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.name in TAILSCALE_INTERFACES && iface.isUp) {
                    AppLog.d(TAG, "Tailscale interface found: ${iface.name}")
                    return true
                }
            }
            AppLog.d(TAG, "No Tailscale interface found")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check Tailscale status: ${e.message}", e)
            false
        }
    }
    
    /**
     * 获取 Tailscale IP 地址
     * 
     * @return Tailscale IPv4 地址，如果没有则返回 null
     */
    fun getTailscaleIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.name in TAILSCALE_INTERFACES && iface.isUp) {
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            val ip = address.hostAddress
                            AppLog.d(TAG, "Found Tailscale IP: $ip on ${iface.name}")
                            return ip
                        }
                    }
                }
            }
            AppLog.d(TAG, "No Tailscale IP found")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Tailscale IP: ${e.message}", e)
            null
        }
    }
    
    /**
     * 获取所有 Tailscale 网络接口信息
     */
    fun getTailscaleInterfaceInfo(): List<TailscaleInterfaceInfo> {
        return try {
            val result = mutableListOf<TailscaleInterfaceInfo>()
            val interfaces = NetworkInterface.getNetworkInterfaces()
            
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.name in TAILSCALE_INTERFACES) {
                    val addresses = mutableListOf<String>()
                    val inetAddresses = iface.inetAddresses
                    while (inetAddresses.hasMoreElements()) {
                        val addr = inetAddresses.nextElement()
                        if (!addr.isLoopbackAddress) {
                            addresses.add(addr.hostAddress ?: continue)
                        }
                    }
                    
                    result.add(
                        TailscaleInterfaceInfo(
                            name = iface.name,
                            addresses = addresses,
                            isUp = iface.isUp,
                            mtu = iface.mtu
                        )
                    )
                }
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get interface info: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * 解析 MagicDNS 名称
     * 
     * @param name MagicDNS 名称 (如：my-server.tailnet-name.ts.net)
     * @return 解析后的 IP 地址，失败返回 null
     */
    suspend fun resolveMagicDns(name: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val addresses = InetAddress.getAllByName(name)
                val ip = addresses.firstOrNull { it is Inet4Address }?.hostAddress
                AppLog.d(TAG, "Resolved $name -> $ip")
                ip
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve $name: ${e.message}")
                null
            }
        }
    }
    
    /**
     * 监控 Tailscale 连接状态变化
     * 
     * @return Flow 发射连接状态变化
     */
    fun observeTailscaleConnection(): Flow<Boolean> = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isTailscaleConnected())
            }
            
            override fun onLost(network: Network) {
                trySend(isTailscaleConnected())
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(isTailscaleConnected())
            }
        }
        
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        
        // 发射初始状态
        trySend(isTailscaleConnected())
        
        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }.distinctUntilChanged()
    
    /**
     * 构建 Tailscale Gateway URL
     * 
     * @param tailnetName Tailnet 名称
     * @param deviceName 设备名称
     * @param port 端口号 (默认 18789)
     * @param useTls 是否使用 TLS
     * @return WebSocket URL
     */
    fun buildTailscaleUrl(
        tailnetName: String,
        deviceName: String,
        port: Int = 18789,
        useTls: Boolean = false
    ): String {
        val hostname = "$deviceName.$tailnetName.ts.net"
        val protocol = if (useTls) "wss" else "ws"
        return "$protocol://$hostname:$port/ws"
    }
    
    /**
     * 使用已解析的 IP 构建 URL
     * 
     * @param ipAddress Tailscale IP 地址
     * @param port 端口号
     * @param useTls 是否使用 TLS
     * @return WebSocket URL
     */
    fun buildUrlFromIp(
        ipAddress: String,
        port: Int = 18789,
        useTls: Boolean = false
    ): String {
        val protocol = if (useTls) "wss" else "ws"
        return "$protocol://$ipAddress:$port/ws"
    }
}

/**
 * Tailscale 网络接口信息
 */
data class TailscaleInterfaceInfo(
    val name: String,
    val addresses: List<String>,
    val isUp: Boolean,
    val mtu: Int
)
