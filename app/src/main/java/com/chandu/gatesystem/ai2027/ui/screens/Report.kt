package com.chandu.gatesystem.ai2027.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chandu.gatesystem.ai2027.data.repo.Graded
import com.chandu.gatesystem.ai2027.domain.*
import com.chandu.gatesystem.ai2027.ui.components.*
import com.chandu.gatesystem.ai2027.ui.theme.Hunter

fun fmtTime(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
fun pct(d: Double) = "${Math.round(d * 100)}%"

@Composable
fun GroupTable(title: String, rows: List<GroupStat>, label: (String) -> String = { it }) {
    if (rows.isEmpty()) return
    SystemPanel(title) {
        rows.forEach { g ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label(g.key), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Hunter.Text)
                Text("${g.correct}/${g.attempted}/${g.total}", style = MaterialTheme.typography.bodySmall, color = Hunter.TextDim)
                Text(if (g.attempted == 0) "—" else pct(g.accuracy), style = MaterialTheme.typography.bodySmall, color = if (g.attempted > 0 && g.accuracy < 0.5) Hunter.Red else Hunter.Green)
            }
        }
        Dim("correct / attempted / total")
    }
}

/** Shared result body used by the Preparation/Revision final report and the Exam/Mock results. */
@Composable
fun ReportBody(
    report: SetReport, explanations: Map<String, Explanation>, aiText: Map<String, String>, aiBusy: String?,
    onAskAi: (Graded) -> Unit, onReport: (String) -> Unit, topicTitle: (String) -> String
) {
    SystemPanel("Score") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Net marks", "${report.netMarks}/${report.maxMarks.toInt()}", Modifier.weight(1f), Hunter.Gold)
            StatTile("Percent", "%.1f%%".format(report.percent), Modifier.weight(1f), Hunter.Cyan)
            StatTile("Accuracy", pct(report.accuracy), Modifier.weight(1f), Hunter.Green)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Correct", "${report.correct}", Modifier.weight(1f), Hunter.Green); StatTile("Wrong", "${report.incorrect}", Modifier.weight(1f), Hunter.Red)
            StatTile("Unanswered", "${report.unanswered}", Modifier.weight(1f), Hunter.Orange)
        }
        KeyValue("Total / attempted", "${report.total} / ${report.attempted}")
        KeyValue("Positive marks", "+${report.positiveMarks}", Hunter.Green); KeyValue("Negative marks", "−${report.negativeMarks}", Hunter.Red)
        KeyValue("Time recorded", fmtTime(report.timeSpentSec))
    }
    GroupTable("By subject", report.bySubject) { Subjects.get(it).name }
    GroupTable("By topic", report.byTopic, topicTitle)
    GroupTable("By difficulty", report.byDifficulty) { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
    val review = report.outcomes.filter { it.question.id in report.needsReview }
    if (review.isNotEmpty()) {
        SectionTitle("Questions to review (${review.size})")
        review.forEachIndexed { i, o ->
            val g = Graded(o.question, o.response, o.result, explanations[o.question.id] ?: ExplanationBuilder.local(o.question, o.response), o.timeSpentSec)
            val n = report.outcomes.indexOf(o) + 1
            GradedCard(n, g, aiText[o.question.id], aiBusy == o.question.id, { onAskAi(g) }, { onReport(o.question.id) })
        }
    } else Note("Every question was answered correctly.", NoteKind.OK)
}
