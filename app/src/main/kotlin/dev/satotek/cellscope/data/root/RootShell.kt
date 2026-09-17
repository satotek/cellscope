package dev.satotek.cellscope.data.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/** Minimal `su -c` runner. KernelSU Next prompts the user on first use; after that it's silent. */
object RootShell {

    data class Result(val exit: Int, val out: String, val err: String) {
        val ok get() = exit == 0
    }

    suspend fun run(cmd: String, timeoutSec: Long = 10): Result = withContext(Dispatchers.IO) {
        runCatching {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(false).start()
            val out = StringBuilder(); val err = StringBuilder()
            val t1 = Thread { BufferedReader(InputStreamReader(p.inputStream)).use { r -> r.lineSequence().forEach { out.appendLine(it) } } }
            val t2 = Thread { BufferedReader(InputStreamReader(p.errorStream)).use { r -> r.lineSequence().forEach { err.appendLine(it) } } }
            t1.start(); t2.start()
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) { p.destroyForcibly(); return@withContext Result(-1, out.toString(), "timeout") }
            t1.join(1000); t2.join(1000)
            Result(p.exitValue(), out.toString(), err.toString())
        }.getOrElse { Result(-2, "", it.message ?: it.toString()) }
    }

    suspend fun available(): Boolean = run("id", 5).let { it.ok && it.out.contains("uid=0") }
}
