package com.chandu.gatesystem.ai2027.alarms

import com.chandu.gatesystem.ai2027.AppContainer
import com.chandu.gatesystem.ai2027.data.toSpec
import com.chandu.gatesystem.ai2027.domain.*
import java.time.LocalDate

/** Notification text built from the plan and saved progress. No times or messages are invented beyond the plan. */
object AlarmContent {
    suspend fun build(c: AppContainer, id: AlarmId, today: LocalDate): Pair<String, String> {
        val pd = c.plan.focusDay(today)
        val tomorrow = c.plan.plan.forDate(today.plusDays(1).toString())
        suspend fun incomplete(): List<String> {
            val day = pd.day
            val specs = c.db.player().questsForDayOnce(day).map { it.toSpec() }
            val facts = c.progress.factsFor(day)
            return QuestEvaluator.incomplete(specs, facts, today).map { it.name }
        }
        return when (id) {
            AlarmId.WAKE -> "Day ${pd.day}: wake up" to "Today: ${pd.learnTopic} (${pd.learnSubjectLabel}). Plan wake time is 5:00 AM."
            AlarmId.WORKOUT -> "Workout window" to "${pd.workout}. Mark it Done / Partial / Skipped yourself in the app — it is never ticked automatically."
            AlarmId.FOLLOW_UP -> { val l = incomplete(); "Follow-up" to if (l.isEmpty()) "All of today's quests are cleared." else "Still open: ${l.take(5).joinToString(", ")}${if (l.size > 5) " +${l.size - 5} more" else ""}" }
            AlarmId.PREPARE_TOMORROW -> {
                val l = incomplete()
                "Prepare tomorrow" to buildString {
                    append(if (l.isEmpty()) "Today's quests are cleared. " else "Incomplete today: ${l.take(4).joinToString(", ")}. Resume them — do not double tomorrow's load. ")
                    tomorrow?.let { append("Tomorrow: ${it.learnTopic}.") }
                }
            }
            AlarmId.SLEEP -> "Sleep time" to "Plan sleep time is 9:00 PM so you can wake at 5:00 AM."
            AlarmId.DUE_REVISION -> {
                val due = c.db.mistakes().pendingRevisions().count { it.dueEpochDay <= today.toEpochDay() }
                val monsters = c.db.mistakes().activeCount()
                "Revision due" to "$due 1-4-7 reviews due and $monsters active Error Monsters."
            }
            AlarmId.SAME_SUBJECT -> "Subject day" to "Today's subject: ${pd.learnSubjectLabel}. Revisit earlier ${pd.learnSubjectLabel} topics and mistakes."
            AlarmId.MOCK -> "Mock test tomorrow" to (tomorrow?.takeIf { it.fullMock }?.let { "Day ${it.day}: ${it.mockQuestions ?: 65} questions, ${it.mockMarks ?: 100} marks, ${it.mockMinutes ?: 180} minutes. Sleep early." } ?: "Check the calendar.")
        }
    }
}
