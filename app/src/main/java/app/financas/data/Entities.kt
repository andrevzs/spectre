package app.financas.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Valores em centavos (Long). Datas em epochDay (dias desde 1970-01-01).
// Grupos, tipos e origens são strings fixas (ver Keys) para o backup ficar legível.

object Keys {
    const val CUSTOS = "custos"
    const val RESERVA = "reserva"
    const val INVEST = "investimento"
    val GROUPS = listOf(CUSTOS, RESERVA, INVEST)

    const val GASTO = "gasto"
    const val RECEITA = "receita"

    const val SALARIO = "salario"
    const val VARIAVEL = "variavel"
    const val OUTRA = "outra"

    val METHODS = listOf("pix", "débito", "crédito", "dinheiro")
}

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 1,
    val salaryCents: Long = 0,
    val payday: Int = 5,
    val hasVariable: Boolean = false,
    val variableName: String = "Música",
    val variableToInvest: Boolean = true,
    val pctCustos: Int = 75,
    val pctReserva: Int = 10,
    val pctInvest: Int = 15,
    /** "Já tenho" fora do app: soma ao progresso de todas as metas do grupo. */
    val reservaInitialCents: Long = 0,
    val investInitialCents: Long = 0,
    val onboarded: Boolean = false,
    val lastMethod: String = "pix",
    val remindDaily: Boolean = false,
    val remindBills: Boolean = false,
    /** Último mês (AAAAMM) cujo resumo de fechamento já foi visto. */
    val lastSummaryMonth: Int = 0,
) {
    fun percentOf(group: String): Int = when (group) {
        Keys.CUSTOS -> pctCustos
        Keys.RESERVA -> pctReserva
        else -> pctInvest
    }

    fun initialOf(group: String): Long = when (group) {
        Keys.RESERVA -> reservaInitialCents
        Keys.INVEST -> investInitialCents
        else -> 0
    }
}

@Entity(tableName = "category")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val groupKey: String,
    val plannedCents: Long = 0,
    val archived: Boolean = false,
    val sort: Int = 0,
)

@Entity(tableName = "entry", indices = [Index("epochDay"), Index("categoryId"), Index("fixedBillId")])
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val description: String = "",
    val amountCents: Long,
    /** gasto | receita. Guardar e investir são gastos numa área desses grupos. */
    val kind: String,
    val categoryId: Long? = null,
    /** Só receitas: salario | variavel | outra. */
    val source: String? = null,
    val method: String? = null,
    val installmentNo: Int = 1,
    val installmentCount: Int = 1,
    /** Liga as parcelas de uma mesma compra. */
    val seriesId: String? = null,
    /** Pagamento de uma conta fixa neste mês. */
    val fixedBillId: Long? = null,
    val createdAt: Long = 0,
)

@Entity(tableName = "fixed_bill")
data class FixedBill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Valor fixo, ou a estimativa quando a conta é variável. */
    val amountCents: Long,
    val variable: Boolean = false,
    val dueDay: Int,
    val categoryId: Long? = null,
    val active: Boolean = true,
    /** A conta só cobra a partir do mês em que foi criada. */
    val startEpochDay: Long = 0,
)

@Entity(tableName = "goal")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val groupKey: String,
    val targetCents: Long,
    /** Rendimento anual líquido em pontos-base (1000 = 10%). */
    val rateBp: Int = 1000,
    /** Aporte mensal definido; null usa a média real. */
    val monthlyOverrideCents: Long? = null,
    val position: Int = 0,
)
