package app.financas.domain

import app.financas.data.Category
import app.financas.data.Entry
import app.financas.data.FixedBill
import app.financas.data.Goal
import app.financas.data.Keys
import app.financas.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetTest {
    private val today = LocalDate.of(2026, 10, 26)
    private val oct = YearMonth.of(2026, 10)
    private fun day(d: Int, m: Int = 10) = LocalDate.of(2026, m, d).toEpochDay()

    private val settings = Settings(salaryCents = 500000, payday = 5, hasVariable = true, onboarded = true)
    private val cats = listOf(
        Category(1, "Mercado", Keys.CUSTOS, 80000),
        Category(2, "Transporte", Keys.CUSTOS, 30000),
        Category(3, "Moradia", Keys.CUSTOS, 150000),
        Category(4, "Reserva", Keys.RESERVA, 50000),
        Category(5, "Renda fixa", Keys.INVEST, 75000),
    )

    @Test fun money() {
        assertEquals("R$ 1.234,56", Money.brl(123456))
        assertEquals("R$ 0,05", Money.brl(5))
        assertEquals("−R$ 12,90", Money.brl(-1290))
        assertEquals("R$ 1.000.000,00", Money.brl(100000000))
        assertEquals("R$ 22.500", Money.short(2250000))
        assertEquals("R$ 3.000", Money.intPart(300000))
        assertEquals(",00", Money.decPart(300000))
        assertEquals(123456L, Money.parse("1.234,56"))
        assertEquals(123456L, Money.parse("R$ 1234.56"))
        assertEquals(123400L, Money.parse("1.234"))
        assertEquals(5000L, Money.parse("50"))
        assertEquals(0L, Money.parse("0"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
        assertEquals(Money.MAX_CENTS, Money.parse("999.999.999,99"))
        assertNull(Money.parse("1.000.000.000,00")) // acima do limite
        assertNull(Money.parse("99999999999999999999")) // não estoura o Long
        assertEquals(1234L, Money.parse("0012,34"))
        assertEquals("10%", Money.percent(1000))
        assertEquals("10,5%", Money.percent(1050))
    }

    @Test fun split() {
        assertEquals(listOf(375000L, 50000L, 75000L), splitByPercent(500000, listOf(75, 10, 15)))
        val odd = splitByPercent(1001, listOf(75, 10, 15))
        assertEquals(1001L, odd.sum())
        assertEquals(listOf(34L, 33L, 33L), splitInstallments(100, 3))
        assertEquals(100L, splitInstallments(100, 3).sum())
    }

    @Test fun segments() {
        assertEquals(0, litSegments(40, 0.0))
        assertEquals(1, litSegments(40, 0.001)) // qualquer valor acende 1
        assertEquals(40, litSegments(40, 1.5))
        assertEquals(5, litSegments(40, 3000.0 / 22500)) // 5,33 -> 5
        assertEquals(listOf(30, 4, 6), allocateSegments(40, listOf(375000, 50000, 75000)))
        assertEquals(40, allocateSegments(40, listOf(375000, 1, 75000)).sum())
        assertEquals(1, allocateSegments(40, listOf(375000, 1, 75000))[1])
    }

    @Test fun monthWithPlannedSalary() {
        val entries = listOf(
            Entry(1, day(10), "Feira", 35040, Keys.GASTO, categoryId = 1),
            Entry(2, day(26), "Gasolina", 31290, Keys.GASTO, categoryId = 2),
        )
        val bills = listOf(
            FixedBill(1, "Aluguel", 150000, dueDay = 5, categoryId = 3, startEpochDay = day(1)),
        )
        val s = summarizeMonth(oct, today, settings, cats, entries, bills)
        assertTrue(s.baseIsPlanned)
        assertEquals(375000L, s.custos.budgetCents)
        assertEquals(66330L, s.custos.spentCents)
        assertEquals(150000L, s.pendingFixedCents)
        assertEquals(375000L - 66330 - 150000, s.freeCents)
        assertEquals(6, s.daysLeft)
        assertEquals(1, s.areasOver.size) // transporte passou
        val t = todos(s, settings, today)
        assertTrue(t[0] is Todo.ConfirmSalary)
        assertTrue(t[1] is Todo.PayBill)
        assertEquals(4, t.size) // salário, aluguel, guardar, investir
    }

    @Test fun variableIncomeGoesToInvest() {
        val entries = listOf(
            Entry(1, day(5), "Salário", 500000, Keys.RECEITA, source = Keys.SALARIO),
            Entry(2, day(20), "Casamento", 120000, Keys.RECEITA, source = Keys.VARIAVEL),
            Entry(3, day(21), "CDB", 196500, Keys.GASTO, categoryId = 5),
            Entry(4, day(22), "Bônus", 10000, Keys.RECEITA, source = Keys.OUTRA),
        )
        val s = summarizeMonth(oct, today, settings, cats, entries, emptyList())
        assertTrue(!s.baseIsPlanned)
        assertEquals(510000L, s.baseCents)
        assertEquals(76500L + 120000, s.invest.budgetCents)
        assertEquals(120000L, s.invest.directCents)
        // investiu tudo: não sobra "investir" em Para fazer
        assertTrue(todos(s, settings, today).none { it is Todo.Contribute && it.groupKey == Keys.INVEST })
    }

    @Test fun billPaidNotPending() {
        val bills = listOf(FixedBill(1, "Aluguel", 150000, dueDay = 5, categoryId = 3, startEpochDay = day(1)))
        val entries = listOf(Entry(1, day(5), "Aluguel", 150000, Keys.GASTO, categoryId = 3, fixedBillId = 1))
        val s = summarizeMonth(oct, today, settings, cats, entries, bills)
        assertEquals(0L, s.pendingFixedCents)
        assertTrue(s.bills.single().paid)
        // conta criada em novembro não cobra em outubro
        val later = listOf(FixedBill(2, "Internet", 12000, dueDay = 10, startEpochDay = day(1, 11)))
        assertTrue(summarizeMonth(oct, today, settings, cats, emptyList(), later).bills.isEmpty())
    }

    @Test fun goals() {
        val entries = listOf(
            Entry(1, day(10, 7), "Reserva", 50000, Keys.GASTO, categoryId = 4),
            Entry(2, day(10, 8), "Reserva", 50000, Keys.GASTO, categoryId = 4),
            Entry(3, day(10, 9), "Reserva", 50000, Keys.GASTO, categoryId = 4),
            Entry(4, day(10, 10), "Reserva", 50000, Keys.GASTO, categoryId = 4),
            Entry(5, day(10, 12), "Futuro", 50000, Keys.GASTO, categoryId = 4),
        )
        val st = settings.copy(reservaInitialCents = 100000)
        val g = Goal(1, "Reserva de emergência", Keys.RESERVA, 2250000, rateBp = 0)
        val status = goalStatuses(listOf(g), st, cats, entries, today).single()
        assertEquals(300000L, status.currentCents) // lançamento futuro não conta
        assertEquals(MonthlyBasis.MEDIA, status.basis)
        assertEquals(50000L, status.monthlyCents)
        // faltam 19.500 a 500/mês sem juros = 39 meses depois de outubro/2026
        assertEquals(YearMonth.of(2030, 1), status.reachMonth)

        val fresh = goalStatuses(listOf(g), settings, cats, emptyList(), today).single()
        assertEquals(MonthlyBasis.ESTIMADA, fresh.basis)
        assertEquals(50000L, fresh.monthlyCents)
    }

    @Test fun projectionWithInterest() {
        val reach = projectReach(0, 10000000, 87500, 1000, YearMonth.of(2026, 12))
        // R$ 875/mês a 10% a.a. chega a R$ 100 mil em pouco mais de 7 anos
        assertTrue(reach!!.year in 2033..2034)
        assertNull(projectReach(0, 100, 0, 0, oct))
    }

    @Test fun setupPlans() {
        val plans = Setup.defaultPlans(375000, mapOf(Setup.MORADIA to 150000, Setup.CASA to 36000))
        assertEquals(375000L, plans.values.sum())
        assertEquals(150000L, plans[Setup.MORADIA])
        assertTrue(plans.values.all { it % 1000 == 0L || it == plans[Setup.MERCADO] })
    }

    @Test fun pendingCloseOnlyWithEntries() {
        val e = listOf(Entry(1, day(10, 9), "x", 100, Keys.GASTO, categoryId = 1))
        assertEquals(YearMonth.of(2026, 9), pendingClose(settings, e, today))
        assertNull(pendingClose(settings.copy(lastSummaryMonth = 202609), e, today))
        assertNull(pendingClose(settings, emptyList(), today))
    }

    @Test fun withdrawal() {
        val entries = listOf(
            Entry(1, day(10, 9), "Reserva", 300000, Keys.GASTO, categoryId = 4),
            Entry(2, day(20), "Conserto do carro", 80000, Keys.RECEITA, categoryId = 4, source = Keys.RESGATE),
            Entry(3, day(21), "Oficina", 80000, Keys.GASTO, categoryId = 2),
        )
        val s = summarizeMonth(oct, today, settings, cats, entries, emptyList())
        // o resgate entra no orçamento de custos e compensa o gasto
        assertEquals(375000L + 80000, s.custos.budgetCents)
        assertEquals(375000L, s.freeCents)
        assertEquals(0L, s.incomeCents) // não é dinheiro novo
        assertEquals(80000L, s.reserva.withdrawnCents)
        assertEquals(-80000L, s.reserva.netContributionCents)
        // a meta perde o que saiu
        assertEquals(220000L, groupBalance(Keys.RESERVA, settings, cats, entries, today.toEpochDay()))
        // resgate não confirma salário nem entra na divisão
        assertTrue(s.baseIsPlanned)
        assertEquals(500000L, s.baseCents)
    }

    @Test fun repayWithdrawal() {
        val entries = listOf(
            Entry(1, day(10, 9), "Reserva", 300000, Keys.GASTO, categoryId = 4),
            Entry(2, day(5), "Conserto", 80000, Keys.RECEITA, categoryId = 4, source = Keys.RESGATE, repay = true),
            Entry(3, day(6), "Sem repor", 10000, Keys.RECEITA, categoryId = 4, source = Keys.RESGATE),
            Entry(4, day(20), "Reposição", 30000, Keys.GASTO, categoryId = 4, repay = true),
            Entry(5, day(21), "Guardar do mês", 50000, Keys.GASTO, categoryId = 4),
        )
        // só o resgate marcado vira pendência: 800 - 300 = 500
        val debts = repayDebts(cats, entries)
        assertEquals(1, debts.size)
        assertEquals(50000L, debts.single().outstandingCents)

        val s = summarizeMonth(oct, today, settings, cats, entries, emptyList())
        // a reposição sai do livre para gastar…
        assertEquals(30000L, s.repaidCents)
        assertEquals(375000L + 90000 - 30000, s.freeCents)
        // …e não conta como o guardar do mês (500 de 500 cumpridos só com o aporte regular)
        assertEquals(50000L, s.reserva.spentCents)
        assertTrue(todos(s, settings, today, debts).none { it is Todo.Contribute && it.groupKey == Keys.RESERVA })
        assertTrue(todos(s, settings, today, debts).any { it is Todo.Repay })
        // patrimônio: 3000 - 800 - 100 + 300 + 500
        assertEquals(290000L, groupBalance(Keys.RESERVA, settings, cats, entries, today.toEpochDay()))

        // repor tudo zera a pendência
        val done = entries + Entry(6, day(22), "Reposição", 50000, Keys.GASTO, categoryId = 4, repay = true)
        assertTrue(repayDebts(cats, done).isEmpty())
    }
}
