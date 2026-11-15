package com.example

import com.example.data.model.UserPlanItem
import com.example.data.model.UserProfile
import com.example.data.repository.MyFundRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun adminEligibility_matchesExactAdminEmailCaseInsensitive() {
        val admin = UserProfile(email = "  MYFUNDGLOBAL@gmail.com ")
        val standardUser = UserProfile(email = "xyztraderboss@gmail.com")

        assertTrue(admin.isAdminEligible)
        assertFalse(standardUser.isAdminEligible)
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
    fun flatLoginJson_handlesFriendlyErrorAndNestedDataWrapper() {
        val failureJson = """{"success":false,"error":"Invalid User ID/Email or Password"}"""
        val failureResult = MyFundRepository.parseUserProfileJson(failureJson)
        assertTrue(failureResult.isFailure)
        assertEquals("Invalid User ID/Email or Password", failureResult.exceptionOrNull()?.message)

        val wrappedJson = """{"success":true,"user":{"email":"a@b.com","name":"Test","inv":100}}"""
        val wrappedResult = MyFundRepository.parseUserProfileJson(wrappedJson)
        assertTrue(wrappedResult.isSuccess)
        assertEquals("a@b.com", wrappedResult.getOrThrow().email)
        assertEquals(100.0, wrappedResult.getOrThrow().invested, 0.001)
    }

    @Test
    fun userPlanItem_earnedSoFar_matchesYearlyAndMonthlyFormulas() {
        val plan = UserPlanItem(id = "P1", name = "Gold", amt = 1200.0, status = "Active", paid = 3, max = 12)
        val yearlyEarned = plan.earnedSoFar(ratePercent = 12.0, period = "yearly")
        val monthlyEarned = plan.earnedSoFar(ratePercent = 5.0, period = "monthly")

        assertEquals(36.0, yearlyEarned, 0.001)
        assertEquals(180.0, monthlyEarned, 0.001)
    }
}
