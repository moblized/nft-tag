package org.moblized.nfttag

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.moblized.nfttag.ui.NftTagTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private var nfcAdapter: NfcAdapter? = null

    private val pairedUid = mutableStateOf<String?>(null)
    private val pairingArmed = mutableStateOf(false)
    private val themeMode = mutableStateOf(ThemeMode.SYSTEM)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        pairedUid.value = prefs.pairedUid
        themeMode.value = prefs.themeMode
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        maybeRequestNotifications()

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
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE else 0
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

        NoiseService.start(this, prefs.noiseColor, prefs.gain, prefs.sleepMinutes)
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

private fun ByteArray.toHex(): String =
    joinToString("") { "%02X".format(it) }

private val SLEEP_OPTIONS = listOf(0, 15, 30, 45, 60, 90)

@Composable
private fun AppScreen(
    prefs: Prefs,
    pairedUid: String?,
    pairingArmed: Boolean,
    onArmPairing: () -> Unit,
    onForgetTag: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    val running = NoiseService.running

    var color by remember { mutableStateOf(prefs.noiseColor) }
    var gain by remember { mutableFloatStateOf(prefs.gain) }
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
                "Tap your bedside tag to start white noise. Screen has to be on, " +
                    "so tap as you set the phone down to charge.",
                style = MaterialTheme.typography.bodyMedium,
            )

            // Play / stop
            Button(
                onClick = {
                    if (running) NoiseService.stop(context)
                    else NoiseService.start(context, color, gain, sleep)
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text(if (running) "Stop" else "Play now", style = MaterialTheme.typography.titleMedium)
            }

            // Noise color
            Section("Sound") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NoiseColor.entries.forEach { c ->
                        FilterChip(
                            selected = color == c,
                            onClick = {
                                color = c
                                prefs.noiseColor = c
                                if (running) NoiseService.start(context, c, gain, sleep)
                            },
                            label = { Text(c.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }

            // Volume
            Section("Volume") {
                Slider(
                    value = gain,
                    onValueChange = { gain = it },
                    onValueChangeFinished = {
                        prefs.gain = gain
                        if (running) NoiseService.start(context, color, gain, sleep)
                    },
                )
            }

            // Sleep timer
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
