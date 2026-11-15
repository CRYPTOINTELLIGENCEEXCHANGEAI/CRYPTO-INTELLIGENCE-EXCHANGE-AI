package com.example.data.model

const val ADMIN_EMAIL = "myfundglobal@gmail.com"

data class UserProfile(
    val email: String = "",
    val name: String = "",
    val invested: Double = 0.0,
    val totalWithdrawal: Double = 0.0,
    val interestRate: Double = 0.0,
    val withdrawableLimit: Double = 0.0,
    val phone: String = "",
    val userId: String = "",
    val photoUrl: String = "",
    val totalAmount: Double = 0.0,
    val status: String = "Not Verified"
) {
    val isAdminEligible: Boolean
        get() = email.trim().equals(ADMIN_EMAIL, ignoreCase = true)

    val isKycVerified: Boolean
        get() = status.trim().equals("Verified", ignoreCase = true)
}

data class PlanOption(
    val name: String = "",
    val amount: Double = 0.0,
    val monthlyReturn: Double = 0.0,
    val totalReturn: Double = 0.0
)

data class PlansInfo(
    val ratePercent: Double = 0.0,
    val period: String = "monthly",
    val months: Int = 12,
    val plans: List<PlanOption> = emptyList()
)

data class UserPlanItem(
    val id: String = "",
    val name: String = "",
    val amt: Double = 0.0,
    val status: String = "",
    val paid: Int = 0,
    val max: Int = 0
) {
    fun earnedSoFar(ratePercent: Double, period: String): Double {
        val divisor = if (period.trim().equals("yearly", ignoreCase = true)) 12.0 else 1.0
        return amt * (ratePercent / 100.0) / divisor * paid
    }
}

data class HistoryItem(
    val date: String = "",
    val type: String = "",
    val amount: Double = 0.0,
    val details: String = "",
    val status: String = "",
    val proof: String = ""
)

data class DepositMethod(
    val index: Int,
    val title: String,
    val address: String,
    val qrUrl: String
)

data class WithdrawMethod(
    val index: Int,
    val title: String,
    val helper: String
)

data class AppSettingsData(
    val depositMethods: List<DepositMethod> = emptyList(),
    val withdrawMethods: List<WithdrawMethod> = emptyList(),
    val raw: Map<String, String> = emptyMap()
)

data class NotificationItem(
    val title: String = "",
    val message: String = "",
    val date: String = ""
)

data class AdminPendingTx(
    val id: String = "",
    val email: String = "",
    val type: String = "",
    val amount: Double = 0.0,
    val details: String = "",
    val proof: String = "",
    val status: String = ""
)

data class AdminPendingKyc(
    val id: String = "",
    val email: String = "",
    val panNo: String = "",
    val adFront: String = "",
    val adBack: String = "",
    val panImg: String = "",
    val userName: String = "",
    val userUid: String = "",
    val userPhone: String = ""
)

data class AdminDashboardData(
    val deps: List<AdminPendingTx> = emptyList(),
    val withs: List<AdminPendingTx> = emptyList(),
    val kycs: List<AdminPendingKyc> = emptyList(),
    val refs: List<AdminPendingTx> = emptyList(),
    val totalUsers: Int = 0
)

data class AdminUserWithPlans(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val plans: List<UserPlanItem> = emptyList()
)

data class AdminStatsData(
    val metrics: List<Pair<String, String>> = emptyList()
)
