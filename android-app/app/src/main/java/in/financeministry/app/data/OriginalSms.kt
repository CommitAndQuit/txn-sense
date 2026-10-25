package `in`.financeministry.app.data

import android.content.Context
import android.provider.Telephony
import android.os.CancellationSignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest

/** Ephemeral UI result only. Never save, log, export or put into saved instance state. */
sealed interface OriginalSmsResult {
    data object Unavailable : OriginalSmsResult
    data object PermissionRequired : OriginalSmsResult
    class Found(val message: HistoricalSms) : OriginalSmsResult {
        override fun toString() = "OriginalSmsResult.Found(redacted)"
    }
}

internal class OriginalSmsLookup(private val fingerprint: suspend (String, Long, String) -> ByteArray) {
    suspend fun find(row: TransactionEntity, source: HistoricalSmsSource, permitted: () -> Boolean): OriginalSmsResult {
        val expected = row.sourceFingerprint ?: return OriginalSmsResult.Unavailable
        if (row.sourceType != "SMS" || expected.size != 32 || row.sourceTimestamp <= 0)
            return OriginalSmsResult.Unavailable
        if (!permitted()) return OriginalSmsResult.PermissionRequired
        val window = ImportWindow(row.sourceTimestamp, row.sourceTimestamp)
        var candidate: HistoricalSms? = null
        var matches = 0
        var scanned = 0
        return try {
            source.read(window) { message ->
                currentCoroutineContext().ensureActive()
                if (!permitted()) throw SecurityException()
                check(++scanned <= 200)
                // A delayed live SMS can have a much later inbox receipt date.
                if (window.contains(message.date) || message.sentDate == row.sourceTimestamp) {
                    val received = fingerprint(message.sender, message.date, message.body)
                    val matched = MessageDigest.isEqual(expected, received) ||
                        (message.sentDate > 0 && message.sentDate != message.date &&
                            MessageDigest.isEqual(expected, fingerprint(message.sender, message.sentDate, message.body)))
                    if (matched) { matches++; candidate = message }
                }
            }
            currentCoroutineContext().ensureActive()
            if (!permitted()) OriginalSmsResult.PermissionRequired
            else if (matches == 1) OriginalSmsResult.Found(requireNotNull(candidate))
            else OriginalSmsResult.Unavailable
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SecurityException) { OriginalSmsResult.PermissionRequired }
        catch (_: Exception) { OriginalSmsResult.Unavailable }
    }
}

/** Read-only, narrow inbox query; unlike an import it also considers delayed live delivery. */
internal class AndroidOriginalSmsSource(private val context: Context) : HistoricalSmsSource {
    override suspend fun read(window: ImportWindow, emit: suspend (HistoricalSms) -> Unit) = coroutineScope {
        check(window.start == window.end)
        val cancellation = CancellationSignal()
        val reading = async(Dispatchers.IO) {
            val columns = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT, Telephony.Sms.BODY)
            val cursor = context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, columns,
                "date = ? OR date_sent = ?", arrayOf(window.start.toString(), window.start.toString()),
                "date ASC", cancellation) ?: error("Inbox unavailable")
            cursor.use {
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    emit(HistoricalSms(it.getString(0).orEmpty(), it.getLong(1), it.getLong(2), it.getString(3).orEmpty()))
                }
            }
        }
        // Await is cancellable even when the provider query is blocked on its IO worker.
        // Signal it before coroutineScope waits for the child, which owns/closes its cursor.
        try { reading.await() } finally { cancellation.cancel() }
    }
}
