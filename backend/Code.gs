/**
 * MyFund Global — Google Apps Script REST Backend
 *
 * Exposes a Google Spreadsheet as a JSON API for the MyFund Global mobile app.
 * Sheets required (exact headers):
 * 1. Users: Email, Password, Name, Invested, Total Withdrawal, Interest (%), Withdrawable Limit, Phone, User ID, PhotoURL, Total Amount, Status, Notification, ReferredBy, Terms_Consent_Date
 * 2. Transactions: Date, Email, Type, Amount, Details, Status, Proof
 * 3. Verification: Date, Email, PAN, Front, Back, Selfie
 * 4. User_Plans: PlanID, UID, Email, PlanName, Amount, Status, PaidReturns, MaxReturns, Date
 * 5. Admin_Settings: Key, Value
 *
 * Deployment:
 * 1. Open Extensions -> Apps Script in your Google Sheet.
 * 2. Paste this file, save, and run `setupSheets()` once to initialize headers & Admin_Settings.
 * 3. Deploy -> New deployment -> Web app -> Execute as: Me -> Who has access: Anyone.
 * 4. Copy the Web App URL into the MyFund Global app (Backend Configuration) or BuildConfig.APPS_SCRIPT_URL.
 */

const ADMIN_EMAIL = 'myfundglobal@gmail.com';
const DRIVE_FOLDER_NAME = 'MyFund_Global_Uploads';

const SHEET_HEADERS = {
  Users: [
    'Email', 'Password', 'Name', 'Invested', 'Total Withdrawal',
    'Interest (%)', 'Withdrawable Limit', 'Phone', 'User ID', 'PhotoURL',
    'Total Amount', 'Status', 'Notification', 'ReferredBy', 'Terms_Consent_Date'
  ],
  Transactions: ['Date', 'Email', 'Type', 'Amount', 'Details', 'Status', 'Proof'],
  Verification: ['Date', 'Email', 'PAN', 'Front', 'Back', 'Selfie'],
  User_Plans: ['PlanID', 'UID', 'Email', 'PlanName', 'Amount', 'Status', 'PaidReturns', 'MaxReturns', 'Date'],
  Admin_Settings: ['Key', 'Value']
};

/**
 * Run once manually from the Apps Script Editor to create missing sheets & headers.
 */
function setupSheets() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  Object.keys(SHEET_HEADERS).forEach(function (name) {
    let sheet = ss.getSheetByName(name);
    if (!sheet) {
      sheet = ss.insertSheet(name);
    }
    if (sheet.getLastRow() === 0) {
      sheet.appendRow(SHEET_HEADERS[name]);
      sheet.getRange(1, 1, 1, SHEET_HEADERS[name].length).setFontWeight('bold');
      sheet.setFrozenRows(1);
    }
  });

  const settingsSheet = ss.getSheetByName('Admin_Settings');
  if (settingsSheet.getLastRow() <= 1) {
    settingsSheet.appendRow(['latest_version_code', '1']);
    settingsSheet.appendRow(['latest_version_name', '1.0']);
    settingsSheet.appendRow(['min_version_code', '1']);
    settingsSheet.appendRow(['force_update', 'false']);
    settingsSheet.appendRow(['update_url', 'https://github.com/your-org/myfund-global/releases/latest']);
    settingsSheet.appendRow(['update_message', 'A new version of MyFund Global is available with performance and security improvements.']);
  }
}

function doGet(e) {
  try {
    const params = (e && e.parameter) ? e.parameter : {};
    const action = params.action || '';

    switch (action) {
      case 'checkVersion':
        return jsonResponse(true, 'Version fetched', getAppVersionSettings());
      case 'getUser':
        return handleGetUser(params.email);
      case 'getTransactions':
        return handleGetTransactions(params.email, params.adminEmail, params.status);
      case 'getUserPlans':
        return handleGetUserPlans(params.email);
      case 'getPendingVerifications':
        return handleGetVerifications(params.adminEmail);
      case 'getAllUsers':
        return handleGetAllUsers(params.adminEmail);
      default:
        return jsonResponse(false, 'Unknown GET action: ' + action);
    }
  } catch (err) {
    return jsonResponse(false, 'Server error: ' + err.message);
  }
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(25000);
    const body = parseBody(e);
    const action = body.action || '';

    switch (action) {
      case 'login':
        return handleLogin(body);
      case 'signup':
        return handleSignup(body);
      case 'submitTransaction':
        return handleSubmitTransaction(body);
      case 'submitKyc':
        return handleSubmitKyc(body);
      case 'updateTransactionStatus':
        return handleUpdateTransactionStatus(body);
      case 'updateKycStatus':
        return handleUpdateKycStatus(body);
      case 'updateUserBalance':
        return handleUpdateUserBalance(body);
      default:
        return jsonResponse(false, 'Unknown POST action: ' + action);
    }
  } catch (err) {
    return jsonResponse(false, 'Server error: ' + err.message);
  } finally {
    try { lock.releaseLock(); } catch (_) {}
  }
}

/* ==================== AUTHENTICATION ==================== */

function handleLogin(body) {
  const rawIdentifier = String(body.email || body.identifier || body.userId || '').trim();
  const identifierLower = rawIdentifier.toLowerCase();
  const password = String(body.password || '');
  if (!identifierLower || !password) {
    return jsonResponse(false, 'Email/User ID and password are required.');
  }

  const found = findRowByColumn('Users', 'Email', identifierLower, true)
    || findRowByColumn('Users', 'User ID', rawIdentifier, true);
  if (!found) {
    return jsonResponse(false, 'No account found with this Email or User ID.');
  }

  const storedPassword = String(found.record['Password'] || '');
  if (storedPassword !== password) {
    return jsonResponse(false, 'Incorrect password.');
  }

  const sanitized = sanitizeUser(found.record);
  const resolvedEmail = normalizeEmail(found.record['Email']);
  sanitized.isAdmin = (resolvedEmail === ADMIN_EMAIL);
  return jsonResponse(true, 'Login successful', sanitized);
}

function handleSignup(body) {
  const email = normalizeEmail(body.email);
  const password = String(body.password || '');
  const name = String(body.name || '').trim();
  const phone = String(body.phone || '').trim();
  const referredBy = String(body.referredBy || '').trim();

  if (!email || !password || !name || !phone) {
    return jsonResponse(false, 'Name, email, phone, and password are required.');
  }

  const existing = findRowByColumn('Users', 'Email', email, true);
  if (existing) {
    return jsonResponse(false, 'An account with this email already exists.');
  }

  const sheet = getSheet('Users');
  const headers = getHeaders(sheet);
  const userId = 'MFG-' + Math.floor(100000 + Math.random() * 900000);
  const nowIso = new Date().toISOString();

  const newUserObj = {
    'Email': email,
    'Password': password,
    'Name': name,
    'Invested': 0,
    'Total Withdrawal': 0,
    'Interest (%)': 0,
    'Withdrawable Limit': 0,
    'Phone': phone,
    'User ID': userId,
    'PhotoURL': '',
    'Total Amount': 0,
    'Status': 'Unverified',
    'Notification': 'Welcome to MyFund Global! Complete KYC to unlock full limits.',
    'ReferredBy': referredBy,
    'Terms_Consent_Date': nowIso
  };

  sheet.appendRow(headers.map(function (h) { return newUserObj[h] !== undefined ? newUserObj[h] : ''; }));
  const sanitized = sanitizeUser(newUserObj);
  sanitized.isAdmin = (email === ADMIN_EMAIL);
  return jsonResponse(true, 'Account created successfully', sanitized);
}

/* ==================== USER ENDPOINTS ==================== */

function handleGetUser(emailParam) {
  const email = normalizeEmail(emailParam);
  if (!email) return jsonResponse(false, 'Email is required.');
  const found = findRowByColumn('Users', 'Email', email, true);
  if (!found) return jsonResponse(false, 'User not found.');
  const sanitized = sanitizeUser(found.record);
  sanitized.isAdmin = (email === ADMIN_EMAIL);
  return jsonResponse(true, 'User fetched', sanitized);
}

function handleGetTransactions(emailParam, adminEmailParam, statusFilter) {
  const adminEmail = normalizeEmail(adminEmailParam);
  const email = normalizeEmail(emailParam);
  const isAdmin = adminEmail === ADMIN_EMAIL;

  const rows = readAllObjects('Transactions');
  const filtered = [];
  for (let i = 0; i < rows.length; i++) {
    const r = rows[i];
    const rowEmail = normalizeEmail(r['Email']);
    if (!isAdmin && email && rowEmail !== email) continue;
    if (statusFilter && String(r['Status']).toLowerCase() !== String(statusFilter).toLowerCase()) continue;
    r._rowNumber = i + 2; // 1-indexed header + 1
    filtered.push(r);
  }
  filtered.reverse();
  return jsonResponse(true, 'Transactions fetched', filtered);
}

function handleGetUserPlans(emailParam) {
  const email = normalizeEmail(emailParam);
  if (!email) return jsonResponse(false, 'Email is required.');
  const rows = readAllObjects('User_Plans').filter(function (r) {
    return normalizeEmail(r['Email']) === email;
  });
  return jsonResponse(true, 'Plans fetched', rows);
}

function handleSubmitTransaction(body) {
  const email = normalizeEmail(body.email);
  const type = String(body.type || '').trim(); // Deposit | Withdrawal
  const amount = Number(body.amount || 0);
  const details = String(body.details || '').trim();
  const proofUrlInput = String(body.proofUrl || '').trim();
  const proofBase64 = String(body.proofBase64 || '').trim();

  if (!email || !type || amount <= 0) {
    return jsonResponse(false, 'Valid email, type, and positive amount are required.');
  }

  const userFound = findRowByColumn('Users', 'Email', email, true);
  if (!userFound) {
    return jsonResponse(false, 'User account not found.');
  }

  if (type.toLowerCase() === 'withdrawal') {
    const limit = Number(userFound.record['Withdrawable Limit'] || 0);
    if (amount > limit) {
      return jsonResponse(false, 'Requested amount exceeds Withdrawable Limit (' + limit + ').');
    }
  }

  let finalProofUrl = proofUrlInput;
  if (proofBase64) {
    finalProofUrl = uploadBase64ToDrive(proofBase64, 'proof_' + email + '_' + Date.now() + '.jpg');
  }

  const sheet = getSheet('Transactions');
  const headers = getHeaders(sheet);
  const nowStr = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'yyyy-MM-dd HH:mm:ss');

  const rowObj = {
    'Date': nowStr,
    'Email': email,
    'Type': type,
    'Amount': amount,
    'Details': details,
    'Status': 'Pending',
    'Proof': finalProofUrl
  };

  sheet.appendRow(headers.map(function (h) { return rowObj[h] !== undefined ? rowObj[h] : ''; }));
  rowObj._rowNumber = sheet.getLastRow();
  return jsonResponse(true, type + ' request submitted for review.', rowObj);
}

function handleSubmitKyc(body) {
  const email = normalizeEmail(body.email);
  const pan = String(body.pan || '').trim().toUpperCase();
  if (!email || !pan) {
    return jsonResponse(false, 'Email and PAN number are required.');
  }

  const userFound = findRowByColumn('Users', 'Email', email, true);
  if (!userFound) {
    return jsonResponse(false, 'User account not found.');
  }

  const frontUrl = body.frontBase64
    ? uploadBase64ToDrive(body.frontBase64, 'kyc_front_' + email + '_' + Date.now() + '.jpg')
    : String(body.frontUrl || '');
  const backUrl = body.backBase64
    ? uploadBase64ToDrive(body.backBase64, 'kyc_back_' + email + '_' + Date.now() + '.jpg')
    : String(body.backUrl || '');
  const selfieUrl = body.selfieBase64
    ? uploadBase64ToDrive(body.selfieBase64, 'kyc_selfie_' + email + '_' + Date.now() + '.jpg')
    : String(body.selfieUrl || '');

  const verSheet = getSheet('Verification');
  const verHeaders = getHeaders(verSheet);
  const nowStr = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'yyyy-MM-dd HH:mm:ss');

  const kycObj = {
    'Date': nowStr,
    'Email': email,
    'PAN': pan,
    'Front': frontUrl,
    'Back': backUrl,
    'Selfie': selfieUrl
  };

  verSheet.appendRow(verHeaders.map(function (h) { return kycObj[h] !== undefined ? kycObj[h] : ''; }));
  updateRowFields('Users', userFound.rowNumber, { 'Status': 'Pending' });

  return jsonResponse(true, 'KYC documents submitted. Status is now Pending.', kycObj);
}

/* ==================== ADMIN ENDPOINTS ==================== */

function assertAdmin(adminEmail) {
  if (normalizeEmail(adminEmail) !== ADMIN_EMAIL) {
    throw new Error('Unauthorized: Admin privileges required.');
  }
}

function handleGetVerifications(adminEmail) {
  assertAdmin(adminEmail);
  const verifications = readAllObjects('Verification');
  const users = readAllObjects('Users');
  const statusByEmail = {};
  const nameByEmail = {};
  users.forEach(function (u) {
    const e = normalizeEmail(u['Email']);
    statusByEmail[e] = u['Status'] || 'Unverified';
    nameByEmail[e] = u['Name'] || '';
  });

  const enriched = verifications.map(function (v, idx) {
    const e = normalizeEmail(v['Email']);
    v.userStatus = statusByEmail[e] || 'Unverified';
    v.userName = nameByEmail[e] || '';
    v._rowNumber = idx + 2;
    return v;
  }).reverse();

  return jsonResponse(true, 'Verifications fetched', enriched);
}

function handleGetAllUsers(adminEmail) {
  assertAdmin(adminEmail);
  const users = readAllObjects('Users').map(function (u, idx) {
    const clean = sanitizeUser(u);
    clean._rowNumber = idx + 2;
    return clean;
  });
  return jsonResponse(true, 'Users fetched', users);
}

function handleUpdateTransactionStatus(body) {
  assertAdmin(body.adminEmail);
  const rowNumber = Number(body.rowNumber || 0);
  const newStatus = String(body.status || '').trim(); // Success | Rejected
  if (rowNumber < 2 || (newStatus !== 'Success' && newStatus !== 'Rejected')) {
    return jsonResponse(false, 'Invalid rowNumber or status.');
  }

  const txSheet = getSheet('Transactions');
  const txHeaders = getHeaders(txSheet);
  const rowValues = txSheet.getRange(rowNumber, 1, 1, txHeaders.length).getValues()[0];
  const txObj = rowToObject(txHeaders, rowValues);

  if (String(txObj['Status']) !== 'Pending') {
    return jsonResponse(false, 'Transaction has already been processed (' + txObj['Status'] + ').');
  }

  updateRowFields('Transactions', rowNumber, { 'Status': newStatus });

  // Automatically reconcile user balance upon Approval ("Success")
  if (newStatus === 'Success') {
    const userFound = findRowByColumn('Users', 'Email', normalizeEmail(txObj['Email']), true);
    if (userFound) {
      const amount = Number(txObj['Amount'] || 0);
      const type = String(txObj['Type'] || '').toLowerCase();
      const currentInvested = Number(userFound.record['Invested'] || 0);
      const currentTotal = Number(userFound.record['Total Amount'] || 0);
      const currentWithdrawals = Number(userFound.record['Total Withdrawal'] || 0);
      const currentLimit = Number(userFound.record['Withdrawable Limit'] || 0);

      if (type === 'deposit') {
        updateRowFields('Users', userFound.rowNumber, {
          'Invested': currentInvested + amount,
          'Total Amount': currentTotal + amount
        });
      } else if (type === 'withdrawal') {
        updateRowFields('Users', userFound.rowNumber, {
          'Total Withdrawal': currentWithdrawals + amount,
          'Total Amount': Math.max(0, currentTotal - amount),
          'Withdrawable Limit': Math.max(0, currentLimit - amount)
        });
      }
    }
  }

  return jsonResponse(true, 'Transaction marked as ' + newStatus, { rowNumber: rowNumber, status: newStatus });
}

function handleUpdateKycStatus(body) {
  assertAdmin(body.adminEmail);
  const targetEmail = normalizeEmail(body.email);
  const newStatus = String(body.status || '').trim(); // Verified | Rejected
  if (!targetEmail || (newStatus !== 'Verified' && newStatus !== 'Rejected')) {
    return jsonResponse(false, 'Valid target email and status (Verified/Rejected) required.');
  }

  const userFound = findRowByColumn('Users', 'Email', targetEmail, true);
  if (!userFound) return jsonResponse(false, 'Target user not found.');

  const updates = { 'Status': newStatus };
  if (body.notification !== undefined) {
    updates['Notification'] = String(body.notification);
  }
  updateRowFields('Users', userFound.rowNumber, updates);
  return jsonResponse(true, 'User KYC status updated to ' + newStatus);
}

function handleUpdateUserBalance(body) {
  assertAdmin(body.adminEmail);
  const targetEmail = normalizeEmail(body.email);
  if (!targetEmail) return jsonResponse(false, 'Target email is required.');

  const userFound = findRowByColumn('Users', 'Email', targetEmail, true);
  if (!userFound) return jsonResponse(false, 'User not found.');

  const updates = {};
  if (body.invested !== undefined) updates['Invested'] = Number(body.invested);
  if (body.totalAmount !== undefined) updates['Total Amount'] = Number(body.totalAmount);
  if (body.withdrawableLimit !== undefined) updates['Withdrawable Limit'] = Number(body.withdrawableLimit);
  if (body.interestRate !== undefined) updates['Interest (%)'] = Number(body.interestRate);
  if (body.notification !== undefined) updates['Notification'] = String(body.notification);

  updateRowFields('Users', userFound.rowNumber, updates);
  return jsonResponse(true, 'User balances updated successfully.');
}

/* ==================== APP VERSION CHECK ==================== */

function getAppVersionSettings() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName('Admin_Settings');
  if (!sheet) {
    return {
      latestVersionCode: 1,
      latestVersionName: '1.0',
      minVersionCode: 1,
      forceUpdate: false,
      updateUrl: '',
      updateMessage: ''
    };
  }
  const values = sheet.getDataRange().getValues();
  const map = {};
  for (let i = 1; i < values.length; i++) {
    const k = String(values[i][0] || '').trim();
    const v = String(values[i][1] || '').trim();
    if (k) map[k] = v;
  }
  return {
    latestVersionCode: Number(map['latest_version_code'] || 1),
    latestVersionName: map['latest_version_name'] || '1.0',
    minVersionCode: Number(map['min_version_code'] || 1),
    forceUpdate: String(map['force_update'] || 'false').toLowerCase() === 'true',
    updateUrl: map['update_url'] || '',
    updateMessage: map['update_message'] || 'Update available.'
  };
}

/* ==================== SHEET & DRIVE HELPERS ==================== */

function getSheet(name) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(name);
  if (!sheet) {
    setupSheets();
    sheet = ss.getSheetByName(name);
  }
  return sheet;
}

function getHeaders(sheet) {
  const lastCol = sheet.getLastColumn();
  if (lastCol === 0) return [];
  return sheet.getRange(1, 1, 1, lastCol).getValues()[0].map(function (h) {
    return String(h).trim();
  });
}

function readAllObjects(sheetName) {
  const sheet = getSheet(sheetName);
  const data = sheet.getDataRange().getValues();
  if (data.length <= 1) return [];
  const headers = data[0].map(function (h) { return String(h).trim(); });
  const list = [];
  for (let i = 1; i < data.length; i++) {
    list.push(rowToObject(headers, data[i]));
  }
  return list;
}

function rowToObject(headers, row) {
  const obj = {};
  for (let i = 0; i < headers.length; i++) {
    obj[headers[i]] = row[i] !== undefined ? row[i] : '';
  }
  return obj;
}

function findRowByColumn(sheetName, colName, value, caseInsensitive) {
  const sheet = getSheet(sheetName);
  const data = sheet.getDataRange().getValues();
  if (data.length <= 1) return null;
  const headers = data[0].map(function (h) { return String(h).trim(); });
  const colIdx = headers.indexOf(colName);
  if (colIdx === -1) return null;

  const target = caseInsensitive ? String(value).trim().toLowerCase() : String(value).trim();
  for (let i = 1; i < data.length; i++) {
    const cell = caseInsensitive ? String(data[i][colIdx]).trim().toLowerCase() : String(data[i][colIdx]).trim();
    if (cell === target) {
      return { rowNumber: i + 1, record: rowToObject(headers, data[i]) };
    }
  }
  return null;
}

function updateRowFields(sheetName, rowNumber, updates) {
  const sheet = getSheet(sheetName);
  const headers = getHeaders(sheet);
  Object.keys(updates).forEach(function (field) {
    const colIdx = headers.indexOf(field);
    if (colIdx !== -1) {
      sheet.getRange(rowNumber, colIdx + 1).setValue(updates[field]);
    }
  });
}

function sanitizeUser(record) {
  const copy = Object.assign({}, record);
  delete copy['Password'];
  return copy;
}

function uploadBase64ToDrive(base64Data, filename) {
  const cleaned = base64Data.replace(/^data:image\/\w+;base64,/, '');
  const blob = Utilities.newBlob(Utilities.base64Decode(cleaned), 'image/jpeg', filename);
  const folders = DriveApp.getFoldersByName(DRIVE_FOLDER_NAME);
  const folder = folders.hasNext() ? folders.next() : DriveApp.createFolder(DRIVE_FOLDER_NAME);
  const file = folder.createFile(blob);
  file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  return 'https://drive.google.com/uc?export=view&id=' + file.getId();
}

function normalizeEmail(email) {
  return String(email || '').trim().toLowerCase();
}

function parseBody(e) {
  if (!e || !e.postData || !e.postData.contents) return {};
  return JSON.parse(e.postData.contents);
}

function jsonResponse(success, message, data) {
  const payload = {
    success: success,
    message: message,
    data: data !== undefined ? data : null
  };
  return ContentService
    .createTextOutput(JSON.stringify(payload))
    .setMimeType(ContentService.MimeType.JSON);
}
