package app.financas.domain

import java.time.LocalDate
import java.time.YearMonth

object Dates {
    private val short = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    private val long = listOf(
        "janeiro", "fevereiro", "março", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
    )

    fun ym(epochDay: Long): YearMonth = YearMonth.from(LocalDate.ofEpochDay(epochDay))
    fun key(m: YearMonth): Int = m.year * 100 + m.monthValue
    fun fromKey(k: Int): YearMonth = YearMonth.of(k / 100, k % 100)

    /** "out 2026" */
    fun monthShort(m: YearMonth): String = short[m.monthValue - 1] + " " + m.year

    /** "outubro" */
    fun monthName(m: YearMonth): String = long[m.monthValue - 1]

    /** "Outubro de 2026" */
    fun monthLong(m: YearMonth): String = long[m.monthValue - 1].replaceFirstChar { it.uppercase() } + " de " + m.year

    /** "26 out" */
    fun dayShort(d: LocalDate): String = d.dayOfMonth.toString().padStart(2, '0') + " " + short[d.monthValue - 1]

    /** Cabeçalho do histórico: "hoje, 26 out", "ontem, 25 out", "20 out". */
    fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "hoje, " + dayShort(d)
        today.minusDays(1) -> "ontem, " + dayShort(d)
        else -> dayShort(d)
    }

    /** Texto curto para chips e resumos: "hoje", "ontem", "20 out". */
    fun relative(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "hoje"
        today.minusDays(1) -> "ontem"
        else -> dayShort(d)
    }

    /** Dia do mês ajustado ao tamanho do mês (dia 31 em fevereiro vira 28/29). */
    fun dayIn(m: YearMonth, day: Int): LocalDate = m.atDay(day.coerceIn(1, m.lengthOfMonth()))
}
