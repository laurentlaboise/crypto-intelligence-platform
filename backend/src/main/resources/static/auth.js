const API_BASE = window.location.origin;

// Check if already authenticated
(function checkAuth() {
    const token = localStorage.getItem('authToken');
    if (token) {
        window.location.href = 'dashboard.html';
    }
})();

function switchTab(tab) {
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelector(`[data-tab="${tab}"]`).classList.add('active');

    document.getElementById('login-form').style.display = tab === 'login' ? 'block' : 'none';
    document.getElementById('register-form').style.display = tab === 'register' ? 'block' : 'none';

    clearErrors();
}

function clearErrors() {
    document.getElementById('login-error').style.display = 'none';
    document.getElementById('register-error').style.display = 'none';
    document.getElementById('register-success').style.display = 'none';
}

function showError(elementId, message) {
    const el = document.getElementById(elementId);
    el.textContent = message;
    el.style.display = 'block';
}

function showSuccess(elementId, message) {
    const el = document.getElementById(elementId);
    el.textContent = message;
    el.style.display = 'block';
}

async function handleLogin(e) {
    e.preventDefault();
    clearErrors();

    const email = document.getElementById('login-email').value.trim();
    const password = document.getElementById('login-password').value;

    if (!email || !password) {
        showError('login-error', 'Please fill in all fields');
        return;
    }

    const btn = document.getElementById('login-btn');
    btn.disabled = true;
    btn.textContent = 'Signing in...';

    try {
        const res = await fetch(`${API_BASE}/api/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });

        const data = await res.json();

        if (res.ok) {
            localStorage.setItem('authToken', data.token);
            localStorage.setItem('userRole', data.role);
            window.location.href = 'dashboard.html';
        } else {
            showError('login-error', data.message || 'Login failed');
        }
    } catch (err) {
        showError('login-error', 'Network error. Please try again.');
    } finally {
        btn.disabled = false;
        btn.textContent = 'Sign In';
    }
}

async function handleRegister(e) {
    e.preventDefault();
    clearErrors();

    const name = document.getElementById('reg-name').value.trim();
    const email = document.getElementById('reg-email').value.trim();
    const password = document.getElementById('reg-password').value;
    const confirm = document.getElementById('reg-confirm').value;

    if (!name || !email || !password || !confirm) {
        showError('register-error', 'Please fill in all fields');
        return;
    }

    if (password !== confirm) {
        showError('register-error', 'Passwords do not match');
        return;
    }

    if (password.length < 8) {
        showError('register-error', 'Password must be at least 8 characters');
        return;
    }

    const btn = document.getElementById('register-btn');
    btn.disabled = true;
    btn.textContent = 'Creating account...';

    try {
        const res = await fetch(`${API_BASE}/api/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password, name })
        });

        const data = await res.json();

        if (res.ok) {
            showSuccess('register-success', 'Account created! Redirecting to login...');
            setTimeout(() => switchTab('login'), 1500);
        } else {
            showError('register-error', data.message || 'Registration failed');
        }
    } catch (err) {
        showError('register-error', 'Network error. Please try again.');
    } finally {
        btn.disabled = false;
        btn.textContent = 'Create Account';
    }
}
