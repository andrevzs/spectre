package app.financas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.financas.data.Category
import app.financas.data.Keys
import app.financas.domain.Money
import app.financas.domain.splitByPercent
import app.financas.ui.AppData
import app.financas.ui.AppViewModel
import app.financas.ui.Routes
import app.financas.ui.components.Chip
import app.financas.ui.components.Dot
import app.financas.ui.components.Glyph
import app.financas.ui.components.Ic
import app.financas.ui.components.MoneyInput
import app.financas.ui.components.SectionLabel
import app.financas.ui.components.Stepper
import app.financas.ui.components.SwitchRow
import app.financas.ui.components.TextInput
import app.financas.ui.theme.C
import app.financas.ui.theme.T

private val groupNames = mapOf(Keys.CUSTOS to "Custos", Keys.RESERVA to "Guardar", Keys.INVEST to "Investir")

@Composable
fun ColumnScope.BudgetScreen(vm: AppViewModel, d: AppData, nav: NavHostController) {
    val s = d.settings
    val pcts = listOf(s.pctCustos, s.pctReserva, s.pctInvest)
    val shares = splitByPercent(s.salaryCents, pcts)
    val total = pcts.sum()
    var newAreaFor by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Category?>(null) }
    val activeBills = d.bills.filter { it.active }
    val billCats = activeBills.mapNotNull { it.categoryId }.toSet()

    Column(
        Modifier.weight(1f).fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("orçamento", style = T.mono(14, 500), modifier = Modifier.weight(1f))
            Row(
                Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(C.Raised)
                    .clickable { nav.navigate(Routes.AJUSTES) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Glyph(Ic.Sliders, C.Text, 16.dp)
                Text("ajustes", style = T.mono(14))
            }
        }

        // Renda
        Block("Renda") {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Salário líquido", style = T.sans(15))
                    Text("todo dia ${s.payday} · dividido abaixo", style = T.sans(12, color = C.Muted))
                }
                MoneyInput(s.salaryCents, { v -> vm.updateSettings { it.copy(salaryCents = v) } }, Modifier.width(150.dp), label = "Salário líquido")
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Dia do salário", style = T.sans(15), modifier = Modifier.weight(1f))
                Stepper(
                    "dia ${s.payday}",
                    { vm.updateSettings { it.copy(payday = (it.payday - 1).let { p -> if (p < 1) 31 else p }) } },
                    { vm.updateSettings { it.copy(payday = (it.payday + 1).let { p -> if (p > 31) 1 else p }) } },
                    "Dia anterior", "Dia seguinte", 72.dp,
                )
            }
            SwitchRow("Renda variável", s.variableName.lowercase().takeIf { s.hasVariable }, s.hasVariable, { vm.updateSettings { it.copy(hasVariable = !it.hasVariable) } })
            if (s.hasVariable) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Chip("100% investir", s.variableToInvest, { vm.updateSettings { it.copy(variableToInvest = true) } }, dot = C.Invest, mono = true)
                    Chip("dividir", !s.variableToInvest, { vm.updateSettings { it.copy(variableToInvest = false) } }, dot = C.Text, mono = true)
                }
            }
        }

        // Divisão
        Block("Divisão do salário") {
            Keys.GROUPS.forEachIndexed { i, key ->
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Dot(C.group(key))
                            Text(groupNames.getValue(key), style = T.sans(15))
                        }
                        Text("${Money.brl(shares[i])} por mês", style = T.mono(12, color = C.Muted))
                    }
                    Stepper(
                        "${pcts[i]}%",
                        { vm.updateSettings { it.withPct(key, -5) } },
                        { vm.updateSettings { it.withPct(key, 5) } },
                        "Diminuir ${groupNames[key]} em 5%", "Aumentar ${groupNames[key]} em 5%",
                    )
                }
            }
            Text(
                if (total == 100) "total 100%" else "total $total% · precisa somar 100%",
                style = T.mono(13, color = if (total == 100) C.Muted else C.Alerta),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // Contas fixas
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface)
                .clickable { nav.navigate(Routes.FIXOS) }.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Contas e entradas fixas", style = T.sans(15))
                Text(
                    "${activeBills.size} ${if (activeBills.size == 1) "conta" else "contas"} · ${Money.brl(activeBills.sumOf { it.amountCents })}/mês",
                    style = T.mono(13, color = C.Muted),
                )
            }
            Glyph(Ic.Forward)
        }

        // Áreas de cada grupo
        Keys.GROUPS.forEachIndexed { i, gk ->
            val areas = d.categories.filter { it.groupKey == gk && !it.archived }
            val rest = shares[i] - areas.sumOf { it.plannedCents }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    Dot(C.group(gk))
                    Text(groupNames.getValue(gk), style = T.sans(13, color = C.Muted))
                }
                areas.forEach { c -> key(c.id) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).heightIn(min = 44.dp).clickable { editing = c },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(c.name, style = T.sans(15), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (c.id in billCats) Tag("fixa")
                        }
                        MoneyInput(
                            c.plannedCents, { v -> vm.updateCategory(c.copy(plannedCents = v)) },
                            Modifier.width(130.dp), label = "Planejado por mês para ${c.name}",
                        )
                    }
                } }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "+ nova área", style = T.mono(14),
                        modifier = Modifier.clip(RoundedCornerShape(22.dp)).clickable { newAreaFor = gk }.padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (rest >= 0) "sem área: ${Money.brl(rest)}" else "passa ${Money.brl(-rest)}",
                        style = T.mono(13, color = if (rest >= 0) C.Muted else C.Alerta),
                    )
                }
            }
        }
    }

    newAreaFor?.let { key ->
        NameDialog(
            title = "Nova área em ${groupNames[key]}",
            initial = "",
            confirm = "criar",
            onConfirm = { name -> vm.addCategory(name, key); newAreaFor = null },
            onDismiss = { newAreaFor = null },
        )
    }
    editing?.let { c ->
        NameDialog(
            title = "Área",
            initial = c.name,
            confirm = "salvar",
            onConfirm = { name -> vm.updateCategory(c.copy(name = name)); editing = null },
            onDismiss = { editing = null },
            extra = "arquivar" to {
                vm.updateCategory(c.copy(archived = true)); editing = null
            },
            note = "Arquivar tira a área das escolhas. Os lançamentos antigos continuam no histórico.",
        )
    }
}

private fun app.financas.data.Settings.withPct(key: String, delta: Int) = when (key) {
    Keys.CUSTOS -> copy(pctCustos = (pctCustos + delta).coerceIn(0, 100))
    Keys.RESERVA -> copy(pctReserva = (pctReserva + delta).coerceIn(0, 100))
    else -> copy(pctInvest = (pctInvest + delta).coerceIn(0, 100))
}

@Composable
private fun Block(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        SectionLabel(title, Modifier.padding(vertical = 6.dp))
        content()
    }
}

@Composable
fun Tag(text: String, color: Color = C.Muted) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(C.Raised).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, style = T.mono(11, color = color))
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    extra: Pair<String, () -> Unit>? = null,
    note: String? = null,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Surface,
        title = { Text(title, style = T.sans(17, 500)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextInput(name, { name = it }, "nome", label = "Nome")
                if (note != null) Text(note, style = T.sans(12, color = C.Muted))
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) { Text(confirm, style = T.mono(14)) }
        },
        dismissButton = {
            Row {
                if (extra != null) TextButton(onClick = extra.second) { Text(extra.first, style = T.mono(14, color = C.Alerta)) }
                TextButton(onClick = onDismiss) { Text("cancelar", style = T.mono(14, color = C.Muted)) }
            }
        },
    )
}
