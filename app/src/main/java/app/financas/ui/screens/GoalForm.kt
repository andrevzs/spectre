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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.financas.data.Goal
import app.financas.data.Keys
import app.financas.domain.Dates
import app.financas.domain.MonthlyBasis
import app.financas.domain.Money
import app.financas.domain.groupBalance
import app.financas.domain.monthlyContribution
import app.financas.domain.projectReach
import app.financas.ui.AppData
import app.financas.ui.AppViewModel
import app.financas.ui.components.Chip
import app.financas.ui.components.Ic
import app.financas.ui.components.IconButton
import app.financas.ui.components.MoneyInput
import app.financas.ui.components.PrimaryButton
import app.financas.ui.components.SectionLabel
import app.financas.ui.components.Stepper
import app.financas.ui.components.TextInput
import app.financas.ui.theme.C
import app.financas.ui.theme.T
import java.time.YearMonth

@Composable
fun ColumnScope.GoalFormScreen(vm: AppViewModel, d: AppData, nav: NavHostController, id: Long?) {
    val existing = remember { id?.let { i -> d.goals.firstOrNull { it.id == i } } }
    val ordered = d.goals.sortedWith(compareBy({ it.position }, { it.id }))
    val today = vm.today

    var target by remember { mutableLongStateOf(existing?.targetCents ?: 0L) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var group by remember { mutableStateOf(existing?.groupKey ?: Keys.INVEST) }
    var initial by remember(group) { mutableLongStateOf(d.settings.initialOf(group)) }
    var rate by remember { mutableIntStateOf(existing?.rateBp ?: 1000) }
    var fixed by remember { mutableStateOf(existing?.monthlyOverrideCents != null) }
    var fixedValue by remember { mutableLongStateOf(existing?.monthlyOverrideCents ?: 0L) }
    var position by remember { mutableIntStateOf(existing?.let { ordered.indexOf(it) } ?: ordered.size) }
    val maxPosition = if (existing == null) ordered.size else ordered.size - 1

    val draft = Goal(
        id = existing?.id ?: 0, name = name.trim(), groupKey = group, targetCents = target, rateBp = rate,
        monthlyOverrideCents = if (fixed && fixedValue > 0) fixedValue else null, position = existing?.position ?: -1,
    )
    val (average, basis) = monthlyContribution(draft.copy(monthlyOverrideCents = null), d.settings, d.categories, d.entries, YearMonth.from(today))
    val monthly = draft.monthlyOverrideCents ?: average
    val balance = groupBalance(group, d.settings, d.categories, d.entries, today.toEpochDay()) - d.settings.initialOf(group) + initial
    val reach = projectReach(balance, target, monthly, rate, YearMonth.from(today))
    val preview = when {
        target <= 0 -> "defina o valor"
        balance >= target -> "já atingida"
        reach == null -> "sem previsão com ${Money.brl(monthly)}/mês"
        else -> "previsão ${Dates.monthShort(reach)} · ${Money.brl(monthly)}/mês"
    }
    val ready = target > 0 && name.isNotBlank()
    val color = C.group(group)

    Column(
        Modifier.weight(1f).fillMaxWidth().imePadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(Ic.Close, "Fechar sem salvar", { nav.popBackStack() }, bg = C.Surface, size = 48.dp)
                Text(
                    if (existing == null) "nova meta" else "editar meta",
                    style = T.mono(14, 500), textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                if (existing != null) {
                    Box(
                        Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(C.Surface)
                            .clickable { vm.deleteGoal(existing); nav.popBackStack() }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("excluir", style = T.mono(14, color = C.Alerta)) }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("R$", style = T.mono(22, color = C.Muted), modifier = Modifier.padding(end = 10.dp))
                    MoneyInput(
                        target.takeIf { it > 0 }, { target = it }, Modifier.weight(1f),
                        style = T.figure(40), placeholder = "0,00", label = "Valor da meta em reais", commitOnChange = true, symbol = false,
                    )
                }
                Text(preview, style = T.mono(13, color = color), textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Nome")
                TextInput(name, { name = it }, "ex.: entrada do apartamento", label = "Nome")
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("O dinheiro vai para")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("guardar", group == Keys.RESERVA, { group = Keys.RESERVA }, dot = C.Reserva, mono = true)
                    Chip("investir", group == Keys.INVEST, { group = Keys.INVEST }, dot = C.Invest, mono = true)
                }
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                FormRow("Já tenho", "além do que foi lançado no app; vale para todas as metas de ${if (group == Keys.RESERVA) "guardar" else "investir"}") {
                    MoneyInput(initial, { initial = it }, Modifier.width(140.dp), label = "Já tenho", commitOnChange = true)
                }
                FormRow("Rendimento", "ao ano, já sem imposto") {
                    Stepper(Money.percent(rate), { rate = (rate - 50).coerceAtLeast(0) }, { rate = (rate + 50).coerceAtMost(3000) }, "Menos rendimento", "Mais rendimento", 64.dp)
                }
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Aporte por mês na previsão", style = T.sans(15))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(
                            (if (basis == MonthlyBasis.ESTIMADA) "estimado " else "média ") + Money.short(average),
                            !fixed, { fixed = false }, mono = true,
                        )
                        Chip(if (fixed && fixedValue > 0) "fixo ${Money.short(fixedValue)}" else "definir valor", fixed, { fixed = true }, mono = true)
                    }
                    if (fixed) {
                        MoneyInput(
                            fixedValue.takeIf { it > 0 }, { fixedValue = it },
                            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(C.Raised).padding(horizontal = 16.dp),
                            style = T.mono(16), align = TextAlign.Start, label = "Aporte mensal", commitOnChange = true,
                        )
                    }
                }
                FormRow(
                    "Ordem",
                    when {
                        position == 0 -> "1ª · a primeira da fila"
                        else -> "${position + 1}ª · depois de ${ordered.filter { it.id != existing?.id }.getOrNull(position - 1)?.name ?: "—"}"
                    },
                ) {
                    Stepper("${position + 1}ª", { position = (position - 1).coerceAtLeast(0) }, { position = (position + 1).coerceAtMost(maxPosition) }, "Antes", "Depois", 40.dp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(if (existing == null) "criar meta" else "salvar meta", {
            if (initial != d.settings.initialOf(group)) {
                val g = group
                vm.updateSettings { s -> if (g == Keys.RESERVA) s.copy(reservaInitialCents = initial) else s.copy(investInitialCents = initial) }
            }
            vm.saveGoal(draft, position)
            nav.popBackStack()
        }, enabled = ready)
    }
}

@Composable
private fun FormRow(title: String, sub: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = T.sans(15))
            Text(sub, style = T.sans(12, color = C.Muted))
        }
        trailing()
    }
}
