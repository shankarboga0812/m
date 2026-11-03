package com.chandu.gatesystem.ai2027.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.chandu.gatesystem.ai2027.data.*
import com.chandu.gatesystem.ai2027.data.db.AppDatabase
import com.chandu.gatesystem.ai2027.domain.BankCodec

/** Installs the bundled 250-questions-per-subject bank (practice / exam / mock pools) into Room on first launch. Idempotent. */
class BankInstaller(private val context: Context, private val db: AppDatabase, private val clock: Clock) {
    data class Result(val installed: Int, val total: Int)

    suspend fun installIfNeeded(onProgress: (Int, Int) -> Unit = { _, _ -> }): Result {
        val dao = db.content()
        val expected = BankCodec.subjects.size * BankCodec.PER_SUBJECT
        if (dao.bankCount() >= expected) return Result(0, dao.bankCount())
        val qs = BankCodec.fromJson(context.assets.open(BankCodec.ASSET).bufferedReader().use { it.readText() })
        var done = 0; var inserted = 0
        for (chunk in qs.chunked(250)) {
            db.withTransaction {
                for (q in chunk) {
                    val (e, opts) = q.toRows(clock.millis())
                    if (dao.insertQuestion(e) != -1L) { dao.insertOptions(opts); inserted++ }
                }
            }
            done += chunk.size; onProgress(done, qs.size)
        }
        return Result(inserted, dao.bankCount())
    }
}
