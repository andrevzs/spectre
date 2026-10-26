package app.financas.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.financas.FinancasApp
import app.financas.data.Category
import app.financas.data.Entry
import app.financas.data.EntryDraft
import app.financas.data.FixedBill
import app.financas.data.Goal
import app.financas.data.Keys
import app.financas.data.Settings
import app.financas.data.SetupInput
import app.financas.domain.Dates
import app.financas.domain.Money
import app.financas.work.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

data class AppData(
    val settings: Settings,
    val categories: List<Category>,
    val entries: List<Entry>,
    val bills: List<FixedBill>,
    val goals: List<Goal>,
) {
    fun category(id: Long?): Category? = id?.let { i -> categories.firstOrNull { it.id == i } }
}

data class ToastMsg(val text: String, val value: String?, val undo: (suspend () -> Unit)?, val id: Long = System.nanoTime())

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FinancasApp).repository

    val data: StateFlow<AppData?> = combine(repo.settings, repo.categories, repo.entries, repo.bills, repo.goals) { s, c, e, b, g ->
        AppData(s ?: Settings(), c, e, b, g)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var today by mutableStateOf(LocalDate.now())
        private set

    /** Mês visto no início e no histórico (compartilhado entre as duas telas). */
    var month by mutableStateOf(YearMonth.now())

    var toast by mutableStateOf<ToastMsg?>(null)
        private set

    /** O resumo do mês fechado abre sozinho uma vez por sessão. */
    var closePrompted = false

    fun refreshDay() {
        val now = LocalDate.now()
        if (now != today) {
            if (month == YearMonth.from(today)) month = YearMonth.from(now)
            today = now
        }
    }

    fun dismissToast(id: Long) { if (toast?.id == id) toast = null }

    fun undoToast() {
        val t = toast ?: return
        toast = null
        viewModelScope.launch { t.undo?.invoke() }
    }

    private fun show(text: String, value: String?) { toast = ToastMsg(text, value, null) }

    private fun showUndo(text: String, value: String?, undo: suspend () -> Unit) { toast = ToastMsg(text, value, undo) }

    // ---------- Lançamentos ----------

    fun addEntry(d: EntryDraft, onSaved: (Long) -> Unit) = viewModelScope.launch {
        onSaved(repo.addEntry(d))
    }

    fun updateEntry(old: Entry, d: EntryDraft, onDone: () -> Unit) = viewModelScope.launch {
        repo.updateEntry(old, d); onDone()
    }

    fun deleteEntry(e: Entry, label: String) = viewModelScope.launch {
        val removed = repo.deleteEntry(e)
        val total = removed.sumOf { it.amountCents }
        showUndo(label + if (removed.size > 1) " excluída (${removed.size} parcelas)" else " excluída", Money.brl(total)) {
            repo.restore(removed)
        }
    }

    /** "desfazer" logo depois de lançar: apaga sem aviso. */
    fun discardEntry(id: Long, onDone: () -> Unit) = viewModelScope.launch {
        repo.entry(id)?.let { repo.deleteEntry(it) }
        onDone()
    }

    fun confirmSalary() = viewModelScope.launch {
        val e = repo.confirmSalary(today)
        showUndo("Salário confirmado", Money.brl(e.amountCents)) { repo.deleteEntry(e) }
    }

    fun payBill(bill: FixedBill) = viewModelScope.launch {
        val e = repo.payBill(bill, today)
        showUndo("${bill.name} paga", Money.brl(e.amountCents)) { repo.deleteEntry(e) }
    }

    // ---------- Configurações ----------

    fun updateSettings(f: (Settings) -> Settings) = viewModelScope.launch {
        val s = repo.settingsNow()
        val n = f(s)
        repo.saveSettings(n)
        if (n.remindDaily != s.remindDaily || n.remindBills != s.remindBills) {
            Reminders.schedule(getApplication<Application>(), n.remindDaily || n.remindBills)
        }
    }

    fun addCategory(name: String, group: String) = viewModelScope.launch { repo.addCategory(name, group) }
    fun updateCategory(c: Category) = viewModelScope.launch { repo.updateCategory(c) }

    fun saveBill(b: FixedBill) = viewModelScope.launch { if (b.id == 0L) repo.addBill(b) else repo.updateBill(b) }

    fun saveGoal(g: Goal, position: Int? = null) = viewModelScope.launch {
        val id = repo.saveGoal(g)
        if (position != null) repo.moveGoal(g.copy(id = id), position)
    }

    fun deleteGoal(g: Goal) = viewModelScope.launch { repo.deleteGoal(g) }

    fun completeSetup(input: SetupInput, onDone: () -> Unit) = viewModelScope.launch {
        repo.completeSetup(input)
        month = YearMonth.from(today)
        onDone()
    }

    fun markSummarySeen(m: YearMonth) = updateSettings { s ->
        if (Dates.key(m) > s.lastSummaryMonth) s.copy(lastSummaryMonth = Dates.key(m)) else s
    }

    /** Leva a sobra de um mês fechado para guardar ou investir. */
    fun moveLeftover(m: YearMonth, group: String, cents: Long) = viewModelScope.launch {
        val cat = data.value?.categories?.filter { it.groupKey == group && !it.archived }
            ?.maxByOrNull { it.plannedCents } ?: return@launch
        repo.addEntry(
            EntryDraft(
                kind = Keys.GASTO, amountCents = cents, categoryId = cat.id, source = null,
                date = today, description = "Sobra de " + Dates.monthName(m), method = null,
            ),
        )
        show(if (group == Keys.RESERVA) "Sobra guardada" else "Sobra investida", Money.brl(cents))
    }

    // ---------- Dados ----------

    fun export(uri: Uri) = viewModelScope.launch {
        val ok = runCatching {
            val json = repo.exportJson()
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
            }
        }.isSuccess
        show(if (ok) "Cópia salva" else "Não foi possível salvar a cópia", null)
    }

    fun import(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            val text = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            }
            repo.importJson(text)
        }
        show(if (result.isSuccess) "Cópia restaurada" else (result.exceptionOrNull()?.message ?: "Arquivo inválido"), null)
        val s = repo.settingsNow()
        Reminders.schedule(getApplication<Application>(), s.remindDaily || s.remindBills)
    }

    fun wipe(onDone: () -> Unit) = viewModelScope.launch {
        repo.wipe()
        Reminders.schedule(getApplication<Application>(), false)
        onDone()
    }
}
