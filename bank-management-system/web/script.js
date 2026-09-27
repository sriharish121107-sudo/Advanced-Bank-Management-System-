// --- API ENDPOINTS CONFIG ---
const API_BASE = '/api/accounts';

// --- APPLICATION STATE ---
let allAccounts = [];
let activeSection = 'summary';

// Chart.js references (stored to allow clean redraws)
let typeChartInstance = null;
let balanceChartInstance = null;

// --- INITIALIZATION ---
document.addEventListener('DOMContentLoaded', () => {
    setupNavigation();
    refreshDashboardData();
    
    // Start periodic background logs polling (every 4 seconds)
    pollLogs();
    setInterval(pollLogs, 4000);
});

// --- NAVIGATION & ROUTING ---
function setupNavigation() {
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            const targetSection = item.getAttribute('data-section');
            switchSection(targetSection);
        });
    });
}

function switchSection(sectionId) {
    // Remove active class from nav items
    document.querySelectorAll('.nav-item').forEach(item => {
        item.classList.remove('active');
        if (item.getAttribute('data-section') === sectionId) {
            item.classList.add('active');
        }
    });

    // Remove active class from sections
    document.querySelectorAll('.dashboard-section').forEach(sec => {
        sec.classList.remove('active');
    });

    // Add active class to target section
    const target = document.getElementById(`section-${sectionId}`);
    if (target) {
        target.classList.add('active');
    }

    activeSection = sectionId;
    
    // Update Header Titles
    const titleMap = {
        'summary': ['Dashboard Summary', 'Real-time banking administration dashboard'],
        'accounts': ['Manage Accounts', 'Search, update, and delete customer accounts'],
        'create-account': ['Open Account', 'Register a new customer account in the system'],
        'transactions': ['Transfer & Operations', 'Execute deposits, withdrawals, and bank transfers'],
        'history': ['Transactions Log', 'Browse transaction logs and historical ledger statements']
    };

    if (titleMap[sectionId]) {
        document.getElementById('page-title').textContent = titleMap[sectionId][0];
        document.getElementById('page-subtitle').textContent = titleMap[sectionId][1];
    }

    // Refresh data
    refreshDashboardData();
}

// --- FETCH & DATABASE SYNC ---
async function refreshDashboardData() {
    try {
        const response = await fetch(API_BASE);
        if (!response.ok) {
            throw new Error('Failed to load accounts from database.');
        }
        allAccounts = await response.json();
        
        updateMetricsAndStats();
        renderSummaryTable();
        renderManageTable();
        populateSelectors();
        renderCharts();
    } catch (error) {
        showNotification(error.message, true);
    }
}

// --- METRICS & STATS UPDATE ---
function updateMetricsAndStats() {
    const totalAccounts = allAccounts.length;
    let totalDeposits = 0;
    let savingsDeposits = 0;
    let currentAccountsCount = 0;
    let highestBalance = -1;
    let highestHolder = null;
    let mostActive = null;
    let maxTxCount = -1;
    
    allAccounts.forEach(acc => {
        totalDeposits += acc.balance;
        
        if (acc.accountType === 'Savings') {
            savingsDeposits += acc.balance;
        } else if (acc.accountType === 'Current') {
            currentAccountsCount++;
        }

        if (acc.balance > highestBalance) {
            highestBalance = acc.balance;
            highestHolder = acc;
        }

        if (acc.transactionCount > maxTxCount) {
            maxTxCount = acc.transactionCount;
            mostActive = acc;
        }
    });

    const avgBalance = totalAccounts > 0 ? (totalDeposits / totalAccounts) : 0;

    // Set simple cards
    document.getElementById('metric-total-accounts').textContent = totalAccounts;
    document.getElementById('metric-total-deposits').textContent = '₹' + totalDeposits.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    document.getElementById('metric-avg-balance').textContent = '₹' + avgBalance.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    // Set advanced yields
    const projectedSavingsYield = savingsDeposits * 0.04;
    const projectedCurrentFees = currentAccountsCount * 100;
    
    document.getElementById('proj-savings-yield').textContent = '₹' + projectedSavingsYield.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' / year';
    document.getElementById('proj-current-fees').textContent = '₹' + projectedCurrentFees.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' / month';

    if (highestHolder) {
        document.getElementById('proj-highest-bal').textContent = `${highestHolder.accountNumber} - ${highestHolder.holderName} (₹${highestHolder.balance.toLocaleString('en-IN', { minimumFractionDigits: 2 })})`;
    } else {
        document.getElementById('proj-highest-bal').textContent = 'N/A';
    }

    if (mostActive && maxTxCount > 0) {
        document.getElementById('proj-most-active').textContent = `${mostActive.accountNumber} - ${mostActive.holderName} (${maxTxCount} txs)`;
    } else {
        document.getElementById('proj-most-active').textContent = 'N/A';
    }
}

// --- RENDER TABLES ---
function renderSummaryTable() {
    const tbody = document.getElementById('recent-accounts-tbody');
    tbody.innerHTML = '';

    if (allAccounts.length === 0) {
        tbody.innerHTML = `<tr><td colspan="4" class="text-center">No accounts found. Open a new account to begin!</td></tr>`;
        return;
    }

    const sorted = [...allAccounts].sort((a, b) => b.accountNumber - a.accountNumber).slice(0, 5);

    sorted.forEach(acc => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><strong>${acc.accountNumber}</strong></td>
            <td>${escapeHtml(acc.holderName)}</td>
            <td><span class="badge">${acc.accountType}</span></td>
            <td><strong>₹${acc.balance.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</strong></td>
        `;
        tbody.appendChild(tr);
    });
}

function renderManageTable(accountsToRender = allAccounts) {
    const tbody = document.getElementById('all-accounts-tbody');
    tbody.innerHTML = '';

    if (accountsToRender.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center">No accounts match search criteria.</td></tr>`;
        return;
    }

    accountsToRender.forEach(acc => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><strong>${acc.accountNumber}</strong></td>
            <td>${escapeHtml(acc.holderName)}</td>
            <td><span class="badge">${acc.accountType}</span></td>
            <td><strong>₹${acc.balance.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</strong></td>
            <td>
                <button class="btn btn-secondary btn-sm" onclick="openUpdateModal(${acc.accountNumber}, '${escapeQuote(acc.holderName)}', '${acc.accountType}')">✏️ Edit</button>
                <button class="btn btn-danger btn-sm" style="margin-left: 6px;" onclick="openDeleteModal(${acc.accountNumber}, '${escapeQuote(acc.holderName)}', ${acc.balance})">🗑️ Delete</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

// --- POPULATE OPTION SELECTORS ---
function populateSelectors() {
    // 1. Transaction History selector
    const historySelect = document.getElementById('history-acc-select');
    const prevHistoryVal = historySelect.value;
    historySelect.innerHTML = '<option value="">-- Choose Account --</option>';

    // 2. Quick Transfer Sender selector
    const qSender = document.getElementById('quick-sender');
    const prevSenderVal = qSender.value;
    qSender.innerHTML = '<option value="">-- Select --</option>';

    // 3. Quick Transfer Receiver selector
    const qReceiver = document.getElementById('quick-receiver');
    const prevReceiverVal = qReceiver.value;
    qReceiver.innerHTML = '<option value="">-- Select --</option>';

    allAccounts.forEach(acc => {
        const displayText = `${acc.accountNumber} - ${acc.holderName} (₹${acc.balance.toFixed(2)})`;
        
        // History option
        const opt1 = document.createElement('option');
        opt1.value = acc.accountNumber;
        opt1.textContent = `${acc.accountNumber} - ${acc.holderName}`;
        historySelect.appendChild(opt1);

        // Sender option
        const opt2 = document.createElement('option');
        opt2.value = acc.accountNumber;
        opt2.textContent = displayText;
        qSender.appendChild(opt2);

        // Receiver option
        const opt3 = document.createElement('option');
        opt3.value = acc.accountNumber;
        opt3.textContent = displayText;
        qReceiver.appendChild(opt3);
    });

    // Re-select previous values if they still exist
    if (prevHistoryVal && allAccounts.some(a => a.accountNumber == prevHistoryVal)) {
        historySelect.value = prevHistoryVal;
    }
    if (prevSenderVal && allAccounts.some(a => a.accountNumber == prevSenderVal)) {
        qSender.value = prevSenderVal;
    }
    if (prevReceiverVal && allAccounts.some(a => a.accountNumber == prevReceiverVal)) {
        qReceiver.value = prevReceiverVal;
    }
}

// --- RENDER DYNAMIC CHARTS (CHART.JS) ---
function renderCharts() {
    // 1. Account type doughnut chart
    let savingsCount = 0;
    let currentCount = 0;
    allAccounts.forEach(acc => {
        if (acc.accountType === 'Savings') savingsCount++;
        else currentCount++;
    });

    const ctxType = document.getElementById('type-chart').getContext('2d');
    if (typeChartInstance) {
        typeChartInstance.destroy();
    }
    typeChartInstance = new Chart(ctxType, {
        type: 'doughnut',
        data: {
            labels: ['Savings Accounts', 'Current Accounts'],
            datasets: [{
                data: [savingsCount, currentCount],
                backgroundColor: ['#3b82f6', '#10b981'],
                borderColor: '#1e293b',
                borderWidth: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: { color: '#f8fafc', font: { family: 'Plus Jakarta Sans', size: 12 } }
                }
            }
        }
    });

    // 2. Wealth distribution bar chart (Top 5 Rich accounts)
    const sortedRich = [...allAccounts].sort((a, b) => b.balance - a.balance).slice(0, 5);
    const richNames = sortedRich.map(a => `${a.accountNumber} (${a.holderName})`);
    const richBalances = sortedRich.map(a => a.balance);

    const ctxBal = document.getElementById('balance-chart').getContext('2d');
    if (balanceChartInstance) {
        balanceChartInstance.destroy();
    }
    balanceChartInstance = new Chart(ctxBal, {
        type: 'bar',
        data: {
            labels: richNames,
            datasets: [{
                label: 'Account Balance (₹)',
                data: richBalances,
                backgroundColor: '#eab308',
                borderColor: '#eab308',
                borderRadius: 4
            }]
        },
        options: {
            indexAxis: 'y',
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                x: { ticks: { color: '#94a3b8' }, grid: { color: 'rgba(255, 255, 255, 0.05)' } },
                y: { ticks: { color: '#f8fafc' }, grid: { display: false } }
            },
            plugins: {
                legend: { display: false }
            }
        }
    });
}

// --- FORM ACTION: CREATE ACCOUNT ---
async function handleCreateAccountSubmit(e) {
    e.preventDefault();
    const name = document.getElementById('holder-name-input').value.trim();
    const type = document.getElementById('account-type-input').value;
    const balance = parseFloat(document.getElementById('opening-balance-input').value);

    try {
        const response = await fetch(API_BASE, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ holderName: name, accountType: type, openingBalance: balance })
        });
        
        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.error || 'Failed to create account.');
        }

        showNotification(data.message, false);
        document.getElementById('create-account-form').reset();
        
        // Sync and switch home
        setTimeout(() => {
            switchSection('summary');
        }, 800);

    } catch (error) {
        showNotification(error.message, true);
    }
}

// --- ACTION BAR: SEARCH ---
async function executeSearch() {
    const accNum = document.getElementById('search-input').value.trim();
    if (!accNum) {
        refreshDashboardData();
        return;
    }

    const startTime = performance.now();
    try {
        const response = await fetch(`${API_BASE}/search?accountNumber=${accNum}`);
        const data = await response.json();
        
        const duration = (performance.now() - startTime).toFixed(1);
        document.getElementById('search-benchmark').textContent = `Search completed in ${duration} ms (client-side)`;

        if (!response.ok) {
            throw new Error(data.error || 'Account not found.');
        }

        renderManageTable([data]);
    } catch (error) {
        showNotification(error.message, true);
        document.getElementById('all-accounts-tbody').innerHTML = `<tr><td colspan="5" class="text-center text-danger">Account ${accNum} not found.</td></tr>`;
    }
}

function resetSearch() {
    document.getElementById('search-input').value = '';
    document.getElementById('search-benchmark').textContent = '';
    renderManageTable();
}

// --- MODAL: UPDATE ACCOUNT ---
function openUpdateModal(accNum, name, type) {
    document.getElementById('update-acc-number').value = accNum;
    document.getElementById('update-acc-number-display').value = accNum;
    document.getElementById('update-holder-name').value = name;
    document.getElementById('update-account-type').value = type;
    document.getElementById('update-modal').classList.remove('hidden');
}

function closeUpdateModal() {
    document.getElementById('update-modal').classList.add('hidden');
}

async function handleUpdateSubmit(e) {
    e.preventDefault();
    const accNum = parseInt(document.getElementById('update-acc-number').value);
    const name = document.getElementById('update-holder-name').value.trim();
    const type = document.getElementById('update-account-type').value;

    try {
        const response = await fetch(`${API_BASE}/update`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ accountNumber: accNum, holderName: name, accountType: type })
        });
        
        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.error || 'Failed to update account.');
        }

        showNotification(data.message, false);
        closeUpdateModal();
        refreshDashboardData();
    } catch (error) {
        showNotification(error.message, true);
    }
}

// --- MODAL: DELETE ACCOUNT ---
function openDeleteModal(accNum, name, balance) {
    document.getElementById('delete-acc-num').textContent = accNum;
    document.getElementById('delete-acc-holder').textContent = name;
    document.getElementById('delete-acc-balance').textContent = '₹' + balance.toFixed(2);
    
    const confirmBtn = document.getElementById('delete-confirm-btn');
    const newConfirmBtn = confirmBtn.cloneNode(true);
    confirmBtn.parentNode.replaceChild(newConfirmBtn, confirmBtn);

    newConfirmBtn.addEventListener('click', () => {
        executeDelete(accNum);
    });

    document.getElementById('delete-modal').classList.remove('hidden');
}

function closeDeleteModal() {
    document.getElementById('delete-modal').classList.add('hidden');
}

async function executeDelete(accNum) {
    try {
        const response = await fetch(`${API_BASE}/delete?accountNumber=${accNum}`, {
            method: 'DELETE'
        });
        
        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.error || 'Failed to delete account.');
        }

        showNotification(data.message, false);
        closeDeleteModal();
        refreshDashboardData();
    } catch (error) {
        showNotification(error.message, true);
    }
}

// --- DEPOSIT / WITHDRAW OPERATIONS ---
function switchTxSubTab(tab) {
    document.getElementById('btn-tab-deposit').classList.remove('active');
    document.getElementById('btn-tab-withdraw').classList.remove('active');
    
    document.getElementById(`btn-tab-${tab}`).classList.add('active');
    document.getElementById('ops-type').value = tab;
    document.getElementById('ops-submit-btn').textContent = `Execute ${tab.charAt(0).toUpperCase() + tab.slice(1)}`;
    
    if (tab === 'deposit') {
        document.getElementById('ops-submit-btn').className = 'btn btn-primary';
    } else {
        document.getElementById('ops-submit-btn').className = 'btn btn-danger';
    }
}

async function handleOpsSubmit(e) {
    e.preventDefault();
    const type = document.getElementById('ops-type').value;
    const accNum = parseInt(document.getElementById('ops-acc-num').value);
    const amount = parseFloat(document.getElementById('ops-amount').value);

    try {
        const response = await fetch(`${API_BASE}/${type}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ accountNumber: accNum, amount: amount })
        });
        
        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.error || `Failed to perform ${type}.`);
        }

        document.getElementById('ops-form').reset();
        await refreshDashboardData();
        
        // Open printable success receipt
        const targetAcc = allAccounts.find(a => a.accountNumber === accNum);
        showReceipt({
            txId: `TX-${type.toUpperCase()}-${Math.random().toString(36).substring(2, 8).toUpperCase()}`,
            timestamp: new Date().toLocaleString(),
            opType: type.toUpperCase(),
            amount: amount,
            balance: targetAcc ? targetAcc.balance : 0
        });

    } catch (error) {
        showNotification(error.message, true);
    }
}

// --- FUND TRANSFER OPERATION ---
async function handleTransferSubmit(e) {
    e.preventDefault();
    const sender = parseInt(document.getElementById('transfer-sender').value);
    const receiver = parseInt(document.getElementById('transfer-receiver').value);
    const amount = parseFloat(document.getElementById('transfer-amount').value);

    try {
        const response = await fetch(`${API_BASE}/transfer`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ 
                senderAccountNumber: sender, 
                receiverAccountNumber: receiver, 
                amount: amount 
            })
        });

        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.error || 'Failed to complete transfer.');
        }

        document.getElementById('transfer-form').reset();
        await refreshDashboardData();
        
        // Open receipt for the sender
        const senderAcc = allAccounts.find(a => a.accountNumber === sender);
        showReceipt({
            txId: `TX-TRF-${Math.random().toString(36).substring(2, 8).toUpperCase()}`,
            timestamp: new Date().toLocaleString(),
            opType: `TRANSFER (TO ACC ${receiver})`,
            amount: amount,
            balance: senderAcc ? senderAcc.balance : 0
        });

    } catch (error) {
        showNotification(error.message, true);
    }
}

// --- QUICK TRANSFER SUBMISSION ---
async function handleQuickTransferSubmit(e) {
    e.preventDefault();
    const sender = parseInt(document.getElementById('quick-sender').value);
    const receiver = parseInt(document.getElementById('quick-receiver').value);
    const amount = parseFloat(document.getElementById('quick-amount').value);

    try {
        const response = await fetch(`${API_BASE}/transfer`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ 
                senderAccountNumber: sender, 
                receiverAccountNumber: receiver, 
                amount: amount 
            })
        });

        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.error || 'Failed to complete transfer.');
        }

        document.getElementById('quick-transfer-form').reset();
        await refreshDashboardData();
        
        // Open receipt modal
        const senderAcc = allAccounts.find(a => a.accountNumber === sender);
        showReceipt({
            txId: `TX-TRF-Q-${Math.random().toString(36).substring(2, 8).toUpperCase()}`,
            timestamp: new Date().toLocaleString(),
            opType: `QUICK TRANSFER (TO ${receiver})`,
            amount: amount,
            balance: senderAcc ? senderAcc.balance : 0
        });

    } catch (error) {
        showNotification(error.message, true);
    }
}

// --- TRANSACTION LEDGER HISTORY ---
async function loadTransactionHistory() {
    const accNum = document.getElementById('history-acc-select').value;
    const tbody = document.getElementById('history-tbody');
    
    if (!accNum) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center">Select an account number above to display transaction history.</td></tr>`;
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/history?accountNumber=${accNum}`);
        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.error || 'Failed to retrieve transaction history.');
        }

        tbody.innerHTML = '';
        if (data.length === 0) {
            tbody.innerHTML = `<tr><td colspan="5" class="text-center">No transactions recorded for this account yet.</td></tr>`;
            return;
        }

        const sorted = data.reverse();

        sorted.forEach(tx => {
            const tr = document.createElement('tr');
            let typeColor = '';
            if (tx.type.startsWith('DEPOSIT') || tx.type.startsWith('TRANSFER_FRM') || tx.type === 'INITIAL_DEP') {
                typeColor = 'color: var(--color-success); font-weight: 500;';
            } else {
                typeColor = 'color: var(--color-danger); font-weight: 500;';
            }

            tr.innerHTML = `
                <td><code>${tx.transactionId}</code></td>
                <td>${tx.dateTime}</td>
                <td><span style="${typeColor}">${tx.type}</span></td>
                <td><strong>₹${tx.amount.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</strong></td>
                <td>₹${tx.balanceAfter.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</td>
            `;
            tbody.appendChild(tr);
        });
    } catch (error) {
        showNotification(error.message, true);
        tbody.innerHTML = `<tr><td colspan="5" class="text-center text-danger">Error: ${error.message}</td></tr>`;
    }
}

// --- LOG CONSOLE POLL DAEMON ---
async function pollLogs() {
    try {
        const response = await fetch('/api/logs');
        if (!response.ok) return;
        const logs = await response.json();
        
        const screen = document.getElementById('log-console-screen');
        if (screen) {
            screen.textContent = logs.join('\n');
            // Auto scroll container
            const container = screen.parentElement;
            container.scrollTop = container.scrollHeight;
        }
    } catch (err) {
        // Silently skip if backend server logs aren't ready
    }
}

// --- RECEIPT MODAL FUNCTIONS ---
function showReceipt(details) {
    document.getElementById('receipt-ref-id').textContent = details.txId;
    document.getElementById('receipt-time').textContent = details.timestamp;
    document.getElementById('receipt-op-type').textContent = details.opType;
    document.getElementById('receipt-amount').textContent = '₹' + details.amount.toLocaleString('en-IN', { minimumFractionDigits: 2 });
    document.getElementById('receipt-balance').textContent = '₹' + details.balance.toLocaleString('en-IN', { minimumFractionDigits: 2 });
    
    document.getElementById('receipt-modal').classList.remove('hidden');
}

function closeReceiptModal() {
    document.getElementById('receipt-modal').classList.add('hidden');
}

// --- UTIL: NOTIFICATION FEEDBACK ---
function showNotification(message, isError = false) {
    const box = document.getElementById('notification-box');
    box.textContent = message;
    
    box.className = 'alert-box ' + (isError ? 'error' : 'success');
    box.classList.remove('hidden');

    setTimeout(() => {
        box.classList.add('hidden');
    }, 4500);
}

// --- UTIL: INPUT HTML SANITIZATION ---
function escapeHtml(text) {
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return text.replace(/[&<>"']/g, function(m) { return map[m]; });
}

function escapeQuote(text) {
    if (!text) return '';
    return text.replace(/'/g, "\\'");
}
