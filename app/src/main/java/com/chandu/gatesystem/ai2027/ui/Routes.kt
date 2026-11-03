package com.chandu.gatesystem.ai2027.ui

import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import com.chandu.gatesystem.ai2027.AppContainer

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

object Routes {
    const val INIT = "init"; const val DETECT = "detect"; const val DASH = "dashboard"; const val QUESTS = "quests"; const val CALENDAR = "calendar"
    const val SUBJECTS = "subjects"; const val SUBJECT = "subject/{id}"; fun subject(id: String) = "subject/$id"
    const val LESSON = "lesson/{topic}"; fun lesson(topic: String) = "lesson/${Uri.encode(topic)}"
    const val SETUP = "setup/{kind}/{day}"; fun setup(kind: String, day: Int) = "setup/$kind/$day"
    const val REVISION = "revision/{day}"; fun revision(day: Int) = "revision/$day"
    const val PRACTICE = "practice/{id}"; fun practice(id: String) = "practice/${Uri.encode(id)}"
    const val REPORT = "report/{id}"; fun report(id: String) = "report/${Uri.encode(id)}"
    const val EXAM_SETUP = "examsetup/{day}"; fun examSetup(day: Int) = "examsetup/$day"
    const val EXAM = "exam/{id}"; fun exam(id: String) = "exam/${Uri.encode(id)}"
    const val MOCKS = "mocks"; const val MONSTER = "monster"; const val PLAYER = "player"; const val XP = "xp"; const val WORKOUT = "workout"
    const val ALARMS = "alarms"; const val AI = "ai"; const val BANK = "bank"; const val BACKUP = "backup"; const val SETTINGS = "settings"
}
