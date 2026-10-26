package app.financas.domain

import kotlin.math.abs

/** Formatação brasileira sem depender do Locale do aparelho. */
object Money {
    private fun group(n: Long): String {
        val s = n.toString()
        val sb = StringBuilder()
        for ((i, c) in s.withIndex()) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return sb.toString()
    }

    /** 123456 -> "1.234,56" */
    fun plain(cents: Long): String {
        val a = abs(cents)
        val sign = if (cents < 0) "−" else ""
        return sign + group(a / 100) + "," + (a % 100).toString().padStart(2, '0')
    }

    /** 123456 -> "R$ 1.234,56"; negativo -> "−R$ 1.234,56" */
    fun brl(cents: Long): String {
        val a = abs(cents)
        val sign = if (cents < 0) "−" else ""
        return sign + "R$ " + group(a / 100) + "," + (a % 100).toString().padStart(2, '0')
    }

    /** Parte inteira para valores grandes: "R$ 1.234" */
    fun intPart(cents: Long): String {
        val sign = if (cents < 0) "−" else ""
        return sign + "R$ " + group(abs(cents) / 100)
    }

    /** ",56" */
    fun decPart(cents: Long): String = "," + (abs(cents) % 100).toString().padStart(2, '0')

    /** Sem centavos, arredondado: metas grandes. "R$ 22.500" */
    fun short(cents: Long): String {
        val reais = (abs(cents) + 50) / 100
        val sign = if (cents < 0) "−" else ""
        return sign + "R$ " + group(reais)
    }

    /**
     * Texto digitado -> centavos. Aceita "1.234,56", "1234,56", "1234.56", "R$ 50",
     * "1.234" (milhar). Vazio ou inválido -> null. Zero é aceito.
     */
    fun parse(input: String?): Long? {
        if (input == null) return null
        var s = input.replace("R$", "").replace(" ", "").replace(" ", "").trim()
        if (s.isEmpty()) return null
        if (s.contains(',')) {
            s = s.replace(".", "").replace(',', '.')
        } else if (Regex("^\\d{1,3}(\\.\\d{3})+$").matches(s)) {
            s = s.replace(".", "")
        }
        if (!Regex("^\\d+(\\.\\d{1,2})?$").matches(s)) return null
        val parts = s.split('.')
        val whole = parts[0].toLongOrNull() ?: return null
        val frac = if (parts.size > 1) parts[1].padEnd(2, '0').toLong() else 0L
        return whole * 100 + frac
    }

    /** Teclado do app: dígitos digitados formam centavos ("31290" -> 312,90). */
    fun fromDigits(digits: String): Long = digits.toLongOrNull() ?: 0L

    fun percent(bp: Int): String {
        val whole = bp / 100
        val frac = (bp % 100) / 10
        return if (frac == 0) "$whole%" else "$whole,$frac%"
    }
}
