package app.financas.domain

import app.financas.data.Category
import app.financas.data.Entry
import app.financas.data.FixedBill
import app.financas.data.Goal
import app.financas.data.Keys
import app.financas.data.Settings
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// Regras do orçamento. Funções puras: recebem tudo que precisam e não tocam no banco.

/**
 * Divide um valor entre percentuais sem perder centavos (método do maior resto).
 * Quando os percentuais somam 100, a soma das partes é exatamente o total.
 */
fun splitByPercent(totalCents: Long, percents: List<Int>): List<Long> {
    val raw = percents.map { totalCents * it } // em centésimos de centavo
    val floors = raw.map { Math.floorDiv(it, 100L) }
    val target = (raw.sum() + 50).let { Math.floorDiv(it, 100L) }
    var leftover = target - floors.sum()
    val order = raw.indices.sortedByDescending { Math.floorMod(raw[it], 100L) }
    val result = floors.toMutableList()
    for (i in order) {
        if (leftover <= 0) break
        result[i] += 1
        leftover--
    }
    return result
}

/** Parcelas iguais; os centavos que sobram vão nas primeiras. */
fun splitInstallments(totalCents: Long, count: Int): List<Long> {
    val n = max(1, count)
    val base = totalCents / n
    val rest = (totalCents % n).toInt()
    return List(n) { i -> base + if (i < rest) 1 else 0 }
}

/**
 * Quantos segmentos acendem numa barra de [n]: segmento cheio ou vazio, arredondado
 * para o mais próximo, e pelo menos um quando o valor é maior que zero.
 */
fun litSegments(n: Int, ratio: Double): Int {
    if (ratio.isNaN() || ratio <= 0.0) return 0
    return max(1, (min(ratio, 1.0) * n).roundToInt())
}

fun litSegments(n: Int, value: Long, total: Long): Int = when {
    value <= 0 -> 0
    total <= 0 -> n
    else -> litSegments(n, value.toDouble() / total)
}

/** Distribui [n] segmentos entre partes proporcionais aos pesos (maior resto). */
fun allocateSegments(n: Int, weights: List<Long>): List<Int> {
    val total = weights.sum()
    if (total <= 0) return weights.mapIndexed { i, _ -> if (i == 0) n else 0 }
    val raw = weights.map { it.toDouble() * n / total }
    val floors = raw.map { it.toInt() }.toMutableList()
    var leftover = n - floors.sum()
    val order = raw.indices.sortedByDescending { raw[it] - floors[it] }
    for (i in order) {
        if (leftover <= 0) break
        floors[i] += 1
        leftover--
    }
    // Uma parte com valor nunca fica invisível: tira um da maior.
    for (i in weights.indices) {
        if (weights[i] > 0 && floors[i] == 0) {
            val biggest = floors.indices.maxBy { floors[it] }
            if (floors[biggest] > 1) {
                floors[biggest] -= 1
                floors[i] = 1
            }
        }
    }
    return floors
}

data class AreaStatus(val category: Category, val spentCents: Long) {
    val remainingCents: Long get() = category.plannedCents - spentCents
    val isOver: Boolean get() = spentCents > category.plannedCents
}

data class GroupStatus(
    val key: String,
    /** Quanto cabe ao grupo no mês: parte do salário mais renda direta. */
    val budgetCents: Long,
    val fromSplitCents: Long,
    /** Renda variável que vai direto para investir. */
    val directCents: Long,
    val spentCents: Long,
    val areas: List<AreaStatus>,
) {
    val remainingCents: Long get() = budgetCents - spentCents
    val plannedInAreasCents: Long get() = areas.filter { !it.category.archived }.sumOf { it.category.plannedCents }
    val unassignedCents: Long get() = budgetCents - plannedInAreasCents
}

data class BillStatus(val bill: FixedBill, val due: LocalDate, val payment: Entry?) {
    val paid: Boolean get() = payment != null
}

data class MonthSummary(
    val month: YearMonth,
    val baseCents: Long,
    /** Salário ainda não confirmado: a divisão usa o salário previsto. */
    val baseIsPlanned: Boolean,
    val salaryConfirmed: Boolean,
    val incomeCents: Long,
    val groups: Map<String, GroupStatus>,
    val bills: List<BillStatus>,
    /** Contas fixas de custos ainda não pagas no mês. */
    val pendingFixedCents: Long,
    val freeCents: Long,
    /** Só no mês atual ou futuro: dias que restam contando hoje. */
    val daysLeft: Int?,
    /** Só no mês atual: fração do mês que já passou (hoje incluso). */
    val monthFraction: Double?,
) {
    fun group(key: String): GroupStatus = groups.getValue(key)
    val custos: GroupStatus get() = group(Keys.CUSTOS)
    val reserva: GroupStatus get() = group(Keys.RESERVA)
    val invest: GroupStatus get() = group(Keys.INVEST)
    val perDayCents: Long? get() = daysLeft?.let { if (freeCents > 0) freeCents / it else 0 }
    val areasOver: List<AreaStatus> get() = custos.areas.filter { it.isOver }
}

fun entriesIn(entries: List<Entry>, month: YearMonth): List<Entry> {
    val from = month.atDay(1).toEpochDay()
    val to = month.atEndOfMonth().toEpochDay()
    return entries.filter { it.epochDay in from..to }
}

/** Para onde vai uma receita: divisão pelo orçamento ou direto para investir. */
fun goesToInvest(entry: Entry, settings: Settings): Boolean =
    entry.kind == Keys.RECEITA && entry.source == Keys.VARIAVEL && settings.variableToInvest

fun summarizeMonth(
    month: YearMonth,
    today: LocalDate,
    settings: Settings,
    categories: List<Category>,
    allEntries: List<Entry>,
    bills: List<FixedBill>,
): MonthSummary {
    val entries = entriesIn(allEntries, month)
    val receitas = entries.filter { it.kind == Keys.RECEITA }
    val gastos = entries.filter { it.kind == Keys.GASTO }

    val salaryConfirmed = receitas.any { it.source == Keys.SALARIO }
    val direct = receitas.filter { goesToInvest(it, settings) }.sumOf { it.amountCents }
    val splitIncome = receitas.filter { !goesToInvest(it, settings) }.sumOf { it.amountCents }
    val baseIsPlanned = !salaryConfirmed && settings.salaryCents > 0
    val base = if (baseIsPlanned) settings.salaryCents + splitIncome else splitIncome

    val shares = splitByPercent(base, Keys.GROUPS.map { settings.percentOf(it) })
    val byId = categories.associateBy { it.id }
    val spentByCat = gastos.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountCents } }

    val groups = Keys.GROUPS.mapIndexed { i, key ->
        val areas = categories
            .filter { it.groupKey == key }
            .filter { !it.archived || (spentByCat[it.id] ?: 0) > 0 }
            .sortedWith(compareBy({ it.sort }, { it.id }))
            .map { AreaStatus(it, spentByCat[it.id] ?: 0) }
        // Gasto sem área conhecida conta como custo.
        val orphan = if (key == Keys.CUSTOS) {
            gastos.filter { it.categoryId == null || byId[it.categoryId] == null }.sumOf { it.amountCents }
        } else 0L
        val directHere = if (key == Keys.INVEST) direct else 0L
        key to GroupStatus(
            key = key,
            budgetCents = shares[i] + directHere,
            fromSplitCents = shares[i],
            directCents = directHere,
            spentCents = areas.sumOf { it.spentCents } + orphan,
            areas = areas,
        )
    }.toMap()

    val billStatus = billsFor(month, bills, entries)
    val pending = billStatus
        .filter { !it.paid && it.bill.active }
        .filter { b -> b.bill.categoryId?.let { byId[it]?.groupKey } ?: Keys.CUSTOS == Keys.CUSTOS }
        .sumOf { it.bill.amountCents }

    val custos = groups.getValue(Keys.CUSTOS)
    val current = YearMonth.from(today)
    val daysLeft = when {
        month == current -> month.lengthOfMonth() - today.dayOfMonth + 1
        month.isAfter(current) -> month.lengthOfMonth()
        else -> null
    }
    val fraction = if (month == current) today.dayOfMonth.toDouble() / month.lengthOfMonth() else null

    return MonthSummary(
        month = month,
        baseCents = base,
        baseIsPlanned = baseIsPlanned,
        salaryConfirmed = salaryConfirmed,
        incomeCents = receitas.sumOf { it.amountCents },
        groups = groups,
        bills = billStatus,
        pendingFixedCents = pending,
        freeCents = custos.budgetCents - custos.spentCents - pending,
        daysLeft = daysLeft,
        monthFraction = fraction,
    )
}

/** Contas fixas que valem no mês, com o pagamento lançado (se houver). */
fun billsFor(month: YearMonth, bills: List<FixedBill>, monthEntries: List<Entry>): List<BillStatus> {
    val payments = monthEntries.filter { it.fixedBillId != null }.associateBy { it.fixedBillId }
    return bills
        .filter { b ->
            val started = !Dates.ym(b.startEpochDay).isAfter(month)
            (b.active && started) || payments.containsKey(b.id)
        }
        .map { BillStatus(it, Dates.dayIn(month, it.dueDay), payments[it.id]) }
        .sortedWith(compareBy({ it.due }, { it.bill.name }))
}

// ---------- Para fazer ----------

sealed class Todo {
    data class ConfirmSalary(val cents: Long, val day: Int) : Todo()
    data class PayBill(val status: BillStatus) : Todo()
    /** Guardar ou investir o que falta do mês, numa área do grupo. */
    data class Contribute(val groupKey: String, val remainingCents: Long, val categoryId: Long) : Todo()
}

fun todos(summary: MonthSummary, settings: Settings, today: LocalDate): List<Todo> {
    if (summary.month != YearMonth.from(today)) return emptyList()
    val out = mutableListOf<Todo>()
    if (!summary.salaryConfirmed && settings.salaryCents > 0) {
        out += Todo.ConfirmSalary(settings.salaryCents, settings.payday)
    }
    summary.bills.filter { !it.paid && it.bill.active }.forEach { out += Todo.PayBill(it) }
    for (key in listOf(Keys.RESERVA, Keys.INVEST)) {
        val g = summary.group(key)
        val area = g.areas.filter { !it.category.archived }
            .maxByOrNull { it.remainingCents } ?: continue
        if (g.remainingCents > 0) out += Todo.Contribute(key, g.remainingCents, area.category.id)
    }
    return out
}

// ---------- Áreas ----------

/** Áreas ativas de gasto ordenadas pelo uso nos últimos 90 dias. */
fun areasByUsage(categories: List<Category>, entries: List<Entry>, today: LocalDate): List<Category> {
    val since = today.minusDays(90).toEpochDay()
    val uses = entries.filter { it.kind == Keys.GASTO && it.epochDay >= since }
        .groupingBy { it.categoryId }.eachCount()
    val groupOrder = Keys.GROUPS
    return categories.filter { !it.archived }.sortedWith(
        compareByDescending<Category> { uses[it.id] ?: 0 }
            .thenBy { groupOrder.indexOf(it.groupKey) }
            .thenBy { it.sort },
    )
}

// ---------- Metas ----------

enum class MonthlyBasis { DEFINIDO, MEDIA, ESTIMADA }

data class GoalStatus(
    val goal: Goal,
    val currentCents: Long,
    val monthlyCents: Long,
    val basis: MonthlyBasis,
    /** Mês em que atinge; null quando já atingiu ou não atinge em 100 anos. */
    val reachMonth: YearMonth?,
    val reached: Boolean,
) {
    val ratio: Double get() = if (goal.targetCents <= 0) 1.0 else min(1.0, currentCents.toDouble() / goal.targetCents)
    val percent: Int get() = (ratio * 100).toInt()
    val missingCents: Long get() = max(0, goal.targetCents - currentCents)
}

/** Patrimônio do grupo: o "já tenho" mais tudo que foi guardado/investido até [upTo]. */
fun groupBalance(
    group: String,
    settings: Settings,
    categories: List<Category>,
    entries: List<Entry>,
    upTo: Long,
): Long {
    val ids = categories.filter { it.groupKey == group }.map { it.id }.toSet()
    return settings.initialOf(group) + entries
        .filter { it.kind == Keys.GASTO && it.categoryId in ids && it.epochDay <= upTo }
        .sumOf { it.amountCents }
}

/**
 * Aporte mensal usado na previsão: o definido na meta; senão a média dos meses
 * fechados (até 3) desde que o app começou a ser usado; senão a parte do salário.
 */
fun monthlyContribution(
    goal: Goal,
    settings: Settings,
    categories: List<Category>,
    entries: List<Entry>,
    current: YearMonth,
): Pair<Long, MonthlyBasis> {
    goal.monthlyOverrideCents?.let { if (it > 0) return it to MonthlyBasis.DEFINIDO }
    val first = entries.minOfOrNull { it.epochDay }?.let { Dates.ym(it) }
    if (first != null) {
        val start = maxOf(first, current.minusMonths(3))
        val months = generateSequence(start) { it.plusMonths(1) }.takeWhile { it.isBefore(current) }.toList()
        if (months.isNotEmpty()) {
            val ids = categories.filter { it.groupKey == goal.groupKey }.map { it.id }.toSet()
            val from = months.first().atDay(1).toEpochDay()
            val to = months.last().atEndOfMonth().toEpochDay()
            val total = entries.filter { it.kind == Keys.GASTO && it.categoryId in ids && it.epochDay in from..to }
                .sumOf { it.amountCents }
            return total / months.size to MonthlyBasis.MEDIA
        }
    }
    return settings.salaryCents * settings.percentOf(goal.groupKey) / 100 to MonthlyBasis.ESTIMADA
}

/**
 * Mês em que o saldo atinge a meta, com aportes no fim de cada mês e juros compostos
 * mensais equivalentes à taxa anual. A contagem começa no mês seguinte a [current].
 */
fun projectReach(currentCents: Long, targetCents: Long, monthlyCents: Long, rateBp: Int, current: YearMonth): YearMonth? {
    if (currentCents >= targetCents) return null
    val r = (1.0 + max(rateBp, 0) / 10000.0).pow(1.0 / 12) - 1
    if (monthlyCents <= 0 && (r == 0.0 || currentCents <= 0)) return null
    var balance = currentCents.toDouble()
    for (m in 1..1200) {
        balance = balance * (1 + r) + monthlyCents
        if (balance >= targetCents) return current.plusMonths(m.toLong())
    }
    return null
}

fun goalStatuses(
    goals: List<Goal>,
    settings: Settings,
    categories: List<Category>,
    entries: List<Entry>,
    today: LocalDate,
): List<GoalStatus> {
    val current = YearMonth.from(today)
    return goals.sortedWith(compareBy({ it.position }, { it.id })).map { g ->
        val have = groupBalance(g.groupKey, settings, categories, entries, today.toEpochDay())
        val (monthly, basis) = monthlyContribution(g, settings, categories, entries, current)
        GoalStatus(
            goal = g,
            currentCents = have,
            monthlyCents = monthly,
            basis = basis,
            reachMonth = projectReach(have, g.targetCents, monthly, g.rateBp, current),
            reached = have >= g.targetCents,
        )
    }
}

/** A meta atual é a primeira da fila que ainda não foi atingida. */
fun currentGoal(statuses: List<GoalStatus>): GoalStatus? =
    statuses.firstOrNull { !it.reached } ?: statuses.lastOrNull()

// ---------- Fechamento do mês ----------

data class MonthClose(
    val summary: MonthSummary,
    /** O que sobrou do orçamento de custos. Negativo quando passou. */
    val leftoverCents: Long,
    val over: List<AreaStatus>,
    val under: List<AreaStatus>,
)

fun closeMonth(summary: MonthSummary): MonthClose {
    val custos = summary.custos
    val active = custos.areas.filter { it.category.plannedCents > 0 || it.spentCents > 0 }
    return MonthClose(
        summary = summary,
        leftoverCents = custos.budgetCents - custos.spentCents,
        over = active.filter { it.isOver }.sortedByDescending { -it.remainingCents },
        under = active.filter { it.remainingCents > 0 }.sortedByDescending { it.remainingCents },
    )
}

/** Mês fechado mais recente cujo resumo ainda não foi visto e que teve lançamentos. */
fun pendingClose(settings: Settings, entries: List<Entry>, today: LocalDate): YearMonth? {
    val prev = YearMonth.from(today).minusMonths(1)
    if (Dates.key(prev) <= settings.lastSummaryMonth) return null
    return if (entriesIn(entries, prev).isNotEmpty()) prev else null
}

/** Meses fechados com lançamentos, do mais recente ao mais antigo. */
fun closedMonths(entries: List<Entry>, today: LocalDate): List<YearMonth> {
    val current = YearMonth.from(today)
    return entries.map { Dates.ym(it.epochDay) }.distinct().filter { it.isBefore(current) }.sortedDescending()
}
