package org.moblized.nfttag

import android.content.ActivityNotFoundException
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.moblized.nfttag.ui.NftTagTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private var nfcAdapter: NfcAdapter? = null

    private val pairedUid = mutableStateOf<String?>(null)
    private val pairingArmed = mutableStateOf(false)
    private val themeMode = mutableStateOf(ThemeMode.SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        pairedUid.value = prefs.pairedUid
        themeMode.value = prefs.themeMode
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            NftTagTheme(themeMode.value) {
                AppScreen(
                    prefs = prefs,
                    pairedUid = pairedUid.value,
                    pairingArmed = pairingArmed.value,
                    onArmPairing = { pairingArmed.value = true },
                    onForgetTag = {
                        prefs.pairedUid = null
                        pairedUid.value = null
                    },
                    onThemeChange = {
                        prefs.themeMode = it
                        themeMode.value = it
                    },
                    onTrigger = { triggerChroma(prefs.presetChoice, prefs.sleepMinutes) },
                )
            }
        }

        handleNfcIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter ?: return
        val pi = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                android.app.PendingIntent.FLAG_MUTABLE else 0
        )
        runCatching { adapter.enableForegroundDispatch(this, pi, null, null) }
    }

    override fun onPause() {
        super.onPause()
        runCatching { nfcAdapter?.disableForegroundDispatch(this) }
    }

    private fun handleNfcIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_NDEF_DISCOVERED
        ) return

        @Suppress("DEPRECATION")
        val tag: Tag? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            else intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)

        val uid = tag?.id?.toHex() ?: return
        onTagRead(uid)
    }

    private fun onTagRead(uid: String) {
        if (pairingArmed.value) {
            prefs.pairedUid = uid
            pairedUid.value = uid
            pairingArmed.value = false
            toast("Paired this tag as your trigger")
            return
        }

        val paired = prefs.pairedUid
        if (prefs.lockToTag && paired != null && !paired.equals(uid, ignoreCase = true)) {
            toast("Not your paired tag — ignoring")
            return
        }

        triggerChroma(prefs.presetChoice, prefs.sleepMinutes)
    }

    /** Hand off to ChromaNoise's no-UI entry point to actually make the sound. */
    private fun triggerChroma(preset: String, minutes: Int) {
        val i = Intent().apply {
            setClassName(CHROMA_PKG, CHROMA_TRIGGER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (preset.isNotEmpty()) putExtra(EXTRA_PRESET, preset)
            if (minutes > 0) putExtra(EXTRA_TIMER, minutes)
        }
        try {
            startActivity(i)
        } catch (_: ActivityNotFoundException) {
            toast("ChromaNoise isn't installed")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val CHROMA_PKG = "app.chromanoise"
        const val CHROMA_TRIGGER = "app.chromanoise.playback.PlayTriggerActivity"
        const val EXTRA_PRESET = "app.chromanoise.extra.PRESET"
        const val EXTRA_TIMER = "app.chromanoise.extra.TIMER_MINUTES"
    }
}

private fun ByteArray.toHex(): String =
    joinToString("") { "%02X".format(it) }

private val SLEEP_OPTIONS = listOf(0, 15, 30, 45, 60, 90)

// "" = whatever ChromaNoise played last; the rest are its built-in presets.
private val PRESETS = listOf("" to "Last used") +
    listOf("White", "Pink", "Brown", "Grey", "Blue", "Violet").map { it to it }

@Composable
private fun AppScreen(
    prefs: Prefs,
    pairedUid: String?,
    pairingArmed: Boolean,
    onArmPairing: () -> Unit,
    onForgetTag: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onTrigger: () -> Unit,
) {
    var preset by remember { mutableStateOf(prefs.presetChoice) }
    var sleep by remember { mutableIntStateOf(prefs.sleepMinutes) }
    var lockToTag by remember { mutableStateOf(prefs.lockToTag) }

    Scaffold { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("nft-tag", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Tap your bedside tag to start white noise in ChromaNoise. The screen has to " +
                    "be on, so tap as you set the phone down to charge.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(
                onClick = onTrigger,
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text("Test trigger", style = MaterialTheme.typography.titleMedium)
            }

            // Which sound ChromaNoise should play
            Section("Play") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { (value, label) ->
                        FilterChip(
                            selected = preset == value,
                            onClick = { preset = value; prefs.presetChoice = value },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // Sleep timer handed to ChromaNoise
            Section("Sleep timer") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SLEEP_OPTIONS.forEach { m ->
                        FilterChip(
                            selected = sleep == m,
                            onClick = { sleep = m; prefs.sleepMinutes = m },
                            label = { Text(if (m == 0) "Off" else "$m m") },
                        )
                    }
                }
            }

            // Pairing
            Section("Bedside tag") {
                Text(
                    pairedUid?.let { "Paired: $it" } ?: "No tag paired yet",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onArmPairing) {
                        Text(if (pairingArmed) "Tap your tag now…" else "Pair a tag")
                    }
                    if (pairedUid != null) {
                        TextButton(onClick = onForgetTag) { Text("Forget") }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Only my paired tag triggers", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = lockToTag,
                        onCheckedChange = { lockToTag = it; prefs.lockToTag = it },
                    )
                }
            }

            // Theme
            Section("Theme") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { t ->
                        FilterChip(
                            selected = prefs.themeMode == t,
                            onClick = { onThemeChange(t) },
                            label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
