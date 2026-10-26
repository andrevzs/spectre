package app.financas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.financas.data.FixedBill
import app.financas.data.Keys
import app.financas.domain.Dates
import app.financas.domain.Money
import app.financas.domain.litSegments
import app.financas.domain.summarizeMonth
import app.financas.ui.AppData
import app.financas.ui.AppViewModel
import app.financas.ui.Routes
import app.financas.ui.components.Card
import app.financas.ui.components.Chip
import app.financas.ui.components.Glyph
import app.financas.ui.components.Ic
import app.financas.ui.components.ListRow
import app.financas.ui.components.MoneyInput
import app.financas.ui.components.Pill
import app.financas.ui.components.SectionLabel
import app.financas.ui.components.SegBar
import app.financas.ui.components.Stepper
import app.financas.ui.components.SubHeader
import app.financas.ui.components.SwitchRow
import app.financas.ui.components.TextInput
import app.financas.ui.components.segs
import app.financas.ui.theme.C
import app.financas.ui.theme.T
import java.time.YearMonth

@Composable
fun ColumnScope.FixosScreen(vm: AppViewModel, d: AppData, nav: NavHostController) {
    val today = vm.today
    val month = YearMonth.from(today)
    val summary = remember(d, today) { summarizeMonth(month, today, d.settings, d.categories, d.entries, d.bills) }
    val bills = summary.bills.filter { it.bill.active || it.paid }
    val paid = bills.filter { it.paid }
    val pending = bills.filter { !it.paid }
    val paidCents = paid.sumOf { it.payment!!.amountCents }
    val pendingCents = pending.sumOf { it.bill.amountCents }
    val paused = d.bills.filter { !it.active }
    var editing by remember { mutableStateOf<FixedBill?>(null) }

    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SubHeader("fixos", { nav.popBackStack() }) {
            Pill("+ nova", {
                editing = FixedBill(name = "", amountCents = 0, dueDay = d.settings.payday, startEpochDay = month.atDay(1).toEpochDay())
            })
        }

        Card {
            Row(Modifier.fillMaxWidth()) {
                Text("Contas fixas de ${Dates.monthName(month)}", style = T.sans(13, color = C.Muted), modifier = Modifier.weight(1f))
                Text("${paid.size} de ${bills.size} pagas", style = T.mono(13, color = C.Muted))
            }
            SegBar(segs(40, litSegments(40, paidCents, paidCents + pendingCents), C.Text))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Pagas", style = T.sans(13, color = C.Muted))
                    Text(Money.brl(paidCents), style = T.mono(16))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("A pagar", style = T.sans(13, color = C.Muted))
                    Text(Money.brl(pendingCents), style = T.mono(16))
                }
            }
        }

        if (pending.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                SectionLabel("A pagar")
                pending.forEach { b ->
                    val bill = b.bill
                    val sub = "vence dia ${b.due.dayOfMonth} · " + (if (bill.variable) "≈ " else "") + Money.brl(bill.amountCents)
                    ListRow(bill.name, sub, onClick = { editing = bill }) {
                        if (bill.variable) {
                            Pill("informar", { nav.navigate(Routes.lancar(cat = bill.categoryId ?: -1, amount = bill.amountCents, bill = bill.id)) })
                        } else {
                            Pill("paguei", { vm.payBill(bill) })
                        }
                    }
                }
            }
        }

        if (paid.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                SectionLabel("Pagas")
                paid.forEach { b ->
                    ListRow(
                        b.bill.name,
                        "dia ${b.due.dayOfMonth}" + if (b.bill.variable) " · variável" else "",
                        onClick = { nav.navigate(Routes.lancar(edit = b.payment!!.id)) },
                        leading = { Glyph(Ic.Check, C.Muted, 16.dp) },
                        titleColor = C.Muted,
                    ) { Text(Money.brl(b.payment!!.amountCents), style = T.mono(15)) }
                }
            }
        }

        Column(Modifier.padding(horizontal = 8.dp)) {
            SectionLabel("Entradas fixas")
            ListRow("Salário", "dia ${d.settings.payday} · ${Money.brl(d.settings.salaryCents)}") {
                if (summary.salaryConfirmed) Text("confirmado", style = T.mono(13, color = C.Muted))
                else Pill("confirmar", { vm.confirmSalary() }, dot = C.Text)
            }
        }

        if (paused.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                SectionLabel("Pausadas")
                paused.forEach { b ->
                    ListRow(b.name, "dia ${b.dueDay} · ${Money.brl(b.amountCents)}", onClick = { editing = b }, titleColor = C.Muted)
                }
            }
        }
    }

    editing?.let { b ->
        BillDialog(b, d, onSave = { vm.saveBill(it); editing = null }, onDismiss = { editing = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BillDialog(bill: FixedBill, d: AppData, onSave: (FixedBill) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(bill.name) }
    var amount by remember { mutableLongStateOf(bill.amountCents) }
    var day by remember { mutableIntStateOf(bill.dueDay) }
    var variable by remember { mutableStateOf(bill.variable) }
    var categoryId by remember { mutableStateOf(bill.categoryId) }
    var active by remember { mutableStateOf(bill.active) }
    val isNew = bill.id == 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Surface,
        title = { Text(if (isNew) "Nova conta fixa" else "Conta fixa", style = T.sans(17, 500)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TextInput(name, { name = it }, "nome, ex.: internet", label = "Nome")
                MoneyInput(
                    amount.takeIf { it > 0 }, { amount = it },
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(C.Raised).padding(horizontal = 16.dp),
                    style = T.mono(16), align = TextAlign.Start, label = "Valor por mês", commitOnChange = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vence", style = T.sans(15), modifier = Modifier.weight(1f))
                    Stepper("dia $day", { day = if (day <= 1) 31 else day - 1 }, { day = if (day >= 31) 1 else day + 1 }, "Dia anterior", "Dia seguinte", 64.dp)
                }
                SwitchRow("Valor varia", "Luz, água: você confirma o valor do mês.", variable, { variable = !variable })
                SectionLabel("Área")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.categories.filter { !it.archived }.sortedBy { Keys.GROUPS.indexOf(it.groupKey) }.forEach { c ->
                        Chip(c.name, c.id == categoryId, { categoryId = c.id }, dot = C.group(c.groupKey))
                    }
                }
                if (!isNew) SwitchRow("Ativa", "Pausada some de \"Para fazer\".", active, { active = !active })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && amount >= 0) {
                    onSave(bill.copy(name = name.trim(), amountCents = amount, dueDay = day, variable = variable, categoryId = categoryId, active = active))
                }
            }) { Text("salvar", style = T.mono(14)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("cancelar", style = T.mono(14, color = C.Muted)) } },
    )
}
