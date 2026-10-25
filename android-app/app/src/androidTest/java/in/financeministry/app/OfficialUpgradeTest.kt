package `in`.financeministry.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in only: run on the disposable official-signer upgrade emulator. */
class OfficialUpgradeTest {
    @Test fun official_apk_upgrade_preserves_edited_record_and_keys() = runBlocking {
        val stage = InstrumentationRegistry.getArguments().getString("upgradeStage")
        val expanded = InstrumentationRegistry.getArguments().getString("upgradeFixtures") == "expanded"
        assumeTrue("Requires disposable signed upgrade device", stage in listOf("seed", "verify"))
        val app = ApplicationProvider.getApplicationContext<FinanceMinistryApp>()
        val repository = app.container.repository
        val id = "synthetic-official-upgrade"
        if (stage == "seed") {
            assertNull(repository.get(id))
            val input = ManualInput("42.00", Direction.Debit, 1788600000000L, TransactionType.Other)
            repository.save(input, null, id)
            repository.save(input.copy(amount = "43.00"), id)
        }
        if (stage == "seed" && expanded) {
            repository.addCategory("Upgrade checks")
            val member = repository.addLocalMember("Upgrade Alice")
            val source = repository.addPaymentSource("Upgrade account", "Bank account", Channel.UPI)
            val sharedId = repository.save(ManualInput("900", Direction.Debit, 1788600000000L,
                TransactionType.Other, label = "Upgrade dinner", notes = "Synthetic upgrade note",
                category = "Upgrade checks", ownership = SpendingOwnership.Group, groupLabel = "Upgrade trip",
                personalShare = "300", paymentSourceId = source.id, channel = Channel.UPI), null, "synthetic-upgrade-shared")
            repository.saveGroupSplit(sharedId, 30000, listOf(MemberShareInput(member.id, 60000)), repository.get(sharedId)!!.updatedAt)
            repository.recordRepayment(sharedId, "200", 1788600001000L, "Upgrade Alice", memberId = member.id)
            repository.preferences.edit().putString("preferred_name", "Upgrade Tester").putString("appearance", "Dark").commit()
        }
        val row = repository.get(id)!!
        assertEquals(4300L, row.amountMinor)
        assertTrue(row.isUserCorrected)
        if (expanded) {
        val shared = repository.get("synthetic-upgrade-shared")!!
        assertEquals(90000L, shared.amountMinor)
        assertEquals("Synthetic upgrade note", shared.userNotes)
        assertEquals("Upgrade checks", shared.category)
        assertEquals(20000L, shared.repaidMinor)
        assertEquals(40000L, RepaymentAccounting.owed(shared))
        assertEquals(60000L, repository.groupSharesFor(shared.id).single().amountMinor)
        assertEquals(20000L, repository.repaymentsFor(shared.id).single().amountMinor)
        assertEquals("Upgrade Alice", repository.localMembers().single { it.id == repository.groupSharesFor(shared.id).single().memberId }.name)
        assertEquals("Upgrade account", repository.paymentSources().single { it.id == shared.paymentSourceId }.nickname)
        assertTrue(repository.categories().contains("Upgrade checks"))
        assertEquals("Upgrade Tester", repository.preferences.getString("preferred_name", null))
        assertEquals("Dark", repository.preferences.getString("appearance", null))
        }
        repository.close()
        if (stage == "verify") {
            val db = FinanceDatabase.open(app, DeviceSecrets(app).databasePassphrase(true))
            try {
                assertTrue(db.transactions().corrections(id).any { it.fieldName == "amountMinor" && it.previousValue == "4200" && it.newValue == "4300" })
                assertNull(db.transactions().get(id)!!.referenceHash)
                assertEquals(9, db.openHelper.readableDatabase.version)
            } finally { db.close() }
            assertEquals(4300L, repository.get(id)!!.amountMinor)
            repository.close()
        }
    }
}
