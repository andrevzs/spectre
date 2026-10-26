package app.financas.domain

/** Padrões da configuração inicial. Tudo pode ser mudado depois no Orçamento. */
object Setup {
    const val MORADIA = "Moradia"
    const val MERCADO = "Mercado"
    const val CASA = "Contas da casa"
    const val TRANSPORTE = "Transporte"
    const val LAZER = "Lazer e restaurantes"
    const val SAUDE = "Saúde"

    val CUSTOS_AREAS = listOf(MORADIA, MERCADO, CASA, LAZER, TRANSPORTE, SAUDE)
    const val RESERVA_AREA = "Reserva de emergência"
    val INVEST_AREAS = listOf("Renda fixa", "Tesouro Direto")

    data class BillPreset(val name: String, val hint: String, val variable: Boolean, val area: String, val checked: Boolean)

    val BILLS = listOf(
        BillPreset("Aluguel ou financiamento", "fixo", false, MORADIA, true),
        BillPreset("Condomínio", "fixo", false, MORADIA, false),
        BillPreset("Luz", "varia: você confirma o valor", true, CASA, true),
        BillPreset("Água", "varia: você confirma o valor", true, CASA, false),
        BillPreset("Internet", "fixo", false, CASA, true),
        BillPreset("Celular", "fixo", false, CASA, true),
        BillPreset("Assinaturas", "streaming, apps", false, LAZER, false),
    )

    data class Preset(val name: String, val custos: Int, val reserva: Int, val invest: Int, val note: String)

    val PRESETS = listOf(
        Preset("75 · 10 · 15", 75, 10, 15, "para começar"),
        Preset("70 · 10 · 20", 70, 10, 20, "mais investimento"),
        Preset("80 · 10 · 10", 80, 10, 10, "custos mais altos"),
    )

    /** Pesos para dividir o que sobra dos custos depois das contas fixas. */
    private val weights = mapOf(MERCADO to 40, LAZER to 25, TRANSPORTE to 20, SAUDE to 15)

    /**
     * Planejado inicial de cada área de custos: as contas fixas de cada área, mais
     * uma parte do restante, arredondada para baixo em múltiplos de R$ 10.
     */
    fun defaultPlans(custosBudget: Long, billsByArea: Map<String, Long>): Map<String, Long> {
        val fixed = billsByArea.values.sum()
        val rest = (custosBudget - fixed).coerceAtLeast(0)
        val plans = CUSTOS_AREAS.associateWith { billsByArea[it] ?: 0L }.toMutableMap()
        var used = 0L
        for ((area, w) in weights) {
            val share = rest * w / 100 / 1000 * 1000
            plans[area] = plans.getValue(area) + share
            used += share
        }
        plans[MERCADO] = plans.getValue(MERCADO) + (rest - used)
        return plans
    }
}
