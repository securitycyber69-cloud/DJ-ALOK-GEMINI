package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.core.image.ImageProcessor
import com.example.core.security.SecurityValidator
import com.example.core.storage.TempMediaCacheManager
import com.example.domain.model.AudioAnalysisResult
import com.example.domain.model.AudioMetadataInfo
import com.example.domain.model.AudioTranscriptSegment
import com.example.domain.model.ChartDataPoint
import com.example.domain.model.ChartSeries
import com.example.domain.model.ChartSpec
import com.example.domain.model.ChartType
import com.example.domain.model.ColumnStatistics
import com.example.domain.model.ConflictingClaim
import com.example.domain.model.DataAnalyticsResult
import com.example.domain.model.DataCleaningSummary
import com.example.domain.model.ParsedTable
import com.example.domain.model.ResearchReport
import com.example.domain.model.ResponseBlock
import com.example.domain.model.SearchResultItem
import com.example.domain.model.SourceVerificationStatus
import com.example.domain.model.VideoAnalysisResult
import com.example.domain.model.VideoMetadataInfo
import com.example.domain.model.VideoTimelineSegment
import com.example.multimodal.audio.AudioProcessor
import com.example.multimodal.document.DocumentProcessor
import com.example.multimodal.video.VideoProcessor
import com.example.ui.components.ResponseBlockRenderer
import com.example.ui.model.AiModelOption
import com.example.ui.screens.AttachmentBottomSheet
import com.example.ui.screens.ModelSelectionBottomSheet
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MousomiTheme
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Part3RobolectricTest {

  @get:Rule val composeRule = createComposeRule()

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  @Test
  fun `model selector bottom sheet displays exactly four required models with checkmark on selected`() {
    var selected: AiModelOption = AiModelOption.GEMINI_4_HYDROGEN

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        ModelSelectionBottomSheet(
          selectedModel = selected,
          onSelectModel = { selected = it },
          onDismiss = {},
        )
      }
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithText("Gemini 4 Hydrogen").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("New Base Intelligence · Advanced AI Engine").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Gemini Pro").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Advanced Reasoning · Coding · Complex Analysis").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Gemini Flash").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Fast Responses · Everyday AI · Multimodal Tasks").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Groq Reasoning").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Fast Inference · Logical Reasoning · Technical Assistance").performScrollTo().assertIsDisplayed()

    // Strictly 1 checkmark (on selected model) and NO Image Generation model
    composeRule.onAllNodesWithContentDescription("Selected").assertCountEquals(1)
    assertEquals(4, AiModelOption.entries.size)

    composeRule.onNodeWithTag("model_option_GROQ_REASONING").performScrollTo().performClick()
    assertEquals(AiModelOption.GROQ_REASONING, selected)
  }

  @Test
  fun `attachment bottom sheet exposes Photo, Multi, Video, Audio, Document, and Sample tiles`() {
    var videoClicked = false
    var audioClicked = false
    var docClicked = false

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        AttachmentBottomSheet(
          onPickFromGallery = {},
          onPickMultipleFromGallery = {},
          onPickVideo = { videoClicked = true },
          onPickAudio = { audioClicked = true },
          onPickDocuments = { docClicked = true },
          onAttachSamplePreview = {},
          onDismiss = {},
        )
      }
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("attach_video_button").assertIsDisplayed().performClick()
    composeRule.onNodeWithTag("attach_audio_button").assertIsDisplayed().performClick()
    composeRule.onNodeWithTag("attach_document_button").assertIsDisplayed().performClick()
    assertTrue(videoClicked && audioClicked && docClicked)
  }

  @Test
  fun `document processor extracts DOCX paragraphs tables and XLSX sharedStrings worksheets`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val processor =
      DocumentProcessor(
        tempMediaCacheManager = TempMediaCacheManager(context),
        imageProcessor = ImageProcessor(context),
        securityValidator = SecurityValidator(),
        json = json,
      )

    // Build minimal valid DOCX ZIP
    val docxXml =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
        <w:body>
          <w:p><w:r><w:t>Executive Overview Paragraph</w:t></w:r></w:p>
          <w:tbl>
            <w:tr><w:tc><w:p><w:r><w:t>Metric</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Value</w:t></w:r></w:p></w:tc></w:tr>
            <w:tr><w:tc><w:p><w:r><w:t>Accuracy</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>98.4%</w:t></w:r></w:p></w:tc></w:tr>
          </w:tbl>
        </w:body>
      </w:document>
      """.trimIndent()
    val docxBytes = buildZip(mapOf("word/document.xml" to docxXml.toByteArray()))
    val docxResult = processor.extractDocxFromStream(ByteArrayInputStream(docxBytes), "spec.docx")
    assertEquals("DOCX", docxResult.formatLabel)
    assertTrue(docxResult.extractedText.contains("Executive Overview Paragraph"))
    assertEquals(1, docxResult.tables.size)
    assertEquals(listOf("Metric", "Value"), docxResult.tables.first().headers)
    assertEquals(listOf("Accuracy", "98.4%"), docxResult.tables.first().rows.first())

    // Build minimal valid XLSX ZIP with sharedStrings and sheet1
    val sharedStringsXml =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
        <si><t>Month</t></si>
        <si><t>Revenue</t></si>
        <si><t>January</t></si>
        <si><t>February</t></si>
      </sst>
      """.trimIndent()
    val sheet1Xml =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
        <sheetData>
          <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
          <row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2"><v>4200</v></c></row>
          <row r="3"><c r="A3" t="s"><v>3</v></c><c r="B3"><v>5100</v></c></row>
        </sheetData>
      </worksheet>
      """.trimIndent()
    val xlsxBytes =
      buildZip(
        mapOf(
          "xl/sharedStrings.xml" to sharedStringsXml.toByteArray(),
          "xl/worksheets/sheet1.xml" to sheet1Xml.toByteArray(),
        )
      )
    val xlsxResult = processor.extractXlsxFromStream(ByteArrayInputStream(xlsxBytes), "sales.xlsx")
    assertEquals("XLSX", xlsxResult.formatLabel)
    assertEquals(1, xlsxResult.tables.size)
    assertEquals(listOf("Month", "Revenue"), xlsxResult.tables.first().headers)
    assertEquals(listOf("January", "4200"), xlsxResult.tables.first().rows[0])
    assertEquals(listOf("February", "5100"), xlsxResult.tables.first().rows[1])
  }

  @Test
  fun `document processor safely cleans temp files after CSV URI processing and enforces format validation`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cacheManager = TempMediaCacheManager(context)
    cacheManager.cleanupAllTempFiles()

    val processor =
      DocumentProcessor(
        tempMediaCacheManager = cacheManager,
        imageProcessor = ImageProcessor(context),
        securityValidator = SecurityValidator(),
        json = json,
      )

    val csvFile = File(context.cacheDir, "test_report.csv").apply {
      writeText("\"Region, City\",Sales\n\"South Asia, Dhaka\",1250\nEurope,980")
    }
    try {
      val result = processor.processDocumentUri(csvFile.toURI().toString()).getOrThrow()
      assertEquals("CSV", result.formatLabel)
      assertEquals(1, result.tables.size)
      assertEquals("South Asia, Dhaka", result.tables.first().rows[0][0])
      assertEquals("1250", result.tables.first().rows[0][1])

      // Verify temp cache folder was cleaned up after extraction
      val tempDir = File(context.cacheDir, "mousomi_media_tmp")
      assertTrue(tempDir.listFiles().isNullOrEmpty())

      // Unsupported extension rejected
      val badFile = File(context.cacheDir, "malware.exe").apply { writeText("MZ") }
      assertTrue(processor.processDocumentUri(badFile.toURI().toString()).isFailure)
      badFile.delete()
    } finally {
      csvFile.delete()
    }
  }

  @Test
  fun `video and audio processors parse timestamps, format intervals, and compute bounded sample frames`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val validator = SecurityValidator()
    val cacheManager = TempMediaCacheManager(context)
    val videoProcessor = VideoProcessor(cacheManager, ImageProcessor(context), validator)
    val audioProcessor = AudioProcessor(cacheManager, validator)

    val parsedTs = videoProcessor.parseRequestedTimestampsMs("What appears at 00:35 and 01:02:05?")
    assertEquals(listOf(35_000L, 3_725_000L), parsedTs)
    assertEquals("00:35", videoProcessor.formatTimestamp(35_000L))
    assertEquals("01:02:05", videoProcessor.formatTimestamp(3_725_000L))

    val samples = videoProcessor.computeSampleTimestamps(durationMs = 120_000L, requestedMs = listOf(35_000L), maxFrames = 6)
    assertEquals(6, samples.size)
    assertTrue(samples.contains(35_000L))
    assertTrue(samples == samples.sorted())

    assertEquals("02:05", audioProcessor.formatSeconds(125.0))
    assertFalse(SecurityValidator().validateHttpsResourceUrl("http://example.com"))
  }

  @Test
  fun `all Part 3 response blocks and interactive chart card render cleanly in Compose`() {
    val now = 1_750_000_000_000L
    val sampleSource =
      SearchResultItem(
        id = "src1",
        title = "Kotlin Docs",
        url = "https://kotlinlang.org/docs/home.html",
        publisher = "JetBrains",
        snippet = "Official Kotlin language reference.",
        retrievedTimestampMs = now,
        verificationStatus = SourceVerificationStatus.VERIFIED_HTTPS,
        sourceDomain = "kotlinlang.org",
      )

    val chartSpec =
      ChartSpec(
        title = "Quarterly Revenue",
        subtitle = "Bar Chart • 3 verified rows",
        chartType = ChartType.BAR,
        xAxisLabel = "Quarter",
        yAxisLabel = "USD",
        unit = "$",
        series =
          listOf(
            ChartSeries(
              name = "Revenue",
              points =
                listOf(
                  ChartDataPoint("Q1", 0.0, 100.0),
                  ChartDataPoint("Q2", 1.0, 220.0),
                  ChartDataPoint("Q3", 2.0, 310.0),
                ),
            )
          ),
        sourceAttribution = "Verified CSV",
      )

    val report =
      ResearchReport(
        query = "Compose Multiplatform adoption",
        complexity = "High",
        executiveSummary = "Adoption grew steadily across mobile teams.",
        researchObjectives = listOf("Assess stability", "Compare adoption metrics"),
        methodology = "Synthesized verified HTTPS docs.",
        keyFindings = listOf("Shared UI reduced boilerplate by 35%."),
        detailedAnalysis = "Detailed analysis narrative.",
        sourceComparison = listOf("JetBrains Official — Verified HTTPS"),
        conflictingInformation =
          listOf(
            ConflictingClaim(
              topic = "Survey Rate",
              claimA = "35%",
              sourceA = "Source A",
              claimB = "48%",
              sourceB = "Source B",
            )
          ),
        limitations = listOf("Public benchmark window only."),
        conclusion = "Ready for production Android & iOS workflows.",
        references = listOf(sampleSource),
        generatedAtMs = now,
      )

    val videoAnalysis =
      VideoAnalysisResult(
        metadata = VideoMetadataInfo("demo.mp4", "video/mp4", 4096L, 40_000L, 1920, 1080, 1_200_000L, true, "audio/mp4a-latm", 44100),
        analysisMethodNote = "Frame-and-Metadata Fallback Method: Sampled 6 key frame(s) from 00:40 total duration.",
        sampledFrameCount = 6,
        sampledTimestamps = listOf("00:00", "00:10", "00:20", "00:30"),
        summary = "Demonstrates dark-theme UI onboarding.",
        topic = "App Walkthrough",
        timeline = listOf(VideoTimelineSegment("00:00", "00:15", "Intro screen", "Logo fades in")),
        transcriptSummary = "Audio present; no external speech transcript generated.",
        keyObservations = listOf("High-contrast typography"),
      )

    val audioAnalysis =
      AudioAnalysisResult(
        metadata = AudioMetadataInfo("voice_note.m4a", "audio/mp4", 2048L, 12_000L, 128_000L),
        detectedLanguage = "English",
        transcriptionEngine = "Groq Whisper (whisper-large-v3-turbo)",
        summary = "Discussed Q3 launch checklist.",
        fullTranscript = "We need to finalize release notes before Friday.",
        segments = listOf(AudioTranscriptSegment("00:00", "00:06", "Speaker", "Finalize release notes.")),
        keyPoints = listOf("Finalize release notes by Friday"),
        translatedText = null,
      )

    val analytics =
      DataAnalyticsResult(
        datasetName = "metrics.csv",
        cleaningSummary = DataCleaningSummary(4, 3, 1, mapOf("Notes" to 1), listOf("Sales")),
        numericStatistics = listOf(ColumnStatistics("Sales", 3, 600.0, 200.0, 200.0, 100.0, 300.0, 81.65)),
        detectedTrends = listOf("Sales: Upward trend (+200%)"),
        correlationNotes = emptyList(),
        groupedHighlights = listOf("Region='APAC': total=600, avg=200"),
        sandboxExecutedOnBackend = false,
      )

    val blocks =
      listOf(
        ResponseBlock.ChartBlock(chartSpec),
        ResponseBlock.ResearchReportBlock(report),
        ResponseBlock.VideoAnalysisBlock(videoAnalysis),
        ResponseBlock.AudioTranscriptBlock(audioAnalysis),
        ResponseBlock.DataAnalyticsBlock(analytics),
        ResponseBlock.DocumentSummaryBlock("contract.pdf", "PDF", 2, "Clause 1...", listOf(ParsedTable("T1", listOf("A"), listOf(listOf("1")))), emptyList()),
        ResponseBlock.SourceCitations(listOf(sampleSource)),
      )

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          blocks.forEach { block -> ResponseBlockRenderer(block = block) }
        }
      }
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("interactive_chart_card").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("1x").performClick()
    composeRule.onNodeWithText("1.6x").assertIsDisplayed()

    composeRule.onNodeWithTag("research_report_card").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("View full 10-section report").performScrollTo().performClick()
    composeRule.onNodeWithText("Conflicting Information").performScrollTo().assertIsDisplayed()

    composeRule.onNodeWithTag("video_analysis_card").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("audio_transcript_card").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("data_analytics_card").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("document_summary_card").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("source_citations_list").performScrollTo().assertIsDisplayed()
  }

  private fun buildZip(entries: Map<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
      entries.forEach { (name, data) ->
        zip.putNextEntry(ZipEntry(name))
        zip.write(data)
        zip.closeEntry()
      }
    }
    return out.toByteArray()
  }
}
