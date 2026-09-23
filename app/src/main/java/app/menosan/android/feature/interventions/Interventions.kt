package app.menosan.android.feature.interventions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.menosan.android.R
import app.menosan.android.core.ui.components.Pill
import app.menosan.android.core.ui.theme.MenosanTheme
import app.menosan.android.data.remote.dto.ImpactDto
import app.menosan.android.data.remote.dto.RecommendationDto
import app.menosan.android.data.remote.dto.Trend
import app.menosan.android.feature.reports.ReportCard
import app.menosan.android.feature.reports.costLabel
import app.menosan.android.feature.reports.effortLabel
import app.menosan.android.feature.reports.typeLabel

// Intervention Results (plan §10): recommendation cards with Adopt, the details sheet, and "How your changes went".
// Mockups `Prevention Recommendation (New)` and `(Adopted)`; behavior from plan §6 and contract §5.9.

/** Type, cost, and effort badges (DESIGN.md §5.6), plus "Keep it up" for a pinned recommendation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecommendationBadges(rec: RecommendationDto) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val tag = MenosanTheme.colors.pending
        val onTag = MenosanTheme.colors.onPending
        if (rec.continued) {
            Pill(stringResource(R.string.report_keep_it_up), MenosanTheme.colors.highlight, MenosanTheme.colors.onHighlight)
        }
        listOfNotNull(typeLabel(rec.type), costLabel(rec.costLevel), effortLabel(rec.effort)).forEach { Pill(it, tag, onTag) }
    }
}

/**
 * One recommendation. The Adopt button shows only when [canAdopt] (the latest report, plan I7). [pending] shows
 * progress while the request runs.
 */
@Composable
fun RecommendationCard(
    rec: RecommendationDto,
    canAdopt: Boolean,
    pending: Boolean,
    notMeasured: Boolean,
    onToggleAdopt: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    ReportCard {
        Text(rec.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text(rec.description, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
        RecommendationBadges(rec)
        rec.note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (rec.adopted) {
            Pill(
                stringResource(if (canAdopt) R.string.report_trying_this else R.string.report_tried_this),
                MenosanTheme.colors.calm,
                MenosanTheme.colors.onCalm,
                icon = Icons.Filled.Check,
            )
            if (notMeasured) {
                Text(
                    stringResource(R.string.report_not_measured),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpenDetails) {
                Text(stringResource(R.string.report_view_details))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            Spacer(Modifier.weight(1f))
            if (canAdopt) AdoptButton(adopted = rec.adopted, pending = pending, onClick = onToggleAdopt)
        }
    }
}

@Composable
private fun AdoptButton(adopted: Boolean, pending: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val content: @Composable () -> Unit = {
        if (pending) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else if (adopted) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.report_adopted), modifier = Modifier.padding(start = 6.dp))
        } else {
            Text(stringResource(R.string.report_adopt))
        }
    }
    if (adopted) {
        val description = stringResource(R.string.report_adopted_undo_description)
        OutlinedButton(
            onClick = onClick,
            enabled = !pending,
            shape = MaterialTheme.shapes.small,
            modifier = modifier.heightIn(min = 44.dp).semantics { contentDescription = description },
        ) { content() }
    } else {
        Button(
            onClick = onClick,
            enabled = !pending,
            shape = MaterialTheme.shapes.small,
            modifier = modifier.heightIn(min = 44.dp),
        ) { content() }
    }
}

/** Details sheet: What to do = description, a note for you = note, Steps = howTo (DESIGN.md §5.6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationDetailsSheet(
    rec: RecommendationDto,
    canAdopt: Boolean,
    pending: Boolean,
    onToggleAdopt: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(rec.title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            RecommendationBadges(rec)
            InfoBlock(stringResource(R.string.report_what_to_do), rec.description, MenosanTheme.colors.calm, MenosanTheme.colors.onCalm)
            rec.note?.let { InfoBlock(stringResource(R.string.report_note_for_you), it, MenosanTheme.colors.mist, MaterialTheme.colorScheme.onSurface) }
            if (rec.howTo.isNotEmpty()) {
                Text(stringResource(R.string.report_steps), style = MaterialTheme.typography.titleSmall)
                rec.howTo.forEach { step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.report_close)) }
                if (canAdopt) {
                    Button(
                        onClick = onToggleAdopt,
                        enabled = !pending,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        if (pending) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(if (rec.adopted) R.string.report_stop_trying else R.string.report_adopt))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoBlock(title: String, body: String, background: Color, content: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(background, MaterialTheme.shapes.small)
            .padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = content)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = content)
    }
}

/**
 * "How your changes went" (plan §5.4): each intervention adopted on the previous report, baseline → follow-up pieces,
 * with supportive copy. An increase is framed kindly ("That's okay — try a smaller step this week").
 */
@Composable
fun ImpactCard(impact: ImpactDto, targetLabel: String) {
    val (label, body, icon) = when (impact.result) {
        Trend.DECREASED -> Triple(R.string.report_impact_decreased, R.string.report_impact_decreased_body, Icons.AutoMirrored.Filled.TrendingDown)
        Trend.INCREASED -> Triple(R.string.report_impact_increased, R.string.report_impact_increased_body, Icons.AutoMirrored.Filled.TrendingUp)
        Trend.SAME, Trend.UNKNOWN -> Triple(R.string.report_impact_same, R.string.report_impact_same_body, Icons.AutoMirrored.Filled.TrendingFlat)
    }
    val (pillColor, onPill) = when (impact.result) {
        Trend.DECREASED -> MenosanTheme.colors.calm to MenosanTheme.colors.onCalm
        Trend.INCREASED -> MenosanTheme.colors.pending to MenosanTheme.colors.onPending
        else -> MenosanTheme.colors.mist to MaterialTheme.colorScheme.onSurface
    }
    ReportCard {
        Text(impact.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text(
            stringResource(R.string.report_impact_target, targetLabel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.report_impact_quantities, impact.baselineQuantity, impact.followupQuantity),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Pill(stringResource(label), pillColor, onPill, icon = icon)
        }
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
    }
}
