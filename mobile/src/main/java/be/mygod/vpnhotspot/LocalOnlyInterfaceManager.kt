package be.mygod.vpnhotspot

import android.widget.Toast
import be.mygod.vpnhotspot.App.Companion.app
import be.mygod.vpnhotspot.net.Routing
import be.mygod.vpnhotspot.net.VpnMonitor
import java.net.InetAddress
import java.net.SocketException

class LocalOnlyInterfaceManager(val downstream: String, private val owner: InetAddress? = null) : VpnMonitor.Callback {
    private var routing: Routing? = null
    private var dns = emptyList<InetAddress>()

    init {
        app.cleanRoutings[this] = this::clean
        VpnMonitor.registerCallback(this) { initRouting() }
    }

    override fun onAvailable(ifname: String, dns: List<InetAddress>) {
        val routing = routing
        initRouting(ifname, if (routing == null) owner else {
            routing.stop()
            check(routing.upstream == null)
            routing.hostAddress
        }, dns)
    }
    override fun onLost(ifname: String) {
        val routing = routing ?: return
        if (!routing.stop()) app.toast(R.string.noisy_su_failure)
        initRouting(null, routing.hostAddress, emptyList())
    }

    private fun clean() {
        val routing = routing ?: return
        routing.started = false
        initRouting(routing.upstream, routing.hostAddress, dns)
    }

    private fun initRouting(upstream: String? = null, owner: InetAddress? = this.owner,
                            dns: List<InetAddress> = this.dns) {
        try {
            val routing = Routing(upstream, downstream, owner)
            this.routing = routing
            this.dns = dns
            val strict = app.pref.getBoolean("service.repeater.strict", false)
            if (strict && upstream == null) return  // in this case, nothing to be done
            if (routing.ipForward()                 // local only interfaces may not enable ip_forward
                            .rule().forward(strict).masquerade(strict).dnsRedirect(dns).start()) return
            app.toast(R.string.noisy_su_failure)
        } catch (e: SocketException) {
            Toast.makeText(app, e.message, Toast.LENGTH_SHORT).show()
            routing = null
        }
    }

    fun stop() {
        VpnMonitor.unregisterCallback(this)
        app.cleanRoutings -= this
        if (routing?.stop() == false) app.toast(R.string.noisy_su_failure)
    }
}
