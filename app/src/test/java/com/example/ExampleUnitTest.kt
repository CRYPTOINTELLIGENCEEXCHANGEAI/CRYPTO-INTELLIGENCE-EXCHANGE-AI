package com.example

import com.example.data.model.UserProfile
import com.example.data.remote.SafeDoubleAdapter
import com.example.data.remote.SafeIntAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private val moshi: Moshi = Moshi.Builder()
        .add(SafeDoubleAdapter())
        .add(SafeIntAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun adminRoleDetection_matchesExactAdminEmailCaseInsensitive() {
        val admin = UserProfile(email = "  MYFUNDGLOBAL@gmail.com ")
        val standardUser = UserProfile(email = "investor@example.com")

        assertTrue(admin.computedIsAdmin)
        assertFalse(standardUser.computedIsAdmin)
    }

    @Test
    fun userProfileDeserialization_handlesGoogleSheetsColumnNamesAndBlankNumbers() {
        val json = """
            {
              "Email": "investor@example.com",
              "Name": "Alex Mercer",
              "Invested": "",
              "Total Withdrawal": 500.5,
              "Interest (%)": "4.25",
              "Withdrawable Limit": 1250,
              "Phone": "+15550199",
              "User ID": "MFG-104829",
              "PhotoURL": "",
              "Total Amount": 3750.75,
              "Status": "Verified",
              "Notification": "Returns credited",
              "ReferredBy": "MFG-100001",
              "Terms_Consent_Date": "2026-11-15T00:00:00Z"
            }
        """.trimIndent()

        val adapter = moshi.adapter(UserProfile::class.java)
        val parsed = adapter.fromJson(json)!!

        assertEquals("investor@example.com", parsed.email)
        assertEquals("Alex Mercer", parsed.name)
        assertEquals(0.0, parsed.invested, 0.001)
        assertEquals(500.5, parsed.totalWithdrawal, 0.001)
        assertEquals(4.25, parsed.interestRate, 0.001)
        assertEquals(1250.0, parsed.withdrawableLimit, 0.001)
        assertEquals(3750.75, parsed.totalAmount, 0.001)
        assertEquals("Verified", parsed.status)
        assertFalse(parsed.computedIsAdmin)
    }
}
