package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalMousomiColors

private sealed interface MarkdownSegment {
  data class Prose(val text: String) : MarkdownSegment

  data class CodeBlock(val language: String, val code: String) : MarkdownSegment
}

@Composable
fun MarkdownMessageContent(
  markdown: String,
  textColor: Color,
  modifier: Modifier = Modifier,
) {
  val segments = remember(markdown) { splitMarkdownSegments(markdown) }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    segments.forEach { segment ->
      when (segment) {
        is MarkdownSegment.Prose -> {
          val styled = remember(segment.text, textColor) { renderInlineMarkdown(segment.text, textColor) }
          Text(
            text = styled,
            color = textColor,
            style = MaterialTheme.typography.bodyLarge,
          )
        }
        is MarkdownSegment.CodeBlock -> {
          SyntaxHighlightedCodeCard(
            language = segment.language,
            code = segment.code,
          )
        }
      }
    }
  }
}

@Composable
private fun SyntaxHighlightedCodeCard(
  language: String,
  code: String,
) {
  val colors = LocalMousomiColors.current
  @Suppress("DEPRECATION")
  val clipboard = LocalClipboardManager.current
  val cardBg = if (colors.isDark) Color(0xFF0B0E14) else Color(0xFF141821)
  val headerBg = if (colors.isDark) Color(0xFF161B26) else Color(0xFF1E2432)
  val highlightedCode = remember(code) { highlightCodeSyntax(code) }

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = cardBg,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(headerBg)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = language.ifBlank { "code" },
          color = Color(0xFF9CA3AF),
          fontFamily = FontFamily.Monospace,
          fontSize = 11.5.sp,
        )
        Row(
          modifier =
            Modifier
              .clip(RoundedCornerShape(8.dp))
              .clickable { clipboard.setText(AnnotatedString(code)) }
              .padding(horizontal = 6.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = "Copy code",
            tint = Color(0xFFCBD5E1),
            modifier = Modifier.size(13.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Copy",
            color = Color(0xFFCBD5E1),
            fontSize = 11.5.sp,
          )
        }
      }

      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(12.dp)
      ) {
        Text(
          text = highlightedCode,
          fontFamily = FontFamily.Monospace,
          fontSize = 13.sp,
          lineHeight = 19.sp,
        )
      }
    }
  }
}

private fun splitMarkdownSegments(raw: String): List<MarkdownSegment> {
  val result = mutableListOf<MarkdownSegment>()
  val lines = raw.lines()
  val proseBuffer = StringBuilder()
  val codeBuffer = StringBuilder()
  var inCode = false
  var codeLang = ""

  for (line in lines) {
    val trimmed = line.trimStart()
    if (trimmed.startsWith("```")) {
      if (!inCode) {
        if (proseBuffer.isNotEmpty()) {
          result.add(MarkdownSegment.Prose(proseBuffer.toString().trimEnd()))
          proseBuffer.clear()
        }
        inCode = true
        codeLang = trimmed.removePrefix("```").trim()
      } else {
        result.add(MarkdownSegment.CodeBlock(codeLang, codeBuffer.toString().trimEnd()))
        codeBuffer.clear()
        inCode = false
        codeLang = ""
      }
    } else if (inCode) {
      if (codeBuffer.isNotEmpty()) codeBuffer.append('\n')
      codeBuffer.append(line)
    } else {
      if (proseBuffer.isNotEmpty()) proseBuffer.append('\n')
      proseBuffer.append(line)
    }
  }

  if (inCode && codeBuffer.isNotEmpty()) {
    result.add(MarkdownSegment.CodeBlock(codeLang, codeBuffer.toString().trimEnd()))
  } else if (proseBuffer.isNotEmpty()) {
    result.add(MarkdownSegment.Prose(proseBuffer.toString().trimEnd()))
  }

  return result
}

private fun renderInlineMarkdown(text: String, baseColor: Color): AnnotatedString {
  return buildAnnotatedString {
    val lines = text.lines()
    lines.forEachIndexed { lineIdx, rawLine ->
      val line =
        when {
          rawLine.startsWith("### ") -> {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.5.sp)) {
              appendFormattedLine(rawLine.removePrefix("### "), baseColor)
            }
            ""
          }
          rawLine.startsWith("## ") -> {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp)) {
              appendFormattedLine(rawLine.removePrefix("## "), baseColor)
            }
            ""
          }
          rawLine.startsWith("# ") -> {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
              appendFormattedLine(rawLine.removePrefix("# "), baseColor)
            }
            ""
          }
          rawLine.trimStart().startsWith("- ") || rawLine.trimStart().startsWith("* ") -> {
            val indent = rawLine.takeWhile { it == ' ' }
            append("$indent• ")
            rawLine.trimStart().drop(2)
          }
          else -> rawLine
        }

      if (line.isNotEmpty()) {
        appendFormattedLine(line, baseColor)
      }
      if (lineIdx < lines.lastIndex) append('\n')
    }
  }
}

private fun AnnotatedString.Builder.appendFormattedLine(line: String, baseColor: Color) {
  var i = 0
  while (i < line.length) {
    when {
      line.startsWith("**", i) -> {
        val end = line.indexOf("**", i + 2)
        if (end != -1) {
          withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(line.substring(i + 2, end))
          }
          i = end + 2
        } else {
          append(line[i])
          i++
        }
      }
      line[i] == '`' -> {
        val end = line.indexOf('`', i + 1)
        if (end != -1) {
          withStyle(
            SpanStyle(
              fontFamily = FontFamily.Monospace,
              background = baseColor.copy(alpha = 0.12f),
              fontSize = 14.sp,
            )
          ) {
            append(" ${line.substring(i + 1, end)} ")
          }
          i = end + 1
        } else {
          append(line[i])
          i++
        }
      }
      line[i] == '*' -> {
        val end = line.indexOf('*', i + 1)
        if (end != -1) {
          withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            append(line.substring(i + 1, end))
          }
          i = end + 1
        } else {
          append(line[i])
          i++
        }
      }
      else -> {
        append(line[i])
        i++
      }
    }
  }
}

private fun highlightCodeSyntax(code: String): AnnotatedString {
  val keywords =
    setOf(
      "fun", "val", "var", "class", "object", "interface", "enum", "sealed", "data",
      "return", "if", "else", "when", "for", "while", "import", "package", "suspend",
      "override", "private", "public", "internal", "def", "async", "await", "const",
      "let", "function", "true", "false", "null", "try", "catch", "throw",
    )
  val keywordColor = Color(0xFFC084FC)
  val stringColor = Color(0xFF4ADE80)
  val commentColor = Color(0xFF6B7280)
  val numberColor = Color(0xFFFBBF24)
  val defaultColor = Color(0xFFE5E7EB)

  return buildAnnotatedString {
    val lines = code.lines()
    lines.forEachIndexed { idx, line ->
      val trimmed = line.trimStart()
      if (trimmed.startsWith("//") || trimmed.startsWith("#")) {
        withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) { append(line) }
      } else {
        var i = 0
        while (i < line.length) {
          val ch = line[i]
          when {
            ch == '"' || ch == '\'' -> {
              val quote = ch
              var end = i + 1
              while (end < line.length && line[end] != quote) {
                if (line[end] == '\\' && end + 1 < line.length) end += 2 else end++
              }
              val sliceEnd = (end + 1).coerceAtMost(line.length)
              withStyle(SpanStyle(color = stringColor)) { append(line.substring(i, sliceEnd)) }
              i = sliceEnd
            }
            ch.isLetter() || ch == '_' -> {
              var end = i + 1
              while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '_')) end++
              val word = line.substring(i, end)
              if (word in keywords) {
                withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Medium)) {
                  append(word)
                }
              } else {
                withStyle(SpanStyle(color = defaultColor)) { append(word) }
              }
              i = end
            }
            ch.isDigit() -> {
              var end = i + 1
              while (end < line.length && (line[end].isDigit() || line[end] == '.')) end++
              withStyle(SpanStyle(color = numberColor)) { append(line.substring(i, end)) }
              i = end
            }
            else -> {
              withStyle(SpanStyle(color = defaultColor)) { append(ch) }
              i++
            }
          }
        }
      }
      if (idx < lines.lastIndex) append('\n')
    }
  }
}
