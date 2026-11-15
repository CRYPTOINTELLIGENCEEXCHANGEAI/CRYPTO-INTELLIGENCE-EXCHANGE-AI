package com.example

import com.example.data.model.UserProfile
import com.example.data.remote.SafeDoubleAdapter
import com.example.data.remote.SafeIntAdapter
import com.example.data.repository.MyFundRepository
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
    fun flatLoginJson_parsesDirectlyWithMixedIntAndDoubleAndMissingFields() {
        val flatJson = """
            {"success":true,"email":"xyztraderboss@gmail.com","name":"KISHAN SAINI","inv":500,"wit":0,"roi":5,"lim":0,"phone":"+917982202196","uid":"CIE-2735","photo":"https://ik.imagekit.io/demo/avatar.png","balance":1250.5,"kycStatus":"Not Verified"}
        """.trimIndent()

        val result = MyFundRepository.parseUserProfileJson(flatJson)
        assertTrue(result.isSuccess)
        val user = result.getOrThrow()
        assertEquals("xyztraderboss@gmail.com", user.email)
        assertEquals("KISHAN SAINI", user.name)
        assertEquals(500.0, user.invested, 0.001)
        assertEquals(0.0, user.totalWithdrawal, 0.001)
        assertEquals(5.0, user.interestRate, 0.001)
        assertEquals(0.0, user.withdrawableLimit, 0.001)
        assertEquals("+917982202196", user.phone)
        assertEquals("CIE-2735", user.userId)
        assertEquals(1250.5, user.totalAmount, 0.001)
        assertEquals("Not Verified", user.status)
    }

    @Test
    fun flatLoginJson_handlesFailureErrorAndNestedDataWrapper() {
        val failureJson = """{"success":false,"error":"Invalid User ID/Email or Password"}"""
        val failureResult = MyFundRepository.parseUserProfileJson(failureJson)
        assertTrue(failureResult.isFailure)
        assertEquals("Invalid User ID/Email or Password", failureResult.exceptionOrNull()?.message)

        val wrappedJson = """{"success":true,"user":{"Email":"a@b.com","Name":"Test","Invested":100}}"""
        val wrappedResult = MyFundRepository.parseUserProfileJson(wrappedJson)
        assertTrue(wrappedResult.isSuccess)
        assertEquals("a@b.com", wrappedResult.getOrThrow().email)
        assertEquals(100.0, wrappedResult.getOrThrow().invested, 0.001)
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
