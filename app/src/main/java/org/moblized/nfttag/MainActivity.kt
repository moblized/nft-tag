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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    pairedUid = pairedUid.value,
                    pairingArmed = pairingArmed.value,
                    lockToTag = prefs.lockToTag,
                    themeMode = themeMode.value,
                    onArmPairing = { pairingArmed.value = true },
                    onForgetTag = {
                        prefs.pairedUid = null
                        pairedUid.value = null
                    },
                    onLockChange = { prefs.lockToTag = it },
                    onThemeChange = {
                        prefs.themeMode = it
                        themeMode.value = it
                    },
                    onTrigger = { triggerChroma() },
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

        triggerChroma()
    }

    /**
     * Hand off to ChromaNoise's no-UI entry point. No extras — ChromaNoise plays whatever
     * its own Tag & schedule settings say, so the sound lives in one place.
     */
    private fun triggerChroma() {
        val i = Intent().apply {
            setClassName(CHROMA_PKG, CHROMA_TRIGGER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
    }
}

private fun ByteArray.toHex(): String =
    joinToString("") { "%02X".format(it) }

@Composable
private fun AppScreen(
    pairedUid: String?,
    pairingArmed: Boolean,
    lockToTag: Boolean,
    themeMode: ThemeMode,
    onArmPairing: () -> Unit,
    onForgetTag: () -> Unit,
    onLockChange: (Boolean) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onTrigger: () -> Unit,
) {
    var lock by remember { mutableStateOf(lockToTag) }

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
                "Tap your bedside tag to start ChromaNoise. What it plays and when it stops " +
                    "is set in ChromaNoise (Tag & schedule). The screen has to be on, so tap as " +
                    "you set the phone down to charge.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(
                onClick = onTrigger,
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text("Test trigger", style = MaterialTheme.typography.titleMedium)
            }

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
                        checked = lock,
                        onCheckedChange = { lock = it; onLockChange(it) },
                    )
                }
            }

            Section("Theme") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { t ->
                        FilterChip(
                            selected = themeMode == t,
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
