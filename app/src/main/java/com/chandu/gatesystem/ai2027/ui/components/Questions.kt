package com.chandu.gatesystem.ai2027.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chandu.gatesystem.ai2027.data.repo.Graded
import com.chandu.gatesystem.ai2027.domain.*
import com.chandu.gatesystem.ai2027.ui.theme.Hunter

fun marksText(q: QuestionView): String = buildString {
    append("+${q.marks}")
    when (q.type) {
        QType.MCQ -> append("  −${q.marks}/3 if wrong")
        QType.MSQ -> append("  no negative · all correct options needed")
        QType.NAT -> append("  no negative")
    }
}

@Composable
fun SourceChip(source: QSource) {
    val (txt, col) = when (source) {
        QSource.AI_GENERATED -> "AI-generated" to Hunter.Violet
        QSource.VERIFIED_PYQ -> "Verified PYQ" to Hunter.Gold
        QSource.BANK_ORIGINAL -> "Original" to Hunter.Cyan
        QSource.TEMPLATE_GENERATED -> "Original · computed key" to Hunter.Cyan
    }
    Chip(txt, col)
}

/**
 * The only question UI shown before submission. It receives a [QuestionView], which structurally contains no
 * answer key, solution or explanations.
 */
@Composable
fun QuestionBlock(
    number: Int, q: QuestionView, response: Response?, natText: String,
    onSelect: (Set<Int>) -> Unit, onNat: (String) -> Unit, enabled: Boolean = true,
    footer: @Composable ColumnScope.() -> Unit = {}
) {
    SystemPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("Q$number", Hunter.Gold); Chip(q.type.name, Hunter.Cyan); SourceChip(q.source)
        }
        Dim(marksText(q) + "  ·  ${q.difficulty.name.lowercase()}")
        Text(q.statement, style = MaterialTheme.typography.bodyLarge, color = Hunter.Text)
        when (q.type) {
            QType.MCQ -> q.options.forEachIndexed { i, opt ->
                val sel = response?.selected?.contains(i) == true
                Row(Modifier.fillMaxWidth().selectable(selected = sel, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(setOf(i)) }).padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = sel, onClick = null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = Hunter.Cyan, unselectedColor = Hunter.TextDim))
                    Spacer(Modifier.width(8.dp))
                    Text("${'A' + i}.  $opt", style = MaterialTheme.typography.bodyMedium, color = Hunter.Text)
                }
            }
            QType.MSQ -> q.options.forEachIndexed { i, opt ->
                val cur = response?.selected ?: emptySet()
                val sel = i in cur
                Row(Modifier.fillMaxWidth().selectable(selected = sel, enabled = enabled, role = Role.Checkbox, onClick = { onSelect(if (sel) cur - i else cur + i) }).padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sel, onCheckedChange = null, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = Hunter.Cyan, uncheckedColor = Hunter.TextDim))
                    Spacer(Modifier.width(8.dp))
                    Text("${'A' + i}.  $opt", style = MaterialTheme.typography.bodyMedium, color = Hunter.Text)
                }
            }
            QType.NAT -> OutlinedTextField(
                value = natText, onValueChange = onNat, enabled = enabled, singleLine = true,
                label = { Text("Numerical answer (e.g. 12, -3.5, 1/2)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                isError = natText.isNotBlank() && NatInput.parse(natText) == null,
                supportingText = { if (natText.isNotBlank() && NatInput.parse(natText) == null) Text("Not a valid number") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        footer()
    }
}

/** Result card after grading: verdict, key, and the seven-part explanation. The score shown is final; AI text is advisory only. */
@Composable
fun GradedCard(
    number: Int, g: Graded, aiText: String?, aiBusy: Boolean, onAskAi: (() -> Unit)?, onReport: (() -> Unit)?
) {
    val q = g.question
    val (label, col) = when (g.result.verdict) {
        Verdict.CORRECT -> "Correct" to Hunter.Green; Verdict.WRONG -> "Wrong" to Hunter.Red; Verdict.UNANSWERED -> "Unanswered" to Hunter.Orange
    }
    SystemPanel(accent = col) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("Q$number", Hunter.Gold); Chip(label, col); Chip("${if (g.result.marks >= 0) "+" else ""}${Scorer.r(g.result.marks)} marks", col); SourceChip(q.source)
        }
        Text(q.statement, style = MaterialTheme.typography.bodyMedium, color = Hunter.Text)
        if (q.type != QType.NAT) q.options.forEachIndexed { i, opt ->
            val right = i in q.correctOptions; val chosen = g.response?.selected?.contains(i) == true
            val c = when { right -> Hunter.Green; chosen -> Hunter.Red; else -> Hunter.TextDim }
            Text("${if (right) "✔" else if (chosen) "✘" else "·"} ${'A' + i}. $opt", color = c, style = MaterialTheme.typography.bodyMedium)
            q.optionExplanations.getOrNull(i)?.takeIf { it.isNotBlank() && (right || chosen) }?.let { Dim("   $it") }
        }
        HorizontalDivider(color = Hunter.Line)
        val e = g.explanation
        ExplainRow("1 · Your answer", e.selected); ExplainRow("2 · Correct answer", e.correct); ExplainRow("3 · Why it was wrong", e.whyWrong)
        ExplainRow("4 · Concept tested", e.concept); ExplainRow("5 · Step-by-step solution", e.steps)
        e.shortcut?.let { ExplainRow("6 · Shortcut", it) }; ExplainRow("7 · Practise next", e.practiceNext)
        if (aiText != null) { HorizontalDivider(color = Hunter.Violet); Text("AI TEACHER (advisory — does not change your score)", style = MaterialTheme.typography.labelMedium, color = Hunter.Violet); Body(aiText) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onAskAi != null && g.result.verdict != Verdict.CORRECT) HunterButton(if (aiBusy) "Asking…" else "Ask AI Teacher", onAskAi, enabled = !aiBusy, color = Hunter.Violet, outlined = true)
            if (onReport != null) HunterButton("Report ambiguous", onReport, color = Hunter.Orange, outlined = true)
        }
    }
}

@Composable
private fun ExplainRow(title: String, text: String) {
    if (text.isBlank()) return
    Column { Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Hunter.Cyan); Body(text) }
}

fun verdictColor(v: Verdict): Color = when (v) { Verdict.CORRECT -> Hunter.Green; Verdict.WRONG -> Hunter.Red; Verdict.UNANSWERED -> Hunter.Orange }
