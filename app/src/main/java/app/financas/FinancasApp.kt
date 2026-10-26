package app.financas

import android.app.Application
import app.financas.data.AppDatabase
import app.financas.data.Repository

class FinancasApp : Application() {
    val repository: Repository by lazy { Repository(AppDatabase.get(this)) }
}
