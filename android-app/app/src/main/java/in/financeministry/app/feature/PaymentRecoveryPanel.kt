package `in`.financeministry.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Navigation only: opening recovery never reads SMS or changes the ledger. */
@Composable
fun PaymentRecoveryPanel(onAdd: () -> Unit, onCapture: () -> Unit, onImport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("First check the payment date, Activity filters and Review. A hidden or unconfirmed payment may already be saved.",
            style = MaterialTheme.typography.bodyMedium)
        RecoveryAction("Add a payment manually", "No SMS needed. Check for an existing entry first to avoid counting it twice.", onAdd)
        RecoveryAction("Check SMS capture", "See permission and capture status for new bank alerts. Notifications and SMS access are separate.", onCapture)
        RecoveryAction("Import past messages", "Preview supported bank alerts from the last three months. Import requires your permission and confirmation.", onImport)
        Text("Not every bank alert can be recognized. Manual entry is always available.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecoveryAction(title: String, detail: String, onClick: () -> Unit) {
    Column {
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp),
            modifier = Modifier.heightIn(min = 48.dp)) { Text(title) }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
