const API_BASE = window.location.origin;
const token = localStorage.getItem('authToken');
const userRole = localStorage.getItem('userRole');

(function checkAuth() {
    if (!token || userRole !== 'ADMIN') {
        window.location.href = 'index.html';
        return;
    }
    loadAdminData();
})();

function authHeaders() {
    return {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
    };
}

async function loadAdminData() {
    await Promise.all([loadUsers(), loadTransactions()]);
}

async function loadUsers() {
    try {
        const res = await fetch(`${API_BASE}/api/admin/users`, { headers: authHeaders() });
        if (res.status === 401 || res.status === 403) { logout(); return; }
        if (!res.ok) return;

        const users = await res.json();
        const tbody = document.getElementById('users-tbody');
        if (users.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="loading-text">No users found</td></tr>';
            return;
        }

        tbody.innerHTML = users.map(u => `
            <tr>
                <td>${u.id}</td>
                <td>${u.email}</td>
                <td>${u.name}</td>
                <td><span class="role-badge role-${u.role.toLowerCase()}">${u.role}</span></td>
                <td>${new Date(u.createdAt).toLocaleString()}</td>
            </tr>
        `).join('');
    } catch (err) {
        console.error('Failed to load users:', err);
    }
}

async function loadTransactions() {
    try {
        const res = await fetch(`${API_BASE}/api/admin/transactions`, { headers: authHeaders() });
        if (res.status === 401 || res.status === 403) { logout(); return; }
        if (!res.ok) return;

        const txs = await res.json();
        const tbody = document.getElementById('transactions-tbody');
        if (txs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="loading-text">No transactions found</td></tr>';
            return;
        }

        tbody.innerHTML = txs.map(tx => {
            const actionClass = tx.action === 'BUY' ? 'positive' : 'negative';
            return `
            <tr>
                <td>${tx.transactionId}</td>
                <td>${tx.coinId}</td>
                <td class="${actionClass}">${tx.action}</td>
                <td>$${parseFloat(tx.amountUsd).toFixed(2)}</td>
                <td>$${parseFloat(tx.executionPrice).toLocaleString('en-US', { maximumFractionDigits: 6 })}</td>
                <td>${parseFloat(tx.quantity).toFixed(8)}</td>
                <td>${new Date(tx.timestamp).toLocaleString()}</td>
            </tr>`;
        }).join('');
    } catch (err) {
        console.error('Failed to load transactions:', err);
    }
}

function logout() {
    localStorage.removeItem('authToken');
    localStorage.removeItem('userRole');
    window.location.href = 'index.html';
}
