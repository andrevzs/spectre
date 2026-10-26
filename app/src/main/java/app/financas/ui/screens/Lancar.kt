package app.financas.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.financas.data.Entry
import app.financas.data.EntryDraft
import app.financas.data.Keys
import app.financas.domain.Dates
import app.financas.domain.Money
import app.financas.domain.areasByUsage
import app.financas.domain.repayDebts
import app.financas.domain.summarizeMonth
import app.financas.ui.AppData
import app.financas.ui.AppViewModel
import app.financas.ui.Routes
import app.financas.ui.components.CheckRow
import app.financas.ui.components.Chip
import app.financas.ui.components.IconButton
import app.financas.ui.components.Ic
import app.financas.ui.components.Keypad
import app.financas.ui.components.PrimaryButton
import app.financas.ui.components.SectionLabel
import app.financas.ui.components.Stepper
import app.financas.ui.components.SwitchRow
import app.financas.ui.components.TextInput
import app.financas.ui.theme.C
import app.financas.ui.theme.T
import java.time.LocalDate
import java.time.YearMonth

data class LancarArgs(
    val editId: Long?,
    val categoryId: Long?,
    val amountCents: Long?,
    val billId: Long?,
    val kind: String,
    /** Veio de "repor" em Para fazer: já marca como reposição. */
    val repay: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.LancarScreen(vm: AppViewModel, d: AppData, nav: NavHostController, args: LancarArgs) {
    val today = vm.today
    val editing: Entry? = remember { args.editId?.let { id -> d.entries.firstOrNull { it.id == id } } }
    val bill = remember { args.billId?.let { id -> d.bills.firstOrNull { it.id == id } } }

    var kind by remember { mutableStateOf(editing?.kind ?: args.kind) }
    var digits by remember { mutableStateOf((editing?.amountCents ?: args.amountCents)?.toString() ?: "") }
    var categoryId by remember { mutableStateOf(editing?.categoryId ?: args.categoryId ?: bill?.categoryId) }
    var source by remember { mutableStateOf(editing?.source ?: Keys.SALARIO) }
    var description by remember { mutableStateOf(editing?.description ?: bill?.name ?: "") }
    var date by remember { mutableStateOf(editing?.let { LocalDate.ofEpochDay(it.epochDay) } ?: today) }
    var method by remember { mutableStateOf(editing?.method ?: d.settings.lastMethod) }
    var installments by remember { mutableIntStateOf(1) }
    var repeat by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var repay by remember { mutableStateOf(editing?.repay ?: args.repay) }

    val cents = Money.fromDigits(digits)
    val isGasto = kind == Keys.GASTO
    val category = d.category(categoryId)
    val isWithdrawal = !isGasto && source == Keys.RESGATE
    val ready = cents > 0 && (if (isGasto || isWithdrawal) category != null else true)
    // Pagamento de conta fixa: a área é a da conta (muda-se em Fixos), para o orçamento não divergir.
    val areaLocked = (editing?.fixedBillId != null || bill != null) && category != null

    BackHandler(sheet) { sheet = false }

    // Prévia do efeito, no mês da data escolhida e sem contar este lançamento (na edição).
    val others = remember(d, editing) { if (editing == null) d.entries else d.entries.filter { it.id != editing.id } }
    val month = YearMonth.from(date)
    val summary = remember(d, others, month, today) { summarizeMonth(month, today, d.settings, d.categories, others, d.bills) }
    // Reposição: só aparece num aporte para uma área com resgate a repor.
    val debt = remember(d, others, categoryId) {
        repayDebts(d.categories, others).firstOrNull { it.category.id == categoryId }?.outstandingCents ?: 0L
    }
    val canRepay = isGasto && category != null && category.groupKey != Keys.CUSTOS && bill == null &&
        (debt > 0 || editing?.repay == true)
    val isRepayment = canRepay && repay
    val (impact, impactColor) = if (isWithdrawal) {
        withdrawalText(cents, category, d, others, today)
    } else if (isRepayment && cents > 0) {
        val left = debt - cents
        (if (left > 0) "falta repor ${Money.brl(left)} · sai do livre para gastar" else "resgate reposto · sai do livre para gastar") to C.group(category!!.groupKey)
    } else {
        impactText(isGasto, cents, category?.let { c -> summary.groups[c.groupKey]?.areas?.firstOrNull { it.category.id == c.id } }, summary, source, d)
    }

    fun draft() = EntryDraft(
        kind = kind, amountCents = cents, categoryId = categoryId, source = source, date = date,
        description = description, method = if (isGasto) method else null,
        installments = if (method == "crédito") installments else 1,
        repeatMonthly = repeat && editing == null, fixedBillId = bill?.id,
        repay = (isWithdrawal && repay) || isRepayment,
    )

    fun submit() {
        if (!ready) return
        if (editing != null) {
            vm.updateEntry(editing, draft()) { nav.popBackStack() }
        } else {
            vm.addEntry(draft()) { id ->
                nav.navigate(Routes.lancado(id)) {
                    popUpTo(nav.currentBackStackEntry?.destination?.route ?: Routes.HOME) { inclusive = true }
                }
            }
        }
    }

    Box(Modifier.weight(1f).fillMaxWidth()) {
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Cabeçalho
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(Ic.Close, "Fechar sem salvar", { nav.popBackStack() }, bg = C.Surface, size = 48.dp)
                    Spacer(Modifier.weight(1f))
                    if (editing == null && bill == null) {
                        Row(Modifier.clip(RoundedCornerShape(24.dp)).background(C.Surface).padding(2.dp)) {
                            Segment("gasto", isGasto) { kind = Keys.GASTO }
                            Segment("receita", !isGasto) { kind = Keys.RECEITA }
                        }
                    } else if (editing != null) {
                        Text("editar lançamento", style = T.mono(14, 500), modifier = Modifier.padding(end = 12.dp))
                        Row(
                            Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(C.Surface)
                                .clickable {
                                    vm.deleteEntry(editing, editing.description.ifBlank { d.category(editing.categoryId)?.name ?: "Lançamento" })
                                    nav.popBackStack()
                                }.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { Text("excluir", style = T.mono(14, color = C.Alerta)) }
                    } else {
                        Text(bill!!.name, style = T.mono(14, 500), modifier = Modifier.padding(end = 8.dp))
                    }
                }

                // Valor
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
                        Text("R$", style = T.mono(22, color = C.Muted), modifier = Modifier.padding(end = 10.dp, bottom = 6.dp))
                        Text(Money.plain(cents), style = T.figure(52, if (cents > 0) C.Text else C.Dim), maxLines = 1)
                    }
                    Text(
                        impact, style = T.mono(13, color = impactColor), textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp),
                    )
                }

                // Área ou origem
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(
                        if (!isGasto) "Origem" else if (areaLocked) "Área · da conta fixa, muda em Fixos" else "Área",
                        Modifier.padding(start = 4.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isGasto && areaLocked) {
                            Chip(category!!.name, true, {}, dot = C.group(category.groupKey))
                        } else if (isGasto) {
                            val ordered = remember(d, today) { areasByUsage(d.categories, d.entries, today) }
                            val visible = if (showAll) ordered else ordered.take(6).let { top ->
                                if (category != null && top.none { it.id == category.id }) top + category else top
                            }
                            visible.forEach { c ->
                                Chip(c.name, c.id == categoryId, { categoryId = c.id }, dot = C.group(c.groupKey))
                            }
                            if (!showAll && ordered.size > visible.size) {
                                Chip("mais", false, { showAll = true }, idleBg = C.Surface, idleFg = C.Muted)
                            }
                        } else {
                            Chip("Salário", source == Keys.SALARIO, { source = Keys.SALARIO }, dot = C.Text)
                            if (d.settings.hasVariable || source == Keys.VARIAVEL) {
                                Chip(
                                    d.settings.variableName, source == Keys.VARIAVEL, { source = Keys.VARIAVEL },
                                    dot = if (d.settings.variableToInvest) C.Invest else C.Text,
                                )
                            }
                            Chip("Outra", source == Keys.OUTRA, { source = Keys.OUTRA }, dot = C.Text)
                            Chip("Resgate", source == Keys.RESGATE, {
                                source = Keys.RESGATE
                                if (category?.groupKey !in setOf(Keys.RESERVA, Keys.INVEST)) categoryId = null
                            }, dot = C.Reserva)
                        }
                    }
                }

                // Resgate: de onde o dinheiro saiu
                if (isWithdrawal) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("Saiu de", Modifier.padding(start = 4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            d.categories
                                .filter { !it.archived && it.groupKey != Keys.CUSTOS }
                                .sortedWith(compareBy({ Keys.GROUPS.indexOf(it.groupKey) }, { it.sort }))
                                .forEach { c -> Chip(c.name, c.id == categoryId, { categoryId = c.id }, dot = C.group(c.groupKey)) }
                        }
                        CheckRow("Repor depois", "Aparece em Para fazer até voltar tudo.", repay, { repay = !repay })
                    }
                }

                // Aporte numa área com resgate a repor
                if (canRepay) {
                    CheckRow(
                        "É reposição de resgate",
                        if (debt > 0) "falta repor ${Money.brl(debt)} · sai do livre para gastar, não conta como o guardar do mês"
                        else "sai do livre para gastar, não conta como o guardar do mês",
                        repay, { repay = !repay },
                    )
                }

                // Detalhes: um botão que resume e abre a folha
                val parts = buildList {
                    add(Dates.relative(date, today))
                    if (isGasto) add(method + if (method == "crédito") " · " + (if (installments > 1) "${installments}x" else "à vista") else "")
                    if (description.isNotBlank()) add(description.lowercase())
                    if (repeat) add("todo mês")
                }
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(C.Surface)
                        .clickable { sheet = true }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(parts.joinToString(" · "), style = T.mono(13, color = C.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("detalhes", style = T.mono(13))
                }
            }

            Spacer(Modifier.height(12.dp))
            Keypad(
                onKey = { k ->
                    digits = if (k == "⌫") digits.dropLast(1)
                    else if (digits.length + k.length <= 9) (digits + k).trimStart('0') else digits
                },
                onClear = { digits = "" },
            )
            Spacer(Modifier.height(14.dp))
            val group = category?.groupKey
            PrimaryButton(
                when {
                    editing != null -> "salvar alterações"
                    isWithdrawal -> "resgatar ${Money.brl(cents)}"
                    isRepayment -> "repor ${Money.brl(cents)}"
                    !isGasto -> "lançar receita"
                    bill != null -> "pagar ${Money.brl(cents)}"
                    group == Keys.RESERVA -> "guardar ${Money.brl(cents)}"
                    group == Keys.INVEST -> "investir ${Money.brl(cents)}"
                    else -> "lançar gasto"
                },
                ::submit, enabled = ready,
            )
        }

        if (sheet) {
            DetailsSheet(
                isGasto = isGasto, editing = editing != null, today = today,
                description = description, onDescription = { description = it },
                date = date, onDate = { date = it },
                method = method, onMethod = { method = it; if (it != "crédito") installments = 1 },
                lastMethod = d.settings.lastMethod,
                installments = installments, onInstallments = { installments = it.coerceIn(1, 24) },
                cents = cents,
                repeat = repeat, onRepeat = { repeat = !repeat },
                showRepeat = editing == null && bill == null,
                onClose = { sheet = false },
            )
        }
    }
}

@Composable
private fun Segment(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).width(84.dp).clip(RoundedCornerShape(22.dp))
            .background(if (on) C.Text else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.mono(14, color = if (on) C.Bg else C.Muted)) }
}

/** Resgate: quanto fica na reserva/investimento e para onde vai o dinheiro. */
private fun withdrawalText(
    cents: Long,
    category: app.financas.data.Category?,
    d: AppData,
    others: List<Entry>,
    today: LocalDate,
): Pair<String, Color> {
    if (category == null) return "escolha de onde saiu" to C.Muted
    if (cents <= 0) return "" to C.Muted
    val have = app.financas.domain.groupBalance(category.groupKey, d.settings, d.categories, others, today.toEpochDay())
    val left = have - cents
    val where = if (category.groupKey == Keys.RESERVA) "na reserva" else "investido"
    return if (left < 0) {
        "mais do que há $where (${Money.brl(have)})" to C.Alerta
    } else {
        "fica ${Money.brl(left)} $where · vai para custos" to C.group(category.groupKey)
    }
}

private fun impactText(
    isGasto: Boolean,
    cents: Long,
    area: app.financas.domain.AreaStatus?,
    summary: app.financas.domain.MonthSummary,
    source: String,
    d: AppData,
): Pair<String, Color> {
    if (cents <= 0) return "" to C.Muted
    if (!isGasto) {
        return when {
            source == Keys.VARIAVEL && d.settings.variableToInvest -> "100% para investir" to C.Invest
            source == Keys.SALARIO -> "dividido ${d.settings.pctCustos} · ${d.settings.pctReserva} · ${d.settings.pctInvest}" to C.Muted
            else -> "dividido pelo orçamento" to C.Muted
        }
    }
    if (area == null) return "" to C.Muted
    val name = area.category.name.lowercase()
    return when (area.category.groupKey) {
        Keys.CUSTOS -> {
            val after = area.spentCents + cents
            if (after > area.category.plannedCents) "$name passa ${Money.brl(after - area.category.plannedCents)} do plano" to C.Alerta
            else "restam ${Money.brl(area.category.plannedCents - after)} em $name" to C.Muted
        }
        Keys.RESERVA -> {
            val g = summary.reserva
            "guardado no mês: ${Money.brl(g.spentCents + cents)} / ${Money.brl(g.budgetCents)}" to C.Reserva
        }
        else -> {
            val g = summary.invest
            "investido no mês: ${Money.brl(g.spentCents + cents)} / ${Money.brl(g.budgetCents)}" to C.Invest
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsSheet(
    isGasto: Boolean,
    editing: Boolean,
    today: LocalDate,
    description: String,
    onDescription: (String) -> Unit,
    date: LocalDate,
    onDate: (LocalDate) -> Unit,
    method: String,
    onMethod: (String) -> Unit,
    lastMethod: String,
    installments: Int,
    onInstallments: (Int) -> Unit,
    cents: Long,
    repeat: Boolean,
    onRepeat: () -> Unit,
    showRepeat: Boolean,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color(0xB8000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(C.Surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.Track))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Detalhes", style = T.sans(17, 500), modifier = Modifier.weight(1f))
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(C.Text).clickable(onClick = onClose).padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("pronto", style = T.mono(14, 500, C.Bg)) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Descrição")
                TextInput(description, onDescription, "opcional, ex.: gasolina", label = "Descrição")
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Data")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("hoje", date == today, { onDate(today) }, mono = true)
                    Chip("ontem", date == today.minusDays(1), { onDate(today.minusDays(1)) }, mono = true)
                    val other = date != today && date != today.minusDays(1)
                    Chip(if (other) Dates.dayShort(date) + " " + date.year else "outra data", other, {
                        DatePickerDialog(context, { _, y, m, dd -> onDate(LocalDate.of(y, m + 1, dd)) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }, mono = true)
                }
            }

            if (isGasto) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Pagamento")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Keys.METHODS.forEach { m -> Chip(m, method == m, { onMethod(m) }, mono = true) }
                    }
                    Text("O app lembra a última forma usada: $lastMethod.", style = T.sans(12, color = C.Muted))
                }
                if (method == "crédito" && !editing) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Parcelas", style = T.sans(13, color = C.Muted))
                            Text(
                                if (installments > 1) "${installments}x de ${Money.brl(Math.round(cents.toDouble() / installments))}" else "à vista",
                                style = T.mono(13),
                            )
                        }
                        Stepper("${installments}x", { onInstallments(installments - 1) }, { onInstallments(installments + 1) }, "Menos parcelas", "Mais parcelas")
                    }
                }
            }

            if (showRepeat && isGasto) {
                SwitchRow("Repetir todo mês", "Vira uma conta fixa e aparece em \"Para fazer\".", repeat, onRepeat)
            }
        }
    }
}
