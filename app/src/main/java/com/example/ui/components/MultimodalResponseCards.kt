package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.ResponseBlock
import com.example.domain.model.SearchResultItem
import com.example.ui.theme.LocalMousomiColors

@Composable
fun ResponseBlockRenderer(
  block: ResponseBlock,
  modifier: Modifier = Modifier,
) {
  when (block) {
    is ResponseBlock.MarkdownText ->
      MarkdownMessageContent(
        markdown = block.content,
        textColor = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
      )
    is ResponseBlock.ChartBlock ->
      InteractiveChartCard(chart = block.chart, modifier = modifier)
    is ResponseBlock.VideoAnalysisBlock ->
      VideoAnalysisBlockCard(block = block, modifier = modifier)
    is ResponseBlock.AudioTranscriptBlock ->
      AudioTranscriptBlockCard(block = block, modifier = modifier)
    is ResponseBlock.DocumentSummaryBlock ->
      DocumentSummaryBlockCard(block = block, modifier = modifier)
    is ResponseBlock.DataAnalyticsBlock ->
      DataAnalyticsBlockCard(block = block, modifier = modifier)
    is ResponseBlock.ResearchReportBlock ->
      ResearchReportBlockCard(block = block, modifier = modifier)
    is ResponseBlock.SourceCitations ->
      SourceCitationsBlockCard(sources = block.sources, modifier = modifier)
    is ResponseBlock.ProgressNote ->
      Text(
        text = block.label,
        color = LocalMousomiColors.current.mutedText,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier,
      )
  }
}

@Composable
private fun VideoAnalysisBlockCard(
  block: ResponseBlock.VideoAnalysisBlock,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  val res = block.analysis
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colors.activeChipContainer.copy(alpha = 0.55f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    modifier = modifier.fillMaxWidth().testTag("video_analysis_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text("Video Analytics • ${res.topic}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
      }
      Text(
        text = "${res.metadata.displayName} • ${res.metadata.width}x${res.metadata.height} • Sampled ${res.sampledFrameCount} frames",
        color = colors.mutedText,
        style = MaterialTheme.typography.labelSmall,
      )
      if (res.timeline.isNotEmpty()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Text("Timeline Breakdown", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        res.timeline.forEach { seg ->
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = "${seg.startTimeFormatted} – ${seg.endTimeFormatted} • ${seg.sceneDescription}",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
            )
            Text(
              text = seg.visualEvents,
              color = colors.mutedText,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
      if (res.keyObservations.isNotEmpty()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Text("Key Observations", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        res.keyObservations.forEach { obs ->
          Text("• $obs", style = MaterialTheme.typography.bodySmall)
        }
      }
      Text(res.analysisMethodNote, color = colors.mutedText, style = MaterialTheme.typography.labelSmall)
    }
  }
}

@Composable
private fun AudioTranscriptBlockCard(
  block: ResponseBlock.AudioTranscriptBlock,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  val res = block.analysis
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colors.activeChipContainer.copy(alpha = 0.55f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    modifier = modifier.fillMaxWidth().testTag("audio_transcript_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text("Audio Intelligence • ${res.detectedLanguage}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
      }
      Text("${res.metadata.displayName} • ${res.transcriptionEngine}", color = colors.mutedText, style = MaterialTheme.typography.labelSmall)
      if (res.keyPoints.isNotEmpty()) {
        Text("Key Points", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        res.keyPoints.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
      }
      if (res.segments.isNotEmpty()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Text("Timestamped Transcript", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        res.segments.take(12).forEach { seg ->
          Text(
            text = "[${seg.startTimeFormatted}–${seg.endTimeFormatted}] ${seg.text}",
            style = MaterialTheme.typography.bodySmall,
          )
        }
      } else if (res.fullTranscript.isNotBlank()) {
        Text(res.fullTranscript.take(900), style = MaterialTheme.typography.bodySmall)
      }
      res.translatedText?.let { tr ->
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Text("Translation: $tr", style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

@Composable
private fun DocumentSummaryBlockCard(
  block: ResponseBlock.DocumentSummaryBlock,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colors.activeChipContainer.copy(alpha = 0.5f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    modifier = modifier.fillMaxWidth().testTag("document_summary_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text("${block.documentName} (${block.formatLabel})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
      }
      Text("${block.pageCount} page/sheet(s) extracted • Document-derived facts", color = colors.mutedText, style = MaterialTheme.typography.labelSmall)
      if (block.warnings.isNotEmpty()) {
        block.warnings.forEach { w -> Text("Note: $w", color = colors.mutedText, style = MaterialTheme.typography.labelSmall) }
      }
      if (block.tables.isNotEmpty()) {
        Text("Detected ${block.tables.size} table(s)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
      }
    }
  }
}

@Composable
private fun DataAnalyticsBlockCard(
  block: ResponseBlock.DataAnalyticsBlock,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  val a = block.analytics
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colors.activeChipContainer.copy(alpha = 0.55f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    modifier = modifier.fillMaxWidth().testTag("data_analytics_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text("Data Analytics • ${a.datasetName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
      }
      Text(
        text = "Rows: ${a.cleaningSummary.originalRowCount} -> ${a.cleaningSummary.cleanedRowCount} cleaned (${a.cleaningSummary.duplicateRowsRemoved} duplicates removed)",
        color = colors.mutedText,
        style = MaterialTheme.typography.labelSmall,
      )
      a.numericStatistics.forEach { st ->
        Text(
          text = "${st.columnName}: mean=${"%.2f".format(st.mean)}, median=${"%.2f".format(st.median)}, min=${"%.2f".format(st.min)}, max=${"%.2f".format(st.max)}, σ=${"%.2f".format(st.stdDev)}",
          style = MaterialTheme.typography.bodySmall,
        )
      }
      a.detectedTrends.forEach { tr -> Text("• $tr", style = MaterialTheme.typography.bodySmall) }
      a.correlationNotes.forEach { cor -> Text("• $cor", style = MaterialTheme.typography.bodySmall) }
    }
  }
}

@Composable
private fun ResearchReportBlockCard(
  block: ResponseBlock.ResearchReportBlock,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  val r = block.report
  var expanded by remember { mutableStateOf(false) }
  @Suppress("DEPRECATION")
  val clipboard = LocalClipboardManager.current

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colors.activeChipContainer.copy(alpha = 0.55f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    modifier = modifier.fillMaxWidth().testTag("research_report_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
          Icon(Icons.Outlined.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
          Text("Deep Research Report", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        IconButton(
          onClick = { clipboard.setText(AnnotatedString(r.toMarkdownExport())) },
          modifier = Modifier.size(32.dp),
        ) {
          Icon(Icons.Outlined.ContentCopy, contentDescription = "Export Markdown Report", modifier = Modifier.size(16.dp))
        }
      }

      Text("${r.complexity} • ${r.references.size} verified sources", color = colors.mutedText, style = MaterialTheme.typography.labelSmall)
      Text("Key Findings", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
      r.keyFindings.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }

      if (expanded) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Text("Research Objectives", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        r.researchObjectives.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        Text("Methodology", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        Text(r.methodology, style = MaterialTheme.typography.bodySmall)
        Text("Detailed Analysis", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        Text(r.detailedAnalysis, style = MaterialTheme.typography.bodySmall)
        Text("Source Comparison", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        r.sourceComparison.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        Text("Conflicting Information", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        if (r.conflictingInformation.isEmpty()) {
          Text("No direct factual conflicts identified.", style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
        } else {
          r.conflictingInformation.forEach { c ->
            Text("• ${c.topic}: ${c.sourceA} vs ${c.sourceB}", style = MaterialTheme.typography.bodySmall)
          }
        }
        Text("Limitations", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        r.limitations.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        Text("Conclusion", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        Text(r.conclusion, style = MaterialTheme.typography.bodySmall)
      }

      Text(
        text = if (expanded) "Collapse full 10-section report" else "View full 10-section report",
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp),
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceCitationsBlockCard(
  sources: List<SearchResultItem>,
  modifier: Modifier = Modifier,
) {
  if (sources.isEmpty()) return
  val uriHandler = LocalUriHandler.current
  val colors = LocalMousomiColors.current

  Column(
    modifier = modifier.fillMaxWidth().testTag("source_citations_list"),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      text = "Verified Sources (${sources.size})",
      color = colors.mutedText,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Medium,
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      sources.take(8).forEach { src ->
        Surface(
          onClick = { runCatching { uriHandler.openUri(src.url) } },
          shape = RoundedCornerShape(12.dp),
          color = colors.activeChipContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = src.title.take(28),
              style = MaterialTheme.typography.labelSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Icon(
              imageVector = Icons.Outlined.OpenInNew,
              contentDescription = "Open source ${src.url}",
              modifier = Modifier.size(13.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }
  }
}
