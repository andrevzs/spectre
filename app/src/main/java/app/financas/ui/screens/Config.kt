package app.financas.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.financas.data.SetupInput
import app.financas.domain.Dates
import app.financas.domain.Money
import app.financas.domain.Setup
import app.financas.domain.projectReach
import app.financas.domain.splitByPercent
import app.financas.ui.AppData
import app.financas.ui.AppViewModel
import app.financas.ui.Routes
import app.financas.ui.components.Chip
import app.financas.ui.components.Glyph
import app.financas.ui.components.Ic
import app.financas.ui.components.IconButton
import app.financas.ui.components.MoneyInput
import app.financas.ui.components.PrimaryButton
import app.financas.ui.components.SectionLabel
import app.financas.ui.components.SegBar
import app.financas.ui.components.Stepper
import app.financas.ui.components.SwitchRow
import app.financas.ui.theme.C
import app.financas.ui.theme.T
import java.time.YearMonth

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.ConfigScreen(vm: AppViewModel, d: AppData, nav: NavHostController) {
    val s = d.settings
    val redo = s.onboarded
    var step by remember { mutableIntStateOf(1) }
    var salary by remember { mutableLongStateOf(s.salaryCents) }
    var payday by remember { mutableIntStateOf(s.payday) }
    var hasVar by remember { mutableStateOf(if (redo) s.hasVariable else true) }
    var toInvest by remember { mutableStateOf(s.variableToInvest) }
    val initialPreset = Setup.PRESETS.indexOfFirst { it.custos == s.pctCustos && it.reserva == s.pctReserva && it.invest == s.pctInvest }
    var preset by remember { mutableIntStateOf(if (initialPreset >= 0) initialPreset else 3) }
    var custom by remember { mutableStateOf(listOf(s.pctCustos, s.pctReserva, s.pctInvest)) }
    val checked = remember {
        mutableStateMapOf<String, Boolean>().apply {
            val existing = d.bills.map { it.name.lowercase() }.toSet()
            Setup.BILLS.forEach { put(it.name, if (redo) false else it.checked) }
            Setup.BILLS.forEach { if (it.name.lowercase() in existing) put(it.name, false) }
        }
    }
    val values = remember { mutableStateMapOf<String, Long>() }
    var months by remember { mutableIntStateOf(6) }
    var have by remember { mutableLongStateOf(s.reservaInitialCents) }

    val pcts = if (preset < 3) Setup.PRESETS[preset].let { listOf(it.custos, it.reserva, it.invest) } else custom
    val shares = splitByPercent(salary, pcts)

    fun back() { if (step > 1) step-- else if (redo) nav.popBackStack() }
    BackHandler(step > 1) { back() }

    val canGo = when (step) {
        1 -> salary > 0
        2 -> pcts.sum() == 100
        else -> true
    }

    Column(Modifier.weight(1f).fillMaxWidth().imePadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
        // Cabeçalho com progresso
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(Ic.Back, "Voltar", ::back, color = if (step > 1 || redo) C.Text else C.Track)
            Text("$step de 4", style = T.mono(13, color = C.Muted))
            Box(Modifier.weight(1f).padding(end = 8.dp)) {
                SegBar(List(4) { if (it < step) C.Text else C.Empty }, height = 4.dp, gap = 4.dp)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            when (step) {
                1 -> {
                    Title("Quanto entra por mês?", "O salário líquido, já sem impostos. Dá para mudar depois.")
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("R$", style = T.mono(22, color = C.Muted), modifier = Modifier.padding(end = 10.dp))
                        MoneyInput(
                            salary.takeIf { it > 0 }, { salary = it }, Modifier.weight(1f),
                            style = T.figure(40), placeholder = "0,00", label = "Salário líquido", commitOnChange = true, symbol = false,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("Cai na conta no dia")
                        val presets = listOf(1, 5, 10, 20)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            presets.forEach { p -> Chip("dia $p", payday == p, { payday = p }, mono = true) }
                        }
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("outro dia", style = T.sans(15, color = if (payday in presets) C.Muted else C.Text), modifier = Modifier.weight(1f))
                            Stepper("dia $payday", { payday = if (payday <= 1) 31 else payday - 1 }, { payday = if (payday >= 31) 1 else payday + 1 }, "Dia anterior", "Dia seguinte", 72.dp)
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SwitchRow("Tenho renda variável", "Música, freelas, eventos", hasVar, { hasVar = !hasVar })
                        if (hasVar) {
                            Text("Quando entrar, vai para", style = T.sans(13, color = C.Muted))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                                Chip("100% investir", toInvest, { toInvest = true }, dot = C.Invest, mono = true)
                                Chip("dividir pelo orçamento", !toInvest, { toInvest = false }, dot = C.Text, mono = true)
                            }
                        }
                    }
                }

                2 -> {
                    Title("Como dividir o salário?", "Custos do dia a dia, o que você guarda e o que investe.")
                    Setup.PRESETS.forEachIndexed { i, p ->
                        val amounts = splitByPercent(salary, listOf(p.custos, p.reserva, p.invest))
                        PresetCard(p.name, p.note, amounts.joinToString(" · ") { Money.brl(it) }, preset == i) { preset = i }
                    }
                    PresetCard("personalizar", "você define", "ajuste cada parte em passos de 5%", preset == 3) { preset = 3 }
                    if (preset == 3) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            listOf("Custos" to C.Text, "Guardar" to C.Reserva, "Investir" to C.Invest).forEachIndexed { i, (name, color) ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(6.dp).background(color, RoundedCornerShape(3.dp)))
                                    Text(name, style = T.sans(15), modifier = Modifier.padding(start = 8.dp).weight(1f))
                                    Stepper(
                                        "${custom[i]}%",
                                        { custom = custom.toMutableList().also { it[i] = (it[i] - 5).coerceAtLeast(0) } },
                                        { custom = custom.toMutableList().also { it[i] = (it[i] + 5).coerceAtMost(100) } },
                                    )
                                }
                            }
                            val t = custom.sum()
                            Text(
                                if (t == 100) "total 100%" else "total $t% · precisa somar 100%",
                                style = T.mono(13, color = if (t == 100) C.Muted else C.Alerta),
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }

                3 -> {
                    Title("Contas que se repetem", "Marque as suas. Todo mês elas aparecem para confirmar com um toque.")
                    if (redo && d.bills.isNotEmpty()) {
                        Text("As contas que você já tem continuam; marque só as novas.", style = T.sans(13, color = C.Muted))
                    }
                    Column {
                        Setup.BILLS.forEach { b ->
                            val on = checked[b.name] == true
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 64.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier.size(44.dp).clickable { checked[b.name] = !on },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(if (on) C.Text else C.Track),
                                        contentAlignment = Alignment.Center,
                                    ) { if (on) Glyph(Ic.Check, C.Bg, 16.dp, 2.4f) }
                                }
                                Column(Modifier.weight(1f).clickable { checked[b.name] = !on }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(b.name, style = T.sans(15, color = if (on) C.Text else C.Muted))
                                    Text(b.hint, style = T.sans(12, color = C.Muted))
                                }
                                if (on) {
                                    MoneyInput(
                                        values[b.name], { values[b.name] = it },
                                        Modifier.width(120.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(C.Raised).padding(horizontal = 12.dp),
                                        label = "Valor por mês de ${b.name}", commitOnChange = true,
                                    )
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
                        }
                    }
                    val total = Setup.BILLS.filter { checked[it.name] == true }.sumOf { values[it.name] ?: 0L }
                    Text(
                        "${Money.brl(total)} por mês em contas fixas, de ${Money.brl(shares[0])} dos custos",
                        style = T.mono(13, color = if (total > shares[0]) C.Alerta else C.Muted),
                    )
                }

                else -> {
                    Title("Primeira meta", "Antes de investir, uma reserva para imprevistos.")
                    val target = shares[0] * months
                    val reach = projectReach(have, target, shares[1], 1000, YearMonth.now())
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Reserva de emergência", style = T.sans(13, color = C.Muted))
                        Text(Money.brl(target), style = T.figure(36, C.Reserva))
                        Text(
                            "$months meses dos seus custos (${Money.brl(shares[0])}). Guardando ${Money.brl(shares[1])} por mês: " +
                                (reach?.let { Dates.monthShort(it) } ?: if (have >= target) "já atingida" else "sem previsão") + ".",
                            style = T.sans(13, color = C.Muted),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(3, 6, 12).forEach { m -> Chip("$m meses", months == m, { months = m }, mono = true) }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Já tenho guardado", style = T.sans(15))
                            Text("Conta para a meta desde o início", style = T.sans(12, color = C.Muted))
                        }
                        MoneyInput(have.takeIf { it > 0 }, { have = it }, Modifier.width(130.dp), label = "Já tenho guardado", commitOnChange = true)
                    }
                    Text(
                        "Depois da reserva, as metas de investimento entram em ordem na tela de Metas.",
                        style = T.sans(13, color = C.Muted),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        PrimaryButton(if (step == 4) "começar a usar" else "continuar", {
            if (step < 4) {
                step++
            } else {
                val bills = Setup.BILLS.filter { checked[it.name] == true }.map { it to (values[it.name] ?: 0L) }
                vm.completeSetup(
                    SetupInput(
                        salaryCents = salary, payday = payday, hasVariable = hasVar, variableToInvest = toInvest,
                        pctCustos = pcts[0], pctReserva = pcts[1], pctInvest = pcts[2],
                        bills = bills, reserveMonths = months, reserveHaveCents = have,
                    ),
                ) { nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } }
            }
        }, enabled = canGo)
    }
}

@Composable
private fun Title(title: String, sub: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = T.sans(26, 500))
        Text(sub, style = T.sans(15, color = C.Muted))
    }
}

@Composable
private fun PresetCard(name: String, note: String, amounts: String, on: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(if (on) Color(0xFF151514) else C.Surface)
            .border(1.dp, if (on) C.Text else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = T.mono(16, 500), modifier = Modifier.weight(1f))
            Text(note, style = T.sans(13, color = C.Muted))
        }
        Text(amounts, style = T.mono(13, color = C.Muted))
    }
}
