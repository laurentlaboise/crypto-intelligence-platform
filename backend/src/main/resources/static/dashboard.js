const API_BASE = window.location.origin;
const token = localStorage.getItem('authToken');
const userRole = localStorage.getItem('userRole');

let marketData = [];
let selectedCoin = null;
let tradeAction = 'BUY';
let priceChart = null;
let currentTimeframeDays = 1;
let modalCloseTimeout = null;

// Auth guard
(function checkAuth() {
    if (!token) {
        window.location.href = 'index.html';
        return;
    }
    // Show admin link if admin
    if (userRole === 'ADMIN') {
        document.getElementById('admin-link').style.display = 'inline-block';
    }
    init();
})();

async function init() {
    await Promise.all([
        loadMarketData(),
        loadPortfolio(),
        loadTransactions()
    ]);
    // Auto-refresh market data every 30 seconds
    setInterval(loadMarketData, 30000);
    setInterval(loadPortfolio, 30000);
}

function authHeaders() {
    return {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
    };
}

function formatUsd(value) {
    if (value == null) return '$0.00';
    const num = typeof value === 'number' ? value : parseFloat(value);
    if (isNaN(num)) return '$0.00';
    if (num >= 1e9) return '$' + (num / 1e9).toFixed(2) + 'B';
    if (num >= 1e6) return '$' + (num / 1e6).toFixed(2) + 'M';
    if (num >= 1000) return '$' + num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    if (num >= 1) return '$' + num.toFixed(2);
    return '$' + num.toFixed(6);
}

function formatNumber(value) {
    if (value == null) return '0';
    return parseFloat(value).toLocaleString('en-US');
}

function formatPct(value) {
    if (value == null) return '0.00%';
    const num = typeof value === 'number' ? value : parseFloat(value);
    const sign = num >= 0 ? '+' : '';
    return sign + num.toFixed(2) + '%';
}

// Market Data
async function loadMarketData() {
    try {
        const res = await fetch(`${API_BASE}/api/market/top20`, { headers: authHeaders() });
        if (res.status === 401) { logout(); return; }
        if (!res.ok) return;

        marketData = await res.json();
        renderMarketTable();

        if (!selectedCoin && marketData.length > 0) {
            selectCoin(marketData[0]);
        }
    } catch (err) {
        console.error('Failed to load market data:', err);
    }
}

function renderMarketTable() {
    const tbody = document.getElementById('market-tbody');
    const search = document.getElementById('market-search').value.toLowerCase();

    const filtered = marketData.filter(coin =>
        coin.name.toLowerCase().includes(search) ||
        coin.symbol.toLowerCase().includes(search)
    );

    if (filtered.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="loading-text">No matching coins found</td></tr>';
        return;
    }

    tbody.innerHTML = filtered.map((coin, idx) => {
        const pctClass = (coin.priceChangePercentage24h || 0) >= 0 ? 'positive' : 'negative';
        const rank = marketData.indexOf(coin) + 1;
        return `
        <tr data-coin-id="${coin.coinId}" onclick="selectCoin(marketData.find(c=>c.coinId==='${coin.coinId}'))">
            <td>${rank}</td>
            <td class="coin-name"><strong>${coin.name}</strong> <span class="symbol">${coin.symbol.toUpperCase()}</span></td>
            <td>${formatUsd(coin.currentPrice)}</td>
            <td class="${pctClass}">${formatPct(coin.priceChangePercentage24h)}</td>
            <td>${formatUsd(coin.marketCap)}</td>
            <td>${formatUsd(coin.totalVolume)}</td>
            <td class="action-buttons">
                <button class="btn btn-buy btn-sm" data-action="buy" onclick="event.stopPropagation(); openTradeModal('${coin.coinId}', 'BUY')">Buy</button>
                <button class="btn btn-sell btn-sm" data-action="sell" onclick="event.stopPropagation(); openTradeModal('${coin.coinId}', 'SELL')">Sell</button>
            </td>
        </tr>`;
    }).join('');
}

function filterMarketTable() {
    renderMarketTable();
}

// Coin Selection & Chart
function selectCoin(coin) {
    if (!coin) return;
    selectedCoin = coin;
    document.getElementById('chart-title').textContent = `${coin.name} (${coin.symbol.toUpperCase()})`;
    loadChart(coin.coinId, currentTimeframeDays);
}

function changeTimeframe(btn) {
    document.querySelectorAll('.tf-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    currentTimeframeDays = parseFloat(btn.dataset.days);
    if (selectedCoin) {
        loadChart(selectedCoin.coinId, currentTimeframeDays);
    }
}

async function loadChart(coinId, days) {
    try {
        const queryDays = days < 1 ? 1 : days;
        const res = await fetch(`${API_BASE}/api/market/history/${coinId}?days=${queryDays}`, { headers: authHeaders() });
        if (!res.ok) return;

        let data = await res.json();

        // For 1H, filter to last hour from 1-day data; fallback to last 12 points
        if (days < 1) {
            const cutoff = Date.now() - 3600000;
            const filtered = data.filter(d => d.timestamp >= cutoff);
            data = filtered.length >= 2 ? filtered : data.slice(-12);
        }

        renderChart(data);
    } catch (err) {
        console.error('Failed to load chart:', err);
    }
}

function renderChart(data) {
    const ctx = document.getElementById('price-chart').getContext('2d');

    if (priceChart) {
        priceChart.destroy();
    }

    const labels = data.map(d => {
        const date = new Date(d.timestamp);
        if (currentTimeframeDays <= 1) {
            return date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
        }
        return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    });

    const prices = data.map(d => d.price);
    const isPositive = prices.length >= 2 && prices[prices.length - 1] >= prices[0];

    priceChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels,
            datasets: [{
                label: 'Price (USD)',
                data: prices,
                borderColor: isPositive ? '#00e676' : '#ff1744',
                backgroundColor: isPositive ? 'rgba(0, 230, 118, 0.1)' : 'rgba(255, 23, 68, 0.1)',
                fill: true,
                tension: 0.3,
                pointRadius: 0,
                pointHoverRadius: 5,
                borderWidth: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: ctx => '$' + ctx.parsed.y.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 6 })
                    }
                }
            },
            scales: {
                x: {
                    grid: { color: 'rgba(255,255,255,0.05)' },
                    ticks: { color: '#8a8d93', maxTicksLimit: 8 }
                },
                y: {
                    grid: { color: 'rgba(255,255,255,0.05)' },
                    ticks: {
                        color: '#8a8d93',
                        callback: val => '$' + val.toLocaleString()
                    }
                }
            }
        }
    });
}

// Portfolio
async function loadPortfolio() {
    try {
        const res = await fetch(`${API_BASE}/api/portfolio`, { headers: authHeaders() });
        if (res.status === 401) { logout(); return; }
        if (!res.ok) return;

        const data = await res.json();
        document.getElementById('user-balance').textContent = parseFloat(data.currentBalance).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        document.getElementById('total-value').textContent = formatUsd(data.totalPortfolioValue);

        // Display 24h portfolio change
        const change24h = parseFloat(data.portfolioChange24h) || 0;
        const changePct = parseFloat(data.portfolioChange24hPercentage) || 0;
        const sign = change24h >= 0 ? '+' : '';
        const changeEl = document.getElementById('portfolio-change');
        changeEl.textContent = `${sign}$${Math.abs(change24h).toFixed(2)} (${sign}${changePct.toFixed(2)}%)`;
        changeEl.className = 'stat-value ' + (change24h >= 0 ? 'positive' : 'negative');

        renderHoldings(data.holdings || []);
    } catch (err) {
        console.error('Failed to load portfolio:', err);
    }
}

function renderHoldings(holdings) {
    const container = document.getElementById('holdings-list');
    if (!holdings || holdings.length === 0) {
        container.innerHTML = '<p class="muted">No holdings yet. Start trading!</p>';
        return;
    }

    container.innerHTML = holdings.map(h => {
        const plClass = parseFloat(h.profitLossPercentage) >= 0 ? 'positive' : 'negative';
        return `
        <div class="holding-item">
            <div class="holding-coin">${h.coinId}</div>
            <div class="holding-qty">Qty: ${parseFloat(h.quantity).toFixed(6)}</div>
            <div class="holding-value">${formatUsd(h.currentValue)}</div>
            <div class="holding-pl ${plClass}">${formatPct(h.profitLossPercentage)}</div>
        </div>`;
    }).join('');
}

// Transactions
async function loadTransactions() {
    try {
        const res = await fetch(`${API_BASE}/api/transactions`, { headers: authHeaders() });
        if (res.status === 401) { logout(); return; }
        if (!res.ok) return;

        const txs = await res.json();
        document.getElementById('total-trades').textContent = txs.length;

        // Calculate win rate: for each sold coin, compare avg sell price vs avg buy price
        const sells = txs.filter(t => t.action === 'SELL');
        const buys = txs.filter(t => t.action === 'BUY');
        if (sells.length > 0) {
            const avgBuyPrice = {};
            buys.forEach(t => {
                if (!avgBuyPrice[t.coinId]) avgBuyPrice[t.coinId] = { total: 0, qty: 0 };
                avgBuyPrice[t.coinId].total += parseFloat(t.amountUsd);
                avgBuyPrice[t.coinId].qty += parseFloat(t.quantity);
            });
            let wins = 0;
            sells.forEach(t => {
                const buyInfo = avgBuyPrice[t.coinId];
                const avgBuy = (buyInfo && buyInfo.qty > 0) ? buyInfo.total / buyInfo.qty : 0;
                if (parseFloat(t.executionPrice) >= avgBuy) wins++;
            });
            document.getElementById('win-rate').textContent = ((wins / sells.length) * 100).toFixed(0) + '%';
        } else {
            document.getElementById('win-rate').textContent = 'N/A';
        }

        renderTransactions(txs);
    } catch (err) {
        console.error('Failed to load transactions:', err);
    }
}

function renderTransactions(txs) {
    const container = document.getElementById('transaction-history');
    if (!txs || txs.length === 0) {
        container.innerHTML = '<p class="muted">No transactions yet.</p>';
        return;
    }

    container.innerHTML = txs.map(tx => {
        const actionClass = tx.action === 'BUY' ? 'tx-buy' : 'tx-sell';
        const date = new Date(tx.timestamp).toLocaleString();
        return `
        <div class="tx-item ${actionClass}">
            <div class="tx-action">${tx.action}</div>
            <div class="tx-coin">${tx.coinId}</div>
            <div class="tx-amount">${formatUsd(tx.amountUsd)}</div>
            <div class="tx-price">@ ${formatUsd(tx.executionPrice)}</div>
            <div class="tx-time">${date}</div>
        </div>`;
    }).join('');
}

// Trading Modal
function openTradeModal(coinId, action) {
    const coin = marketData.find(c => c.coinId === coinId);
    if (!coin) return;

    // Cancel any pending auto-close from a previous trade
    if (modalCloseTimeout) {
        clearTimeout(modalCloseTimeout);
        modalCloseTimeout = null;
    }

    tradeAction = action;
    document.getElementById('modal-title').textContent = `${action} ${coin.name} (${coin.symbol.toUpperCase()})`;

    const price = parseFloat(coin.currentPrice);
    const execBtn = document.getElementById('trade-execute-btn');
    if (!price || isNaN(price) || price <= 0) {
        document.getElementById('modal-price').textContent = 'Price unavailable';
        execBtn.disabled = true;
    } else {
        document.getElementById('modal-price').textContent = price.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 6 });
        execBtn.disabled = false;
    }

    document.getElementById('trade-amount').value = '';
    document.getElementById('trade-preview').style.display = 'none';
    document.getElementById('trade-error').style.display = 'none';
    document.getElementById('trade-success').style.display = 'none';
    execBtn.textContent = `Confirm ${action}`;
    execBtn.className = `btn ${action === 'BUY' ? 'btn-buy' : 'btn-sell'}`;

    // Store current coin for trade
    document.getElementById('trade-modal').dataset.coinId = coinId;
    document.getElementById('trade-modal').style.display = 'flex';
}

function closeTradeModal() {
    if (modalCloseTimeout) {
        clearTimeout(modalCloseTimeout);
        modalCloseTimeout = null;
    }
    document.getElementById('trade-modal').style.display = 'none';
}

function updateTradePreview() {
    const amount = parseFloat(document.getElementById('trade-amount').value);
    const coinId = document.getElementById('trade-modal').dataset.coinId;
    const coin = marketData.find(c => c.coinId === coinId);

    if (!coin || isNaN(amount) || amount <= 0 || !coin.currentPrice || coin.currentPrice <= 0) {
        document.getElementById('trade-preview').style.display = 'none';
        return;
    }

    const quantity = amount / coin.currentPrice;
    document.getElementById('preview-quantity').textContent = quantity.toFixed(8) + ' ' + coin.symbol.toUpperCase();
    document.getElementById('preview-total').textContent = amount.toFixed(2);
    document.getElementById('trade-preview').style.display = 'block';
}

async function executeTrade() {
    const coinId = document.getElementById('trade-modal').dataset.coinId;
    const amount = parseFloat(document.getElementById('trade-amount').value);

    if (isNaN(amount) || amount <= 0) {
        document.getElementById('trade-error').textContent = 'Please enter a valid amount';
        document.getElementById('trade-error').style.display = 'block';
        return;
    }

    const btn = document.getElementById('trade-execute-btn');
    btn.disabled = true;
    btn.textContent = 'Processing...';
    document.getElementById('trade-error').style.display = 'none';

    try {
        const res = await fetch(`${API_BASE}/api/trade`, {
            method: 'POST',
            headers: authHeaders(),
            body: JSON.stringify({ coinId, action: tradeAction, amountUsd: amount })
        });

        const data = await res.json();

        if (res.ok) {
            document.getElementById('trade-success').textContent =
                `${tradeAction} executed! ${parseFloat(data.quantity).toFixed(8)} at ${formatUsd(data.executionPrice)}`;
            document.getElementById('trade-success').style.display = 'block';

            // Refresh data
            await Promise.all([loadPortfolio(), loadTransactions()]);

            modalCloseTimeout = setTimeout(closeTradeModal, 1500);
        } else {
            document.getElementById('trade-error').textContent = data.message || 'Trade failed';
            document.getElementById('trade-error').style.display = 'block';
        }
    } catch (err) {
        document.getElementById('trade-error').textContent = 'Network error. Please try again.';
        document.getElementById('trade-error').style.display = 'block';
    } finally {
        btn.disabled = false;
        btn.textContent = `Confirm ${tradeAction}`;
    }
}

function logout() {
    localStorage.removeItem('authToken');
    localStorage.removeItem('userRole');
    window.location.href = 'index.html';
}
