package app.financas.data

import androidx.room.withTransaction
import app.financas.domain.Setup
import app.financas.domain.splitByPercent
import app.financas.domain.splitInstallments
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** O que a tela de lançar entrega para salvar. */
data class EntryDraft(
    val kind: String,
    val amountCents: Long,
    val categoryId: Long?,
    val source: String?,
    val date: LocalDate,
    val description: String,
    val method: String?,
    val installments: Int = 1,
    val repeatMonthly: Boolean = false,
    val fixedBillId: Long? = null,
)

data class SetupInput(
    val salaryCents: Long,
    val payday: Int,
    val hasVariable: Boolean,
    val variableToInvest: Boolean,
    val pctCustos: Int,
    val pctReserva: Int,
    val pctInvest: Int,
    /** Contas marcadas com o valor por mês. */
    val bills: List<Pair<Setup.BillPreset, Long>>,
    val reserveMonths: Int,
    val reserveHaveCents: Long,
)

class Repository(private val db: AppDatabase) {
    private val dao = db.dao()

    val settings = dao.settings()
    val categories = dao.categories()
    val entries = dao.entries()
    val bills = dao.bills()
    val goals = dao.goals()

    suspend fun settingsNow(): Settings = dao.settingsNow() ?: Settings()

    suspend fun saveSettings(s: Settings) = dao.saveSettings(s)

    suspend fun exportEntries(): List<Entry> = dao.entriesNow()
    suspend fun billsList(): List<FixedBill> = dao.billsNow()

    // ---------- Lançamentos ----------

    /** Salva o lançamento (e as parcelas, e a conta fixa se pedir). Retorna o id do primeiro. */
    suspend fun addEntry(d: EntryDraft): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        var billId = d.fixedBillId
        if (d.repeatMonthly && d.kind == Keys.GASTO && billId == null) {
            val catName = d.categoryId?.let { id -> dao.categoriesNow().firstOrNull { it.id == id }?.name }
            billId = dao.insertBill(
                FixedBill(
                    name = d.description.ifBlank { catName ?: "Conta" },
                    amountCents = d.amountCents,
                    dueDay = d.date.dayOfMonth,
                    categoryId = d.categoryId,
                    startEpochDay = d.date.withDayOfMonth(1).toEpochDay(),
                ),
            )
        }
        val n = if (d.kind == Keys.GASTO && d.method == "crédito") d.installments.coerceIn(1, 48) else 1
        val series = if (n > 1) UUID.randomUUID().toString() else null
        val parts = splitInstallments(d.amountCents, n)
        var firstId = 0L
        for (i in 0 until n) {
            val id = dao.insertEntry(
                Entry(
                    epochDay = d.date.plusMonths(i.toLong()).toEpochDay(),
                    description = d.description.trim(),
                    amountCents = parts[i],
                    kind = d.kind,
                    categoryId = if (d.kind == Keys.GASTO) d.categoryId else null,
                    source = if (d.kind == Keys.RECEITA) d.source else null,
                    method = if (d.kind == Keys.GASTO) d.method else null,
                    installmentNo = i + 1,
                    installmentCount = n,
                    seriesId = series,
                    fixedBillId = if (i == 0) billId else null,
                    createdAt = now,
                ),
            )
            if (i == 0) firstId = id
        }
        if (d.kind == Keys.GASTO && d.method != null) {
            val s = settingsNow()
            if (s.lastMethod != d.method) dao.saveSettings(s.copy(lastMethod = d.method))
        }
        firstId
    }

    suspend fun entry(id: Long): Entry? = dao.entry(id)

    /** Edição: altera só este lançamento (numa compra parcelada, só esta parcela). */
    suspend fun updateEntry(old: Entry, d: EntryDraft) {
        dao.updateEntry(
            old.copy(
                epochDay = d.date.toEpochDay(),
                description = d.description.trim(),
                amountCents = d.amountCents,
                kind = d.kind,
                categoryId = if (d.kind == Keys.GASTO) d.categoryId else null,
                source = if (d.kind == Keys.RECEITA) d.source else null,
                method = if (d.kind == Keys.GASTO) d.method else null,
            ),
        )
    }

    /** Exclui o lançamento; numa compra parcelada, todas as parcelas. Retorna o que saiu, para desfazer. */
    suspend fun deleteEntry(e: Entry): List<Entry> = db.withTransaction {
        val all = e.seriesId?.let { dao.series(it) } ?: listOf(e)
        dao.deleteEntries(all)
        all
    }

    suspend fun restore(entries: List<Entry>) = dao.restoreEntries(entries)

    suspend fun confirmSalary(today: LocalDate): Entry {
        val s = settingsNow()
        val e = Entry(
            epochDay = today.toEpochDay(), description = "Salário", amountCents = s.salaryCents,
            kind = Keys.RECEITA, source = Keys.SALARIO, createdAt = System.currentTimeMillis(),
        )
        return e.copy(id = dao.insertEntry(e))
    }

    suspend fun payBill(bill: FixedBill, today: LocalDate, amountCents: Long = bill.amountCents): Entry {
        val e = Entry(
            epochDay = today.toEpochDay(), description = bill.name, amountCents = amountCents,
            kind = Keys.GASTO, categoryId = bill.categoryId, fixedBillId = bill.id,
            createdAt = System.currentTimeMillis(),
        )
        return e.copy(id = dao.insertEntry(e))
    }

    // ---------- Orçamento ----------

    suspend fun addCategory(name: String, group: String): Long {
        val sort = (dao.categoriesNow().filter { it.groupKey == group }.maxOfOrNull { it.sort } ?: 0) + 1
        return dao.insertCategory(Category(name = name.trim(), groupKey = group, sort = sort))
    }

    suspend fun updateCategory(c: Category) = dao.updateCategory(c)

    suspend fun addBill(b: FixedBill): Long = dao.insertBill(b)
    suspend fun updateBill(b: FixedBill) = dao.updateBill(b)

    // ---------- Metas ----------

    suspend fun saveGoal(g: Goal): Long = if (g.id == 0L) {
        val pos = (dao.goalsNow().maxOfOrNull { it.position } ?: -1) + 1
        dao.insertGoal(g.copy(position = if (g.position < 0) pos else g.position))
    } else {
        dao.updateGoal(g); g.id
    }

    suspend fun deleteGoal(g: Goal) = dao.deleteGoal(g)

    /** Move a meta para a posição [to] (0 = primeira) e renumera as outras. */
    suspend fun moveGoal(g: Goal, to: Int) = db.withTransaction {
        val list = dao.goalsNow().filter { it.id != g.id }.toMutableList()
        list.add(to.coerceIn(0, list.size), g)
        dao.updateGoals(list.mapIndexed { i, x -> x.copy(position = i) })
    }

    // ---------- Configuração inicial ----------

    suspend fun completeSetup(input: SetupInput) = db.withTransaction {
        val s = settingsNow()
        dao.saveSettings(
            s.copy(
                salaryCents = input.salaryCents,
                payday = input.payday,
                hasVariable = input.hasVariable,
                variableToInvest = input.variableToInvest,
                pctCustos = input.pctCustos,
                pctReserva = input.pctReserva,
                pctInvest = input.pctInvest,
                reservaInitialCents = input.reserveHaveCents,
                onboarded = true,
                // Não mostra resumo de meses anteriores à configuração.
                lastSummaryMonth = maxOf(s.lastSummaryMonth, LocalDate.now().minusMonths(1).let { it.year * 100 + it.monthValue }),
            ),
        )
        val shares = splitByPercent(input.salaryCents, listOf(input.pctCustos, input.pctReserva, input.pctInvest))

        if (dao.categoriesNow().isEmpty()) {
            val billsByArea = input.bills.groupBy { it.first.area }.mapValues { (_, l) -> l.sumOf { it.second } }
            val plans = Setup.defaultPlans(shares[0], billsByArea)
            Setup.CUSTOS_AREAS.forEachIndexed { i, name ->
                dao.insertCategory(Category(name = name, groupKey = Keys.CUSTOS, plannedCents = plans[name] ?: 0, sort = i))
            }
            dao.insertCategory(Category(name = Setup.RESERVA_AREA, groupKey = Keys.RESERVA, plannedCents = shares[1]))
            Setup.INVEST_AREAS.forEachIndexed { i, name ->
                dao.insertCategory(Category(name = name, groupKey = Keys.INVEST, plannedCents = if (i == 0) shares[2] else 0, sort = i))
            }
        }

        val cats = dao.categoriesNow()
        val existing = dao.billsNow().map { it.name.lowercase() }.toSet()
        val start = LocalDate.now().withDayOfMonth(1).toEpochDay()
        for ((preset, cents) in input.bills) {
            if (preset.name.lowercase() in existing) continue
            dao.insertBill(
                FixedBill(
                    name = preset.name, amountCents = cents, variable = preset.variable,
                    dueDay = input.payday, categoryId = cats.firstOrNull { it.name == preset.area }?.id,
                    startEpochDay = start,
                ),
            )
        }

        val goals = dao.goalsNow()
        val target = shares[0] * input.reserveMonths
        val reserveGoal = goals.firstOrNull { it.groupKey == Keys.RESERVA }
        if (reserveGoal != null) {
            dao.updateGoal(reserveGoal.copy(targetCents = target))
        } else {
            dao.updateGoals(goals.map { it.copy(position = it.position + 1) })
            dao.insertGoal(Goal(name = "Reserva de emergência", groupKey = Keys.RESERVA, targetCents = target, position = 0))
        }
        if (goals.none { it.groupKey == Keys.INVEST }) {
            listOf("Primeiros R$ 10 mil" to 1_000_000L, "R$ 100 mil" to 10_000_000L, "R$ 500 mil" to 50_000_000L)
                .forEachIndexed { i, (name, cents) ->
                    dao.insertGoal(Goal(name = name, groupKey = Keys.INVEST, targetCents = cents, position = 1 + i))
                }
        }
    }

    // ---------- Dados ----------

    suspend fun wipe() = db.withTransaction {
        dao.clearEntries(); dao.clearCategories(); dao.clearBills(); dao.clearGoals(); dao.clearSettings()
    }

    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("app", "financas")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("settings", Backup.settings(settingsNow()))
        root.put("categories", JSONArray(dao.categoriesNow().map(Backup::category)))
        root.put("entries", JSONArray(dao.entriesNow().map(Backup::entry)))
        root.put("bills", JSONArray(dao.billsNow().map(Backup::bill)))
        root.put("goals", JSONArray(dao.goalsNow().map(Backup::goal)))
        return root.toString(1)
    }

    /** Substitui todos os dados pelos do arquivo. Lança erro se o arquivo não for um backup válido. */
    suspend fun importJson(text: String) {
        val root = JSONObject(text)
        require(root.optString("app") == "financas") { "Este arquivo não é uma cópia do app." }
        val settings = Backup.settings(root.getJSONObject("settings"))
        val cats = root.getJSONArray("categories").objects().map(Backup::category)
        val entries = root.getJSONArray("entries").objects().map(Backup::entry)
        val bills = root.getJSONArray("bills").objects().map(Backup::bill)
        val goals = root.getJSONArray("goals").objects().map(Backup::goal)
        db.withTransaction {
            dao.clearEntries(); dao.clearCategories(); dao.clearBills(); dao.clearGoals(); dao.clearSettings()
            dao.saveSettings(settings)
            dao.insertCategories(cats)
            dao.restoreEntries(entries)
            dao.insertBills(bills)
            dao.insertGoals(goals)
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}

/** Conversão para JSON, campo a campo, para o arquivo continuar legível e estável. */
private object Backup {
    private fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k) || !has(k)) null else getLong(k)
    private fun JSONObject.optStringOrNull(k: String): String? = if (isNull(k) || !has(k)) null else getString(k)

    fun settings(s: Settings) = JSONObject().apply {
        put("salaryCents", s.salaryCents); put("payday", s.payday); put("hasVariable", s.hasVariable)
        put("variableName", s.variableName); put("variableToInvest", s.variableToInvest)
        put("pctCustos", s.pctCustos); put("pctReserva", s.pctReserva); put("pctInvest", s.pctInvest)
        put("reservaInitialCents", s.reservaInitialCents); put("investInitialCents", s.investInitialCents)
        put("onboarded", s.onboarded); put("lastMethod", s.lastMethod)
        put("remindDaily", s.remindDaily); put("remindBills", s.remindBills); put("lastSummaryMonth", s.lastSummaryMonth)
    }

    fun settings(o: JSONObject) = Settings(
        salaryCents = o.optLong("salaryCents"), payday = o.optInt("payday", 5), hasVariable = o.optBoolean("hasVariable"),
        variableName = o.optString("variableName", "Música"), variableToInvest = o.optBoolean("variableToInvest", true),
        pctCustos = o.optInt("pctCustos", 75), pctReserva = o.optInt("pctReserva", 10), pctInvest = o.optInt("pctInvest", 15),
        reservaInitialCents = o.optLong("reservaInitialCents"), investInitialCents = o.optLong("investInitialCents"),
        onboarded = o.optBoolean("onboarded", true), lastMethod = o.optString("lastMethod", "pix"),
        remindDaily = o.optBoolean("remindDaily"), remindBills = o.optBoolean("remindBills"),
        lastSummaryMonth = o.optInt("lastSummaryMonth"),
    )

    fun category(c: Category) = JSONObject().apply {
        put("id", c.id); put("name", c.name); put("groupKey", c.groupKey); put("plannedCents", c.plannedCents)
        put("archived", c.archived); put("sort", c.sort)
    }

    fun category(o: JSONObject) = Category(
        id = o.getLong("id"), name = o.getString("name"), groupKey = o.getString("groupKey"),
        plannedCents = o.optLong("plannedCents"), archived = o.optBoolean("archived"), sort = o.optInt("sort"),
    )

    fun entry(e: Entry) = JSONObject().apply {
        put("id", e.id); put("epochDay", e.epochDay); put("description", e.description); put("amountCents", e.amountCents)
        put("kind", e.kind); put("categoryId", e.categoryId ?: JSONObject.NULL); put("source", e.source ?: JSONObject.NULL)
        put("method", e.method ?: JSONObject.NULL); put("installmentNo", e.installmentNo); put("installmentCount", e.installmentCount)
        put("seriesId", e.seriesId ?: JSONObject.NULL); put("fixedBillId", e.fixedBillId ?: JSONObject.NULL); put("createdAt", e.createdAt)
    }

    fun entry(o: JSONObject) = Entry(
        id = o.getLong("id"), epochDay = o.getLong("epochDay"), description = o.optString("description"),
        amountCents = o.getLong("amountCents"), kind = o.getString("kind"), categoryId = o.optLongOrNull("categoryId"),
        source = o.optStringOrNull("source"), method = o.optStringOrNull("method"),
        installmentNo = o.optInt("installmentNo", 1), installmentCount = o.optInt("installmentCount", 1),
        seriesId = o.optStringOrNull("seriesId"), fixedBillId = o.optLongOrNull("fixedBillId"), createdAt = o.optLong("createdAt"),
    )

    fun bill(b: FixedBill) = JSONObject().apply {
        put("id", b.id); put("name", b.name); put("amountCents", b.amountCents); put("variable", b.variable)
        put("dueDay", b.dueDay); put("categoryId", b.categoryId ?: JSONObject.NULL); put("active", b.active)
        put("startEpochDay", b.startEpochDay)
    }

    fun bill(o: JSONObject) = FixedBill(
        id = o.getLong("id"), name = o.getString("name"), amountCents = o.getLong("amountCents"),
        variable = o.optBoolean("variable"), dueDay = o.optInt("dueDay", 1), categoryId = o.optLongOrNull("categoryId"),
        active = o.optBoolean("active", true), startEpochDay = o.optLong("startEpochDay"),
    )

    fun goal(g: Goal) = JSONObject().apply {
        put("id", g.id); put("name", g.name); put("groupKey", g.groupKey); put("targetCents", g.targetCents)
        put("rateBp", g.rateBp); put("monthlyOverrideCents", g.monthlyOverrideCents ?: JSONObject.NULL); put("position", g.position)
    }

    fun goal(o: JSONObject) = Goal(
        id = o.getLong("id"), name = o.getString("name"), groupKey = o.getString("groupKey"),
        targetCents = o.getLong("targetCents"), rateBp = o.optInt("rateBp", 1000),
        monthlyOverrideCents = o.optLongOrNull("monthlyOverrideCents"), position = o.optInt("position"),
    )
}
