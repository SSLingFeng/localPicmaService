/**
 * LoginModal — 统一登录弹窗模块
 *
 * 在任意页面引入 auth.js 和 login-modal.js 后，调用：
 *   LoginModal.init();           // 初始化（注入 DOM + 样式，检查登录状态）
 *   LoginModal.show();           // 手动弹出登录窗口
 *
 * 登录成功后自动在页面右上角显示用户名，支持退出登录。
 */
var LoginModal = (function () {

    var MODAL_ID   = 'loginModalOverlay';
    var STYLE_ID   = 'loginModalStyles';
    var injected   = false;

    /* ==================== STYLES ==================== */

    function injectStyles() {
        if (document.getElementById(STYLE_ID)) return;
        var css = ''
            /* Overlay */
            + '#' + MODAL_ID + '{'
            +   'position:fixed;inset:0;z-index:10000;'
            +   'display:flex;align-items:center;justify-content:center;'
            +   'background:rgba(0,0,0,.6);'
            +   'backdrop-filter:blur(6px);-webkit-backdrop-filter:blur(6px);'
            +   'opacity:0;visibility:hidden;'
            +   'transition:opacity .3s ease,visibility .3s ease;'
            + '}'
            + '#' + MODAL_ID + '.lm-show{opacity:1;visibility:visible;}'

            /* Modal box */
            + '.lm-box{'
            +   'position:relative;z-index:1;'
            +   'width:100%;max-width:380px;'
            +   'background:#110e14;'
            +   'border:1px solid #2a2432;'
            +   'border-radius:14px;'
            +   'padding:36px 32px 28px;'
            +   'transform:translateY(24px) scale(.97);'
            +   'transition:transform .35s cubic-bezier(.16,1,.3,1);'
            +   'box-shadow:0 24px 64px rgba(0,0,0,.55);'
            + '}'
            + '#' + MODAL_ID + '.lm-show .lm-box{transform:translateY(0) scale(1);}'

            /* Close button */
            + '.lm-close{'
            +   'position:absolute;top:12px;right:14px;'
            +   'background:none;border:none;cursor:pointer;'
            +   'color:#5a5462;font-size:20px;line-height:1;'
            +   'transition:color .2s;'
            + '}'
            + '.lm-close:hover{color:#f0ece4;}'

            /* Brand */
            + '.lm-brand{'
            +   'font-family:"Syne",sans-serif;'
            +   'font-size:11px;font-weight:700;'
            +   'letter-spacing:.2em;text-transform:uppercase;'
            +   'color:#d4a24e;margin-bottom:18px;'
            +   'display:flex;align-items:center;gap:8px;'
            + '}'
            + '.lm-brand::before{'
            +   'content:"";display:inline-block;'
            +   'width:20px;height:2px;background:#d4a24e;border-radius:1px;'
            + '}'

            /* Title */
            + '.lm-title{'
            +   'font-family:"Syne",sans-serif;'
            +   'font-size:24px;font-weight:800;'
            +   'color:#f0ece4;margin-bottom:24px;'
            +   'letter-spacing:-.02em;line-height:1.2;'
            + '}'

            /* Input group */
            + '.lm-field{margin-bottom:14px;}'
            + '.lm-field label{'
            +   'display:block;font-size:11px;font-weight:500;'
            +   'letter-spacing:.08em;color:#5a5462;'
            +   'margin-bottom:6px;text-transform:uppercase;'
            +   'font-family:"DM Mono",monospace;'
            + '}'
            + '.lm-field input{'
            +   'width:100%;height:46px;'
            +   'padding:0 14px;'
            +   'font-family:"DM Mono",monospace;font-size:14px;'
            +   'color:#f0ece4;background:#1a161f;'
            +   'border:1.5px solid #2a2432;border-radius:10px;'
            +   'outline:none;transition:all .3s ease;'
            +   'caret-color:#d4a24e;'
            + '}'
            + '.lm-field input::placeholder{color:#5a5462;font-size:13px;}'
            + '.lm-field input:focus{'
            +   'background:#211c28;border-color:#d4a24e;'
            +   'box-shadow:0 0 0 3px rgba(212,162,78,.15);'
            + '}'

            /* Submit */
            + '.lm-submit{'
            +   'width:100%;height:46px;margin-top:8px;'
            +   'font-family:"Syne",sans-serif;'
            +   'font-size:14px;font-weight:700;'
            +   'letter-spacing:.1em;text-transform:uppercase;'
            +   'color:#08060a;background:#d4a24e;'
            +   'border:none;border-radius:10px;cursor:pointer;'
            +   'transition:all .3s ease;'
            + '}'
            + '.lm-submit:hover{'
            +   'transform:translateY(-1px);'
            +   'box-shadow:0 8px 24px rgba(212,162,78,.25);'
            + '}'
            + '.lm-submit:active{transform:translateY(0) scale(.98);}'
            + '.lm-submit:disabled{opacity:.6;cursor:not-allowed;transform:none;box-shadow:none;}'

            /* Error message */
            + '.lm-error{'
            +   'color:#e74c3c;font-size:12px;'
            +   'text-align:center;margin-top:10px;'
            +   'min-height:18px;'
            +   'font-family:"DM Mono",monospace;'
            + '}'

            /* User badge (top-right) */
            + '.lm-user-badge{'
            +   'display:flex;align-items:center;gap:8px;'
            +   'cursor:pointer;padding:6px 14px;'
            +   'border-radius:8px;'
            +   'background:rgba(212,162,78,.08);'
            +   'border:1px solid rgba(212,162,78,.15);'
            +   'transition:all .25s ease;'
            + '}'
            + '.lm-user-badge:hover{'
            +   'background:rgba(212,162,78,.15);'
            +   'border-color:rgba(212,162,78,.3);'
            + '}'
            + '.lm-user-avatar{'
            +   'width:28px;height:28px;border-radius:50%;'
            +   'background:#d4a24e;color:#08060a;'
            +   'display:flex;align-items:center;justify-content:center;'
            +   'font-family:"Syne",sans-serif;font-size:12px;font-weight:700;'
            + '}'
            + '.lm-user-name{'
            +   'font-family:"DM Mono",monospace;'
            +   'font-size:13px;color:#f0ece4;'
            + '}'
            + '.lm-user-arrow{'
            +   'color:#5a5462;font-size:10px;transition:transform .2s;'
            + '}'
            + '.lm-user-badge.lm-open .lm-user-arrow{transform:rotate(180deg);}'

            /* Dropdown */
            + '.lm-dropdown{'
            +   'position:absolute;top:calc(100% + 6px);right:0;'
            +   'min-width:140px;background:#1a161f;'
            +   'border:1px solid #2a2432;border-radius:8px;'
            +   'overflow:hidden;'
            +   'opacity:0;visibility:hidden;transform:translateY(-6px);'
            +   'transition:all .2s ease;'
            + '}'
            + '.lm-dropdown.lm-dd-show{opacity:1;visibility:visible;transform:translateY(0);}'
            + '.lm-dropdown-item{'
            +   'display:block;width:100%;padding:10px 16px;'
            +   'background:none;border:none;cursor:pointer;'
            +   'font-family:"DM Mono",monospace;font-size:13px;'
            +   'color:#8a8391;text-align:left;'
            +   'transition:all .15s ease;'
            + '}'
            + '.lm-dropdown-item:hover{background:#211c28;color:#f0ece4;}'
            + '.lm-dropdown-item.lm-dd-danger:hover{color:#e74c3c;}';

        var style = document.createElement('style');
        style.id = STYLE_ID;
        style.textContent = css;
        document.head.appendChild(style);
    }

    /* ==================== DOM ==================== */

    function injectModal() {
        if (document.getElementById(MODAL_ID)) return;

        var overlay = document.createElement('div');
        overlay.id = MODAL_ID;
        overlay.innerHTML = ''
            + '<div class="lm-box">'
            +   '<button class="lm-close" id="lmClose" aria-label="关闭">&times;</button>'
            +   '<div class="lm-brand">LocalPicma</div>'
            +   '<div class="lm-title">登录账户</div>'
            +   '<form id="lmForm" autocomplete="off">'
            +     '<div class="lm-field">'
            +       '<label for="lmUsername">用户名</label>'
            +       '<input type="text" id="lmUsername" placeholder="输入用户名" autocomplete="username">'
            +     '</div>'
            +     '<div class="lm-field">'
            +       '<label for="lmPassword">密码</label>'
            +       '<input type="password" id="lmPassword" placeholder="输入密码" autocomplete="current-password">'
            +     '</div>'
            +     '<button type="submit" class="lm-submit" id="lmSubmit">登 录</button>'
            +   '</form>'
            +   '<div class="lm-error" id="lmError"></div>'
            + '</div>';

        document.body.appendChild(overlay);

        /* Events */
        overlay.addEventListener('click', function (e) {
            if (e.target === overlay) hide();
        });
        document.getElementById('lmClose').addEventListener('click', hide);
        document.getElementById('lmForm').addEventListener('submit', handleSubmit);

        /* Close on Escape */
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && overlay.classList.contains('lm-show')) hide();
        });
    }

    /* ==================== SHOW / HIDE ==================== */

    function show() {
        var overlay = document.getElementById(MODAL_ID);
        if (!overlay) return;
        document.getElementById('lmError').textContent = '';
        document.getElementById('lmUsername').value = '';
        document.getElementById('lmPassword').value = '';
        document.getElementById('lmSubmit').disabled = false;
        document.getElementById('lmSubmit').textContent = '登 录';
        overlay.classList.add('lm-show');
        setTimeout(function () {
            document.getElementById('lmUsername').focus();
        }, 100);
    }

    function hide() {
        var overlay = document.getElementById(MODAL_ID);
        if (overlay) overlay.classList.remove('lm-show');
    }

    /* ==================== LOGIN SUBMIT ==================== */

    function handleSubmit(e) {
        e.preventDefault();
        var username = document.getElementById('lmUsername').value.trim();
        var password = document.getElementById('lmPassword').value;
        var errorEl  = document.getElementById('lmError');
        var submitBtn = document.getElementById('lmSubmit');

        errorEl.textContent = '';
        if (!username) { errorEl.textContent = '请输入用户名'; return; }
        if (!password) { errorEl.textContent = '请输入密码'; return; }

        submitBtn.disabled = true;
        submitBtn.textContent = '登录中...';

        fetch('/apilogin', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: username, password: password })
        })
        .then(function (res) { return res.json(); })
        .then(function (data) {
            if (!data.token) {
                throw new Error(data.error || '登录失败，请检查用户名和密码');
            }
            Auth.setAuth(data.token, data.username);
            hide();
            renderUserBadge(data.username);
        })
        .catch(function (err) {
            errorEl.textContent = err.message || '网络错误，请稍后重试';
            submitBtn.disabled = false;
            submitBtn.textContent = '登 录';
        });
    }

    /* ==================== USER BADGE ==================== */

    function renderUserBadge(username) {
        /* Remove existing badge and login button if any */
        var old = document.getElementById('lmUserBadgeWrap');
        if (old) old.remove();
        var loginBtn = document.getElementById('lmLoginBtn');
        if (loginBtn) loginBtn.remove();

        var container = document.createElement('div');
        container.id = 'lmUserBadgeWrap';
        container.style.position = 'relative';

        var initial = (username || '?').charAt(0).toUpperCase();

        container.innerHTML = ''
            + '<div class="lm-user-badge" id="lmUserBadge">'
            +   '<div class="lm-user-avatar">' + initial + '</div>'
            +   '<span class="lm-user-name">' + escapeHtml(username) + '</span>'
            +   '<span class="lm-user-arrow">&#9662;</span>'
            + '</div>'
            + '<div class="lm-dropdown" id="lmDropdown">'
            +   '<button class="lm-dropdown-item lm-dd-danger" id="lmLogout">退出登录</button>'
            + '</div>';

        /* Insert into header — try multiple strategies */
        var inserted = false;
        /* Strategy 1: find .header-inner (homePage) */
        var headerInner = document.querySelector('.header-inner');
        if (headerInner) {
            headerInner.appendChild(container);
            inserted = true;
        }
        /* Strategy 2: find nav-menu and insert after */
        if (!inserted) {
            var navMenu = document.querySelector('.nav-menu');
            if (navMenu && navMenu.parentNode) {
                navMenu.parentNode.appendChild(container);
                inserted = true;
            }
        }
        /* Strategy 3: fallback — fixed top-right */
        if (!inserted) {
            container.style.position = 'fixed';
            container.style.top = '14px';
            container.style.right = '20px';
            container.style.zIndex = '9999';
            document.body.appendChild(container);
        }

        /* Dropdown toggle */
        var badge = document.getElementById('lmUserBadge');
        var dropdown = document.getElementById('lmDropdown');
        badge.addEventListener('click', function (e) {
            e.stopPropagation();
            var isOpen = dropdown.classList.contains('lm-dd-show');
            dropdown.classList.toggle('lm-dd-show', !isOpen);
            badge.classList.toggle('lm-open', !isOpen);
        });
        document.addEventListener('click', function () {
            dropdown.classList.remove('lm-dd-show');
            badge.classList.remove('lm-open');
        });

        /* Logout */
        document.getElementById('lmLogout').addEventListener('click', function () {
            Auth.logout();
        });
    }

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.appendChild(document.createTextNode(str));
        return div.innerHTML;
    }

    /* ==================== INIT ==================== */

    function init() {
        if (injected) return;
        injected = true;

        injectStyles();
        injectModal();

        /* Add "Login" button to header if not logged in, or show badge if logged in */
        Auth.checkToken().then(function (valid) {
            if (valid) {
                var username = localStorage.getItem('username') || 'User';
                renderUserBadge(username);
            } else {
                addLoginButton();
            }
        });
    }

    function addLoginButton() {
        if (document.getElementById('lmLoginBtn')) return;

        var btn = document.createElement('div');
        btn.id = 'lmLoginBtn';
        btn.className = 'lm-user-badge';
        btn.style.cursor = 'pointer';
        btn.innerHTML = ''
            + '<div class="lm-user-avatar" style="background:#2a2432;color:#d4a24e;font-size:14px">+</div>'
            + '<span class="lm-user-name">登录</span>';

        btn.addEventListener('click', function () {
            show();
        });

        /* Insert into header */
        var headerInner = document.querySelector('.header-inner');
        if (headerInner) {
            headerInner.appendChild(btn);
        } else {
            btn.style.position = 'fixed';
            btn.style.top = '14px';
            btn.style.right = '20px';
            btn.style.zIndex = '9999';
            document.body.appendChild(btn);
        }
    }

    /* ==================== PUBLIC API ==================== */

    return {
        init: init,
        show: show,
        hide: hide
    };

})();
