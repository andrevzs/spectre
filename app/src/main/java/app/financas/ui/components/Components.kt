package app.financas.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.financas.domain.Money
import app.financas.ui.theme.C
import app.financas.ui.theme.T

// ---------- Barras segmentadas ----------

fun segs(n: Int, lit: Int, color: Color, empty: Color = C.Empty): List<Color> = List(n) { if (it < lit) color else empty }

/** Barra de segmentos cheios ou vazios. [markAt] desenha o traço de "hoje" antes do segmento. */
@Composable
fun SegBar(colors: List<Color>, modifier: Modifier = Modifier, height: Dp = 10.dp, gap: Dp = 2.dp, markAt: Int? = null) {
    val over = if (markAt != null) 5.dp else 0.dp
    Canvas(modifier.fillMaxWidth().height(height + over * 2)) {
        val n = colors.size
        if (n == 0) return@Canvas
        val g = gap.toPx()
        val w = (size.width - g * (n - 1)) / n
        val top = over.toPx()
        colors.forEachIndexed { i, c ->
            drawRect(c, Offset(i * (w + g), top), Size(w, height.toPx()))
        }
        if (markAt != null && markAt in 0..n) {
            val x = (markAt * (w + g) - g / 2).coerceIn(0f, size.width - 2.dp.toPx())
            drawRect(C.Text, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
        }
    }
}

/** Várias barras lado a lado, com espaço maior entre grupos. */
@Composable
fun GroupedSegBar(parts: List<List<Color>>, modifier: Modifier = Modifier, markPart: Int? = null, markAt: Int? = null) {
    val hasMark = markAt != null
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { i, p ->
            if (p.isNotEmpty()) {
                Box(Modifier.weight(p.size.toFloat())) {
                    SegBar(
                        p,
                        markAt = if (i == markPart) markAt else null,
                        modifier = if (hasMark && i != markPart) Modifier.padding(vertical = 5.dp) else Modifier,
                    )
                }
            }
        }
    }
}

// ---------- Ícones (desenhados, sem biblioteca) ----------

enum class Ic { Back, Forward, Close, Search, Plus, Sliders, Check, Down }

@Composable
fun Glyph(ic: Ic, color: Color = C.Muted, size: Dp = 18.dp, stroke: Float = 1.7f) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        val st = Stroke(width = stroke * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(vararg pts: Float): Path = Path().apply {
            moveTo(pts[0] * s, pts[1] * s)
            var i = 2
            while (i < pts.size) { lineTo(pts[i] * s, pts[i + 1] * s); i += 2 }
        }
        when (ic) {
            Ic.Back -> drawPath(p(15f, 6f, 9f, 12f, 15f, 18f), color, style = st)
            Ic.Forward -> drawPath(p(9f, 6f, 15f, 12f, 9f, 18f), color, style = st)
            Ic.Down -> drawPath(p(6f, 9f, 12f, 15f, 18f, 9f), color, style = st)
            Ic.Close -> { drawPath(p(6f, 6f, 18f, 18f), color, style = st); drawPath(p(18f, 6f, 6f, 18f), color, style = st) }
            Ic.Plus -> { drawPath(p(12f, 5f, 12f, 19f), color, style = st); drawPath(p(5f, 12f, 19f, 12f), color, style = st) }
            Ic.Check -> drawPath(p(5f, 12.5f, 10f, 17f, 19f, 7f), color, style = st)
            Ic.Search -> {
                drawCircle(color, radius = 6f * s, center = Offset(11f * s, 11f * s), style = st)
                drawPath(p(20f, 20f, 15.5f, 15.5f), color, style = st)
            }
            Ic.Sliders -> {
                drawPath(p(4f, 7f, 20f, 7f), color, style = st)
                drawPath(p(4f, 17f, 20f, 17f), color, style = st)
                drawCircle(C.Bg, radius = 2.6f * s, center = Offset(9f * s, 7f * s))
                drawCircle(color, radius = 2.2f * s, center = Offset(9f * s, 7f * s), style = st)
                drawCircle(C.Bg, radius = 2.6f * s, center = Offset(15f * s, 17f * s))
                drawCircle(color, radius = 2.2f * s, center = Offset(15f * s, 17f * s), style = st)
            }
        }
    }
}

@Composable
fun IconButton(ic: Ic, label: String, onClick: () -> Unit, color: Color = C.Muted, bg: Color = Color.Transparent, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { Glyph(ic, color) }
}

// ---------- Botões e chips ----------

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    mono: Boolean = false,
    idleBg: Color = C.Raised,
    idleFg: Color = C.Text,
) {
    val fg = if (selected) C.Bg else idleFg
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (selected) C.Text else idleBg)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected; role = Role.Button }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(6.dp).background(if (selected) C.Bg else dot, CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = if (mono) T.mono(14, color = fg) else T.sans(15, color = fg), maxLines = 1)
    }
}

/** Ação pequena em pílula: "confirmar", "paguei", "+ nova". */
@Composable
fun Pill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, dot: Color? = null, fg: Color = C.Text, bg: Color = C.Raised) {
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(6.dp).background(dot, CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = T.mono(14, color = fg), maxLines = 1)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
            .background(if (enabled) C.Text else C.Raised)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.mono(16, 500, if (enabled) C.Bg else C.Muted)) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, fg: Color = C.Text) {
    Box(
        modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
            .background(C.Surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.mono(16, 500, fg)) }
}

@Composable
fun Card(modifier: Modifier = Modifier, gap: Dp = 12.dp, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

@Composable
fun Toggle(on: Boolean) {
    Box(Modifier.width(52.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) C.Invest else C.Track)) {
        Box(Modifier.offset(x = if (on) 24.dp else 4.dp, y = 4.dp).size(24.dp).background(C.Text, CircleShape))
    }
}

@Composable
fun SwitchRow(title: String, sub: String?, on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(onClick = onToggle)
            .semantics { role = Role.Switch; selected = on },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = T.sans(15))
            if (sub != null) Text(sub, style = T.sans(12, color = C.Muted))
        }
        Toggle(on)
    }
}

@Composable
fun Stepper(value: String, onLess: () -> Unit, onMore: () -> Unit, lessLabel: String = "Diminuir", moreLabel: String = "Aumentar", width: Dp = 52.dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RoundKey("−", lessLabel, onLess)
        Text(value, style = T.mono(18), textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = width))
        RoundKey("+", moreLabel, onMore)
    }
}

@Composable
private fun RoundKey(text: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(C.Raised).clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.mono(20)) }
}

@Composable
fun Dot(color: Color, size: Dp = 6.dp) = Box(Modifier.size(size).background(color, CircleShape))

/** Linha de lista: título, subtítulo em mono e algo à direita. */
@Composable
fun ListRow(
    title: String,
    sub: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    titleColor: Color = C.Text,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 58.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leading != null) leading()
            Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = T.sans(15, color = titleColor), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub, style = T.mono(13, color = C.Muted), maxLines = 2)
            }
            trailing()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
    }
}

// ---------- Valor grande: inteiro claro, centavos apagados ----------

@Composable
fun BigMoney(cents: Long, size: Int, color: Color = C.Text, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(Money.intPart(cents), style = T.figure(size, color), maxLines = 1)
        Text(Money.decPart(cents), style = T.figure(size, C.Muted), maxLines = 1)
    }
}

// ---------- Teclado numérico ----------

@Composable
fun Keypad(onKey: (String) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", "⌫"))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { k ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val erase = k == "⌫"
                        Box(
                            Modifier.size(64.dp).clip(CircleShape)
                                .background(if (erase) C.Surface else C.Raised)
                                .semantics { contentDescription = if (erase) "Apagar último dígito (segure para limpar)" else k; role = Role.Button }
                                .pointerInput(k) {
                                    detectTapGestures(
                                        onTap = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onKey(k) },
                                        onLongPress = { if (erase) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClear() } },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(k, style = T.mono(if (erase) 18 else 24, color = if (erase) C.Muted else C.Text))
                        }
                    }
                }
            }
        }
    }
}

// ---------- Campo de dinheiro ----------

/**
 * Campo de texto para valores. Mostra "R$ 1.500,00" e salva quando perde o foco
 * ou no "concluído". Texto inválido volta ao valor anterior.
 */
@Composable
fun MoneyInput(
    cents: Long?,
    onCommit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = T.mono(15),
    placeholder: String = "R$ 0,00",
    align: TextAlign = TextAlign.End,
    label: String = "Valor",
    commitOnChange: Boolean = false,
    /** false: sem "R$", para quando o símbolo já aparece ao lado. */
    symbol: Boolean = true,
) {
    val fmt: (Long) -> String = { if (symbol) Money.brl(it) else Money.plain(it) }
    val shown = cents?.takeIf { it > 0 }?.let(fmt) ?: ""
    var text by remember { mutableStateOf(shown) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(shown) { if (!focused) text = shown }
    val focus = LocalFocusManager.current
    fun commit() {
        val v = if (text.isBlank()) 0L else Money.parse(text)
        if (v != null) { onCommit(v); text = if (v > 0) fmt(v) else "" } else text = shown
    }
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            if (commitOnChange) { (if (it.isBlank()) 0L else Money.parse(it))?.let(onCommit) }
        },
        singleLine = true,
        textStyle = style.copy(textAlign = align),
        cursorBrush = SolidColor(C.Text),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier
            .semantics { contentDescription = label }
            .onFocusChanged {
                if (focused && !it.isFocused) commit()
                focused = it.isFocused
            },
        decorationBox = { inner ->
            Box(contentAlignment = if (align == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart) {
                if (text.isEmpty()) Text(placeholder, style = style.copy(color = C.Dim, textAlign = align))
                inner()
            }
        },
    )
}

@Composable
fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String = placeholder,
) {
    val focus = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = T.sans(16),
        cursorBrush = SolidColor(C.Text),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(C.Raised)
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = T.sans(16, color = C.Dim))
                inner()
            }
        },
    )
}

// ---------- Navegação e avisos ----------

enum class Tab(val label: String) { INICIO("início"), HISTORICO("histórico"), METAS("metas"), ORCAMENTO("orçamento") }

@Composable
fun BottomNav(current: Tab?, onTab: (Tab) -> Unit, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(C.Bg)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        @Composable
        fun item(t: Tab) {
            val on = t == current
            Column(
                Modifier.weight(1f).height(60.dp).clickable { onTab(t) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                Box(Modifier.size(4.dp).background(if (on) C.Text else Color.Transparent, CircleShape))
                Text(t.label, style = T.mono(12, color = if (on) C.Text else C.Muted), maxLines = 1)
            }
        }
        item(Tab.INICIO)
        item(Tab.HISTORICO)
        Box(Modifier.weight(1f).height(60.dp), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier.offset(y = (-28).dp).size(76.dp).background(C.Bg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(C.Text).clickable(onClick = onAdd)
                        .semantics { contentDescription = "Novo lançamento" },
                    contentAlignment = Alignment.Center,
                ) { Glyph(Ic.Plus, C.Bg, 26.dp, 2f) }
            }
        }
        item(Tab.METAS)
        item(Tab.ORCAMENTO)
    }
}

/** Aviso temporário com "desfazer". */
@Composable
fun UndoToast(text: String, value: String?, onUndo: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp).defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(28.dp)).background(C.Raised).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.weight(1f).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = T.sans(15), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (value != null) {
                Spacer(Modifier.width(8.dp))
                Text(value, style = T.mono(13, color = C.Muted), maxLines = 1)
            }
        }
        if (onUndo != null) {
            Box(Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onUndo).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text("desfazer", style = T.mono(14).copy(textDecoration = TextDecoration.Underline))
            }
        }
    }
}

/** Cabeçalho de tela secundária: voltar, título e ação opcional à direita. */
@Composable
fun SubHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, action: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(Ic.Back, "Voltar", onBack)
        Text(title, style = T.mono(14, 500), modifier = Modifier.weight(1f))
        action()
    }
}

@Composable
fun MonthNav(label: String, onPrev: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier, weight: Int = 500) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(Ic.Back, "Mês anterior", onPrev)
        Text(label, style = T.mono(14, weight))
        IconButton(Ic.Forward, "Próximo mês", onNext)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) = Text(text, style = T.label, modifier = modifier)
