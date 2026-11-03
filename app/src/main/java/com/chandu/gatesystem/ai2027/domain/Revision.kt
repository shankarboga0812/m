package com.chandu.gatesystem.ai2027.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 1-4-7 scheduling, exactly as the supplied plan defines it:
 * a topic learned on day N is due for active recall on N+1, N+4 and N+7.
 * (The document lists each day's due topics under "147 DUE (Day +1 / +4 / +7)".)
 * "Weak/error topics return within 48 hours" is handled by [ErrorMonsterEngine].
 */
data class RevisionEntry(
    val itemKey: String,          // e.g. "topic:EM/discrete-logic-..."
    val stage: Int,               // 1, 2 or 3 (=> +1, +4, +7)
    val dueDate: LocalDate,
    val doneDate: LocalDate? = null,
    val label: String = "",
    val subjectId: String = "",
    val topicId: String = ""
) {
    val uniqueKey get() = "$itemKey#$stage"
    fun isDone() = doneDate != null
    fun overdueDays(today: LocalDate): Long = if (isDone()) 0 else maxOf(0L, ChronoUnit.DAYS.between(dueDate, today))
}

enum class QueueKind { TOPIC_REVISION, MISTAKE }

data class QueueItem(
    val kind: QueueKind, val key: String, val label: String, val subjectId: String, val topicId: String,
    val dueDate: LocalDate, val overdueDays: Long, val priority: Int
)

object RevisionScheduler {
    val OFFSETS = listOf(1, 4, 7)

    fun scheduleFor(itemKey: String, learnDate: LocalDate, label: String, subjectId: String, topicId: String): List<RevisionEntry> =
        OFFSETS.mapIndexed { i, off -> RevisionEntry(itemKey, i + 1, learnDate.plusDays(off.toLong()), null, label, subjectId, topicId) }

    /** Idempotent: an entry whose (itemKey, stage) already exists is never duplicated or overwritten. */
    fun merge(existing: List<RevisionEntry>, incoming: List<RevisionEntry>): List<RevisionEntry> {
        val keys = existing.map { it.uniqueKey }.toMutableSet()
        val out = existing.toMutableList()
        for (e in incoming) if (keys.add(e.uniqueKey)) out += e
        return out
    }

    fun complete(entry: RevisionEntry, on: LocalDate) = if (entry.isDone()) entry else entry.copy(doneDate = on)

    /**
     * Combined study queue. Overdue and repeatedly-wrong items rank first and nothing is silently
     * discarded: items past the daily [limit] stay in [carriedOver] and remain in the next queue.
     */
    data class Queue(val today: List<QueueItem>, val carriedOver: List<QueueItem>) {
        val all get() = today + carriedOver
    }

    fun buildQueue(today: LocalDate, entries: List<RevisionEntry>, mistakes: List<MistakeRecord>, limit: Int = 12): Queue {
        val items = ArrayList<QueueItem>()
        for (e in entries) {
            if (e.isDone() || e.dueDate.isAfter(today)) continue
            val od = e.overdueDays(today)
            items += QueueItem(QueueKind.TOPIC_REVISION, e.uniqueKey, e.label, e.subjectId, e.topicId, e.dueDate, od,
                priority = (od * 10 + (4 - e.stage)).toInt())
        }
        for (m in mistakes) {
            if (m.status != MistakeStatus.ACTIVE) continue
            val due = m.nextRevision ?: continue
            if (due.isAfter(today)) continue
            val od = maxOf(0L, ChronoUnit.DAYS.between(due, today))
            items += QueueItem(QueueKind.MISTAKE, m.id, m.concept, m.subjectId, m.topicId, due, od,
                priority = (od * 10 + m.occurrences * 15 + 25).toInt())
        }
        val sorted = items.sortedWith(compareByDescending<QueueItem> { it.priority }.thenBy { it.dueDate }.thenBy { it.key })
        return Queue(sorted.take(limit), sorted.drop(limit))
    }

    /** Due items for a subject returning on the calendar ("same-subject-day" reminder). */
    fun dueForSubject(q: Queue, subjectId: String) = q.all.filter { it.subjectId == subjectId }
}
