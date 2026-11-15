# MyFund Global

Full-stack Android application (Kotlin + Jetpack Compose + Material 3) backed by Google Sheets via Google Apps Script (`backend/Code.gs`), with automated GitHub Actions CI/CD release APK generation (`.github/workflows/android.yml`).

## 1. Google Sheets Setup
Create a Google Spreadsheet with the following 5 tabs (or paste `backend/Code.gs` and run `setupSheets()` once to auto-create headers):

- **Users**: `Email`, `Password`, `Name`, `Invested`, `Total Withdrawal`, `Interest (%)`, `Withdrawable Limit`, `Phone`, `User ID`, `PhotoURL`, `Total Amount`, `Status`, `Notification`, `ReferredBy`, `Terms_Consent_Date`
- **Transactions**: `Date`, `Email`, `Type`, `Amount`, `Details`, `Status`, `Proof`
- **Verification**: `Date`, `Email`, `PAN`, `Front`, `Back`, `Selfie`
- **User_Plans**: `PlanID`, `UID`, `Email`, `PlanName`, `Amount`, `Status`, `PaidReturns`, `MaxReturns`, `Date`
- **Admin_Settings**: `Key`, `Value`

## 2. Deploy Google Apps Script API
1. Open **Extensions -> Apps Script** inside your Google Sheet.
2. Replace the editor contents with `backend/Code.gs`.
3. Run `setupSheets` once to initialize headers and default `Admin_Settings`.
4. Click **Deploy -> New deployment -> Web app** (`Execute as: Me`, `Who has access: Anyone`).
5. Copy the generated Web App URL (`https://script.google.com/macros/s/.../exec`) and paste it into the **Sheet API Endpoint** dialog in the app's Login screen (or set `APPS_SCRIPT_URL` in `app/build.gradle.kts`).

## 3. Admin Role
Log in or sign up with `myfundglobal@gmail.com` to automatically unlock the **Admin** tab for managing pending Transactions, KYC Verifications, and User Balances.

## 4. GitHub Actions CI/CD
Push to `main` (or trigger manually via `workflow_dispatch`) to run `.github/workflows/android.yml`, which runs unit tests, builds `MyFundGlobal-debug.apk` without requiring any GitHub secrets, and attaches it as both a workflow artifact and a GitHub Release.
