package io.github.pobsteta.ramulus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.pobsteta.ramulus.data.journal.HorlogesAndroid
import io.github.pobsteta.ramulus.data.journal.JournalRepository
import io.github.pobsteta.ramulus.data.journal.RamulusDatabase
import io.github.pobsteta.ramulus.data.sauvegarde.Destination
import io.github.pobsteta.ramulus.data.sauvegarde.SauvegardeRepository
import io.github.pobsteta.ramulus.tournee.AppRamulus
import io.github.pobsteta.ramulus.tournee.ControleurTournee
import io.github.pobsteta.ramulus.tournee.EchangeReel
import io.github.pobsteta.ramulus.tournee.ExportDocuments
import io.github.pobsteta.ramulus.tournee.JournalAndroid
import io.github.pobsteta.ramulus.tournee.StockOperateurPrefs
import io.github.pobsteta.ramulus.tournee.TourneeViewModel
import io.github.pobsteta.ramulus.ui.theme.RamulusTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: TourneeViewModel by viewModels {
        viewModelFactory {
            initializer {
                val ctx = applicationContext
                val horloges = HorlogesAndroid(ctx)
                val version = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
                val db = RamulusDatabase.ouvrir(ctx)
                val journal = JournalRepository(db, horloges)
                val sauvegarde = SauvegardeRepository(db, journal, Destination.pourCetAppareil(ctx), horloges, version)
                TourneeViewModel(
                    ControleurTournee(
                        racine = File(ctx.filesDir, "tournees"),
                        echange = EchangeReel,
                        journal = JournalAndroid(journal, sauvegarde),
                        stock = StockOperateurPrefs(ctx),
                        export = ExportDocuments(ctx),
                        versionRamulus = version,
                    ),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RamulusTheme {
                AppRamulus(vm)
            }
        }
    }
}
