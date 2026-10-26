package app.financas.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import app.financas.domain.Dates
import app.financas.domain.Money
import app.financas.domain.closeMonth
import app.financas.domain.closedMonths
import app.financas.domain.summarizeMonth
import app.financas.ui.AppData
import app.financas.ui.AppViewModel
import app.financas.ui.Routes
import app.financas.ui.components.Dot
import app.financas.ui.components.Glyph
import app.financas.ui.components.Ic
import app.financas.ui.components.ListRow
import app.financas.ui.components.SectionLabel
import app.financas.ui.components.SubHeader
import app.financas.ui.components.SwitchRow
import app.financas.ui.theme.C
import app.financas.ui.theme.T
import java.time.LocalDate

@Composable
fun ColumnScope.AjustesScreen(vm: AppViewModel, d: AppData, nav: NavHostController) {
    val context = LocalContext.current
    val today = vm.today
    val s = d.settings
    var confirmWipe by remember { mutableStateOf(false) }
    var pendingToggle by remember { mutableStateOf<String?>(null) }

    val months = remember(d, today) { closedMonths(d.entries, today).take(12) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.export(it) }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.import(it) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val which = pendingToggle
        pendingToggle = null
        if (granted && which != null) {
            vm.updateSettings { if (which == "daily") it.copy(remindDaily = true) else it.copy(remindBills = true) }
        }
    }
    fun toggle(which: String, on: Boolean) {
        val needs = Build.VERSION.SDK_INT >= 33 && !on &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needs) {
            pendingToggle = which
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.updateSettings { if (which == "daily") it.copy(remindDaily = !on) else it.copy(remindBills = !on) }
        }
    }

    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        SubHeader("ajustes", { nav.popBackStack() })

        if (months.isNotEmpty()) {
            Section("Resumos anteriores") {
                months.forEach { m ->
                    val c = closeMonth(summarizeMonth(m, today, s, d.categories, d.entries, d.bills))
                    ListRow(
                        Dates.monthLong(m), null, onClick = { nav.navigate(Routes.resumo(Dates.key(m))) },
                    ) {
                        Text(
                            if (c.leftoverCents >= 0) "sobrou ${Money.brl(c.leftoverCents)}" else "passou ${Money.brl(-c.leftoverCents)}",
                            style = T.mono(13, color = if (c.leftoverCents >= 0) C.Muted else C.Alerta),
                        )
                        Glyph(Ic.Forward)
                    }
                }
            }
        }

        Section("Lembretes") {
            SwitchRow("Lembrete às 21h", "Só nos dias em que nada foi lançado", s.remindDaily, { toggle("daily", s.remindDaily) })
            SwitchRow("Contas fixas vencendo", "Um dia antes do vencimento", s.remindBills, { toggle("bills", s.remindBills) })
        }

        Section("Cores com significado") {
            ColorRow("Guardar", C.Reserva, "#F5B83D")
            ColorRow("Investir e entradas", C.Invest, "#34D399")
            ColorRow("Acima do plano", C.Alerta, "#FF5A4E")
            ColorRow("Contas fixas a pagar", C.Pending, "#6B6B67")
        }

        Section("Dados") {
            Text(
                "Tudo fica só neste celular. Salve uma cópia de vez em quando (no Drive, por exemplo); ela também serve para levar os dados para outro aparelho.",
                style = T.sans(13, color = C.Muted), modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                ActionBox("salvar cópia", Modifier.weight(1f)) { saveLauncher.launch("financas-${LocalDate.now()}.json") }
                ActionBox("restaurar cópia", Modifier.weight(1f)) { openLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
            }
            Text("Restaurar substitui todos os dados atuais pelos da cópia.", style = T.sans(12, color = C.Muted), modifier = Modifier.padding(top = 4.dp))
        }

        Section("Configuração") {
            ListRow("Refazer configuração inicial", "Seus lançamentos continuam salvos", onClick = { nav.navigate(Routes.CONFIG) }) {
                Glyph(Ic.Forward)
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { confirmWipe = true },
                verticalAlignment = Alignment.CenterVertically,
            ) { Text("Apagar todos os dados", style = T.sans(15, color = C.Alerta)) }
        }

        Text(
            "finanças 1.0 · dados só neste celular",
            style = T.mono(12, color = C.Dim), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            containerColor = C.Surface,
            title = { Text("Apagar todos os dados?", style = T.sans(17, 500)) },
            text = { Text("Lançamentos, áreas, contas e metas somem deste celular. Não dá para desfazer, a não ser que você tenha uma cópia salva.", style = T.sans(15, color = C.Muted)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    vm.wipe { nav.navigate(Routes.CONFIG) { popUpTo(0) { inclusive = true } } }
                }) { Text("apagar", style = T.mono(14, color = C.Alerta)) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("cancelar", style = T.mono(14, color = C.Muted)) } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 8.dp)) {
        SectionLabel(title, Modifier.padding(bottom = 4.dp))
        content()
    }
}

@Composable
private fun ColorRow(name: String, color: Color, hex: String) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Dot(color, 10.dp)
        Text(name, style = T.sans(15), modifier = Modifier.weight(1f))
        Text(hex, style = T.mono(13, color = C.Muted))
    }
}

@Composable
private fun ActionBox(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(C.Raised).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.mono(14)) }
}
