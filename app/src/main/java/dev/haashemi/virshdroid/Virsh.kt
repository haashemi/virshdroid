package dev.haashemi.virshdroid

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.UserInfo
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class Vm(val name: String, val state: String) {
    val running get() = state == "running"
}

/** Thrown when the server offers a host key the user has not accepted yet. */
class HostKeyException(val key: String, val type: String, val fingerprint: String, val changed: Boolean) :
    Exception("Host key not trusted")

object Virsh {
    suspend fun list(server: Server): List<Vm> =
        exec(server, virsh("list --all")).lines().drop(2).mapNotNull { line ->
            Regex("""^\s*\S+\s+(\S+)\s+(.+?)\s*$""").find(line)?.destructured?.let { (name, state) -> Vm(name, state) }
        }

    /** Runs `virsh <action> <vm>`, e.g. start, shutdown or destroy. */
    suspend fun run(server: Server, action: String, vm: String) {
        exec(server, virsh("$action '${vm.replace("'", "'\\''")}'"))
    }

    private fun virsh(args: String) = "LC_ALL=C virsh -c qemu:///system $args"

    /** Runs [command] over SSH and returns stdout, or throws with stderr when it exits non-zero. */
    private suspend fun exec(server: Server, command: String): String = withContext(Dispatchers.IO) {
        val hostKeys = PinnedHostKey(server.hostKey)
        val jsch = JSch().apply {
            hostKeyRepository = hostKeys
            if (server.useKey) {
                addIdentity(server.name, server.privateKey.encodeToByteArray(), null,
                    server.passphrase.takeIf { it.isNotEmpty() }?.encodeToByteArray())
            }
        }
        val session = jsch.getSession(server.user, server.host, server.port).apply {
            if (!server.useKey) setPassword(server.password)
            setConfig("StrictHostKeyChecking", "yes")
            timeout = 15_000
        }
        try {
            try {
                session.connect()
            } catch (e: JSchException) {
                hostKeys.untrusted(server.host)?.let { throw it }
                throw e
            }
            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            val out = channel.inputStream
            val err = channel.errStream
            channel.connect()
            val stdout = out.readBytes().decodeToString()
            val stderr = err.readBytes().decodeToString()
            while (!channel.isClosed) delay(20)
            if (channel.exitStatus != 0) error(stderr.trim().ifEmpty { "virsh exited with ${channel.exitStatus}" })
            stdout
        } finally {
            session.disconnect()
        }
    }
}

/** Trusts only the single host key saved for the server and remembers what the server offered. */
private class PinnedHostKey(private val trusted: String?) : HostKeyRepository {
    private var offered: ByteArray? = null
    private var result = HostKeyRepository.OK

    override fun check(host: String?, key: ByteArray): Int {
        offered = key
        val encoded = Base64.getEncoder().encodeToString(key)
        result = when (trusted) {
            null -> HostKeyRepository.NOT_INCLUDED
            encoded -> HostKeyRepository.OK
            else -> HostKeyRepository.CHANGED
        }
        return result
    }

    fun untrusted(host: String): HostKeyException? {
        val key = offered ?: return null
        if (result == HostKeyRepository.OK) return null
        val digest = MessageDigest.getInstance("SHA-256").digest(key)
        return HostKeyException(
            key = Base64.getEncoder().encodeToString(key),
            type = HostKey(host, key).type,
            fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest),
            changed = result == HostKeyRepository.CHANGED,
        )
    }

    override fun add(hostkey: HostKey?, ui: UserInfo?) {}
    override fun remove(host: String?, type: String?) {}
    override fun remove(host: String?, type: String?, key: ByteArray?) {}
    override fun getKnownHostsRepositoryID() = "pinned"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}
