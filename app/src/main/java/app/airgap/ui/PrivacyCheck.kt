package app.airgap.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.autofill.AutofillManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.security.MessageDigest

/** One permission Airgap holds, in plain words. */
data class PermissionLine(val name: String, val meaning: String, val granted: Boolean)

/**
 * Facts about the installed app, read from Android at the moment the screen opens,
 * so the screen shows what the system enforces, not what Airgap claims.
 */
data class PrivacyReport(
    val permissions: List<PermissionLine>,
    val networkPermissions: List<String>,
    val backupAllowed: Boolean,
    val debuggable: Boolean,
    val signingSha256: String?,
    val versionName: String,
    val isAutofillService: Boolean,
) {
    val noNetworkAccess: Boolean get() = networkPermissions.isEmpty()

    companion object {
        private val NETWORK_PERMISSIONS = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
        )

        fun from(context: Context): PrivacyReport {
            val pm = context.packageManager
            val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES
            val info: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, flags)
            }
            val requested = info.requestedPermissions.orEmpty()
            val grantFlags = info.requestedPermissionsFlags ?: IntArray(requested.size)
            val permissions = requested.mapIndexed { i, name ->
                PermissionLine(name, meaningOf(name), grantFlags.getOrElse(i) { 0 } and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0)
            }
            val appFlags = context.applicationInfo.flags
            val certificate = info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            return PrivacyReport(
                permissions = permissions,
                networkPermissions = requested.filter { it in NETWORK_PERMISSIONS },
                backupAllowed = appFlags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0,
                debuggable = appFlags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
                signingSha256 = certificate?.let { bytes ->
                    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(":") { "%02X".format(it) }
                },
                versionName = info.versionName.orEmpty(),
                isAutofillService = context.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true,
            )
        }

        private fun meaningOf(permission: String): String = when {
            permission == "android.permission.USE_BIOMETRIC" -> "Unlock with fingerprint or face"
            permission == "android.permission.USE_FINGERPRINT" -> "Older fingerprint permission, added by Android's biometric library"
            permission == "android.permission.NFC" -> "Read a card you tap on the phone (radio range of a few centimetres)"
            permission == "android.permission.POST_NOTIFICATIONS" -> "Show bill and expiry reminders"
            permission == "android.permission.RECEIVE_BOOT_COMPLETED" -> "Set reminders again after the phone restarts"
            permission.endsWith("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") -> "Keeps Airgap's own internal messages private (added by AndroidX)"
            permission in NETWORK_PERMISSIONS -> "Network access"
            else -> permission.substringAfterLast('.')
        }
    }
}

/** Shows the [PrivacyReport], with ways to check the same facts outside Airgap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyCheckScreen(report: PrivacyReport, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy check") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                color = if (report.noNetworkAccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                contentColor = if (report.noNetworkAccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (report.noNetworkAccess) "✓ No internet access" else "✗ Network permission found",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        if (report.noNetworkAccess) {
                            "Airgap has no internet or network permission. Android enforces this: an app without it " +
                                "can't open any network connection, so your cards can't be sent anywhere."
                        } else {
                            "This build asks for: ${report.networkPermissions.joinToString()}. It isn't a genuine Airgap build."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Section("Checked just now, from Android") {
                Check(report.noNetworkAccess, "No internet or network permission")
                Check(!report.backupAllowed, if (report.backupAllowed) "Backups allowed" else "Backups turned off")
                Check(
                    !report.debuggable,
                    if (report.debuggable) "Debuggable: this is a test build, not for real cards" else "Not debuggable: other tools can't inspect it",
                )
                Text(
                    if (report.isAutofillService) {
                        "Airgap is your autofill service. It fills card fields only after your fingerprint."
                    } else {
                        "Airgap isn't your autofill service."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Section("Permissions Airgap holds") {
                report.permissions.forEach { permission ->
                    Column {
                        Text(permission.meaning, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${permission.name.substringAfterLast('.')} · ${if (permission.granted) "allowed" else "not allowed"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Section("This copy of Airgap") {
                Text("Version ${report.versionName}", style = MaterialTheme.typography.bodyMedium)
                report.signingSha256?.let { fingerprint ->
                    Text("Signing certificate (SHA-256)", style = MaterialTheme.typography.bodyMedium)
                    Text(fingerprint, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    Text(
                        "Compare it with the fingerprint published in the project's README. A different one means a different publisher.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Section("Check it yourself") {
                Text(
                    "Settings → Apps → Airgap → Permissions lists the same permissions as above.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "The source code is public at github.com/ThePubDoc/airgap, and builds are reproducible: " +
                        "building it yourself gives a byte-identical app.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Check(passed: Boolean, text: String) {
    Row {
        Text(
            if (passed) "✓" else "✗",
            color = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
