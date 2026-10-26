@file:OptIn(ExperimentalTextApi::class)

package app.financas.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.financas.R
import app.financas.data.Keys

/** Preto, branco e cor só onde tem significado. */
object C {
    val Bg = Color(0xFF000000)
    val Surface = Color(0xFF0F0F0F)
    val Raised = Color(0xFF1C1C1A)
    val Track = Color(0xFF2A2A28)
    val Empty = Color(0xFF262624)
    val Line = Color(0xFF1F1F1D)
    val Text = Color(0xFFF2F2EE)
    val Muted = Color(0xFF8C8C87)
    val Dim = Color(0xFF5C5C58)
    val Pending = Color(0xFF6B6B67)
    val Reserva = Color(0xFFF5B83D)
    val ReservaDim = Color(0xFF7A6230)
    val Invest = Color(0xFF34D399)
    val InvestDim = Color(0xFF1F7A5B)
    val Alerta = Color(0xFFFF5A4E)

    fun group(key: String): Color = when (key) {
        Keys.RESERVA -> Reserva
        Keys.INVEST -> Invest
        else -> Text
    }

    fun groupDim(key: String): Color = when (key) {
        Keys.RESERVA -> ReservaDim
        Keys.INVEST -> InvestDim
        else -> Pending
    }
}

private fun variable(res: Int, w: Int) =
    Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Sans = FontFamily(variable(R.font.geist, 400), variable(R.font.geist, 500), variable(R.font.geist, 600))
val Mono = FontFamily(variable(R.font.geist_mono, 400), variable(R.font.geist_mono, 500), variable(R.font.geist_mono, 600))

object T {
    fun sans(size: Int, weight: Int = 400, color: Color = C.Text) =
        TextStyle(fontFamily = Sans, fontSize = size.sp, fontWeight = FontWeight(weight), color = color)

    fun mono(size: Int, weight: Int = 400, color: Color = C.Text, tracking: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = Mono, fontSize = size.sp, fontWeight = FontWeight(weight), color = color, letterSpacing = tracking)

    /** Números grandes: mono, peso 500, um pouco mais apertado. */
    fun figure(size: Int, color: Color = C.Text) =
        TextStyle(fontFamily = Mono, fontSize = size.sp, fontWeight = FontWeight(500), color = color, letterSpacing = (-0.03).em, lineHeight = (size * 1.05).sp)

    val label = sans(13, color = C.Muted)
}

@Composable
fun FinancasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Text,
            onPrimary = C.Bg,
            secondary = C.Invest,
            background = C.Bg,
            onBackground = C.Text,
            surface = C.Surface,
            onSurface = C.Text,
            surfaceVariant = C.Raised,
            onSurfaceVariant = C.Muted,
            surfaceContainerHigh = C.Surface,
            surfaceContainer = C.Surface,
            error = C.Alerta,
            outline = C.Track,
        ),
        content = content,
    )
}
