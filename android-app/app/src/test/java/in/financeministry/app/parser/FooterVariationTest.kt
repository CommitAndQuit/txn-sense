package `in`.financeministry.app.parser

import `in`.financeministry.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

class FooterVariationTest {
    private val debit = "ICICI Bank Acct XX008 debited for Rs 62.00 on 01-Oct-26; testshop o credited. UPI:100000000008."
    private val refund = "Refund of Rs.2751.19 from Test Store received on your Federal Bank One Credit Card. This will be adjusted against your outstanding dues."
    private fun parse(body: String) = RuleBasedFinancialSmsParser().parse(IncomingSms("AD-EXBANK", 0, body))

    @Test fun dispute_phone_footer_does_not_require_sms_block_instruction() {
        val result = parse("$debit Call 18002662 for dispute.")
        assertEquals(ParseDecision.Record, result.decision)
        assertEquals(6200L, result.amountMinor)
        assertEquals(Direction.Debit, result.direction)
        assertEquals(TransactionStatus.Successful, result.status)
    }

    @Test fun completed_refund_accepts_bounded_app_details_footer() {
        val result = parse("$refund Check details in your app.")
        assertEquals(ParseDecision.Record, result.decision)
        assertEquals(275119L, result.amountMinor)
        assertEquals(Direction.Credit, result.direction)
        assertEquals(TransactionType.Refund, result.transactionType)
        assertEquals(TransactionStatus.Successful, result.status)
    }

    @Test fun added_movement_and_unreceived_refunds_cannot_become_automatic_records() {
        listOf("$debit Call 18002662 for dispute. Your account credited INR 50.",
            "$refund Check details in your app. Another payment is pending.",
            refund.replace("received", "not yet received") + " Check details in your app.").forEach {
            assertNotEquals(ParseDecision.Record, parse(it).decision)
        }
    }
}
