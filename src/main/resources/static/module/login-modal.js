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

            /* Options row (remember me / auto login) */
            + '.lm-options{'
            +   'display:flex;align-items:center;gap:16px;'
            +   'margin:4px 0 0;'
            + '}'
            + '.lm-chk{'
            +   'display:inline-flex;align-items:center;gap:5px;'
            +   'cursor:pointer;font-size:12px;color:#8a8391;'
            +   'font-family:"DM Mono",monospace;user-select:none;'
            + '}'
            + '.lm-chk input{display:none;}'
            + '.lm-chk .lm-chk-box{'
            +   'width:14px;height:14px;border-radius:3px;'
            +   'border:1.5px solid #5a5462;display:flex;'
            +   'align-items:center;justify-content:center;'
            +   'transition:all .2s;flex-shrink:0;'
            + '}'
            + '.lm-chk input:checked+.lm-chk-box{'
            +   'background:#d4a24e;border-color:#d4a24e;'
            + '}'
            + '.lm-chk .lm-chk-box svg{display:none;width:10px;height:10px;}'
            + '.lm-chk input:checked+.lm-chk-box svg{display:block;}'

            /* Footer (register link) */
            + '.lm-footer{'
            +   'text-align:center;margin-top:14px;'
            +   'font-family:"DM Mono",monospace;font-size:12px;'
            + '}'
            + '.lm-link{'
            +   'color:#8a8391;text-decoration:none;'
            +   'transition:color .2s;'
            + '}'
            + '.lm-link:hover{color:#d4a24e;}'

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
            + '.lm-dropdown-item.lm-dd-danger:hover{color:#e74c3c;}'

            /* ========== PROFILE MODAL ========== */
            + '.lm-overlay{'
            +   'position:fixed;inset:0;z-index:10000;'
            +   'display:flex;align-items:center;justify-content:center;'
            +   'background:rgba(0,0,0,.6);'
            +   'backdrop-filter:blur(6px);-webkit-backdrop-filter:blur(6px);'
            +   'opacity:0;visibility:hidden;'
            +   'transition:opacity .3s ease,visibility .3s ease;'
            + '}'
            + '.lm-overlay.lm-show{opacity:1;visibility:visible;}'
            + '.lm-modal{'
            +   'position:relative;z-index:1;'
            +   'width:100%;max-width:440px;'
            +   'background:#110e14;'
            +   'border:1px solid #2a2432;'
            +   'border-radius:14px;'
            +   'padding:36px 32px 28px;'
            +   'transform:translateY(24px) scale(.97);'
            +   'transition:transform .35s cubic-bezier(.16,1,.3,1);'
            +   'box-shadow:0 24px 64px rgba(0,0,0,.55);'
            +   'max-height:85vh;overflow-y:auto;'
            + '}'
            + '.lm-overlay.lm-show .lm-modal{transform:translateY(0) scale(1);}'
            + '.lm-pf-row{'
            +   'display:flex;gap:12px;'
            + '}'
            + '.lm-pf-row .lm-field{flex:1;}'
            + '.lm-field input[readonly]{'
            +   'opacity:.5;cursor:not-allowed;'
            + '}'
            + '.lm-btn-row{'
            +   'display:flex;gap:10px;margin-top:12px;'
            + '}'
            + '.lm-btn-secondary{'
            +   'flex:1;height:46px;'
            +   'font-family:"Syne",sans-serif;font-size:13px;font-weight:700;'
            +   'letter-spacing:.06em;text-transform:uppercase;'
            +   'color:#d4a24e;background:transparent;'
            +   'border:1.5px solid #d4a24e;border-radius:10px;cursor:pointer;'
            +   'transition:all .25s ease;'
            + '}'
            + '.lm-btn-secondary:hover{background:rgba(212,162,78,.1);}'
            + '.lm-submit.lm-btn-half{flex:1;margin-top:0;}'
            + '.lm-msg{'
            +   'text-align:center;font-size:12px;margin-top:10px;min-height:18px;'
            +   'font-family:"DM Mono",monospace;'
            + '}'
            + '.lm-msg.lm-msg-ok{color:#6b9e8a;}'
            + '.lm-msg.lm-msg-err{color:#e74c3c;}'

            /* ========== NAV FAB ========== */
            + '.lm-nav-fab{'
            +   'position:fixed;bottom:24px;left:0;z-index:10000;'
            +   'transform:translateX(0);'
            +   'transition:transform .35s cubic-bezier(.16,1,.3,1);'
            + '}'
            + '.lm-nav-fab:hover,.lm-nav-fab.lm-nav-open{'
            +   'transform:translateX(8px);'
            + '}'
            + '.lm-nav-btn{'
            +   'width:44px;height:44px;border-radius:0 22px 22px 0;'
            +   'background:#110e14;border:1px solid #2a2432;border-left:none;'
            +   'color:#d4a24e;font-size:20px;cursor:pointer;'
            +   'display:flex;align-items:center;justify-content:center;'
            +   'transition:all .25s;box-shadow:4px 0 16px rgba(0,0,0,.3);'
            + '}'
            + '.lm-nav-fab:hover .lm-nav-btn,.lm-nav-fab.lm-nav-open .lm-nav-btn{'
            +   'background:#1a161f;box-shadow:4px 0 24px rgba(0,0,0,.5);'
            + '}'
            + '.lm-nav-menu{'
            +   'position:absolute;bottom:52px;left:0;'
            +   'min-width:140px;background:#110e14;'
            +   'border:1px solid #2a2432;border-radius:10px;'
            +   'overflow:hidden;opacity:0;visibility:hidden;'
            +   'transform:translateY(8px);'
            +   'transition:all .25s cubic-bezier(.16,1,.3,1);'
            +   'box-shadow:0 -8px 32px rgba(0,0,0,.5);'
            + '}'
            + '.lm-nav-fab.lm-nav-open .lm-nav-menu{'
            +   'opacity:1;visibility:visible;transform:translateY(0);'
            + '}'
            + '.lm-nav-item{'
            +   'display:block;width:100%;padding:10px 16px;'
            +   'background:none;border:none;cursor:pointer;'
            +   'font-family:"DM Mono",monospace;font-size:13px;'
            +   'color:#8a8391;text-align:left;text-decoration:none;'
            +   'transition:all .15s;'
            + '}'
            + '.lm-nav-item:hover{background:#1a161f;color:#d4a24e;}'
            /* Submenu group */
            + '.lm-nav-group{position:relative;}'
            + '.lm-nav-parent{'
            +   'display:flex;align-items:center;justify-content:space-between;'
            +   'width:100%;padding:10px 16px;'
            +   'background:none;border:none;cursor:pointer;'
            +   'font-family:"DM Mono",monospace;font-size:13px;'
            +   'color:#8a8391;text-align:left;'
            +   'transition:all .15s;'
            + '}'
            + '.lm-nav-parent:hover{background:#1a161f;color:#d4a24e;}'
            + '.lm-nav-parent .arrow{font-size:10px;transition:transform .2s;}'
            + '.lm-nav-group.lm-nav-expanded>.lm-nav-parent .arrow{transform:rotate(90deg);}'
            + '.lm-nav-children{'
            +   'display:none;padding-left:12px;'
            +   'border-left:1px solid #2a2432;margin-left:20px;'
            + '}'
            + '.lm-nav-group.lm-nav-expanded>.lm-nav-children{display:block;}'

            /* 移动端适配 */
            + '@media (max-width: 768px) {'
            +   '.lm-nav-fab { bottom: 16px; }'
            +   '.lm-user-badge { padding: 4px 10px; }'
            +   '.lm-user-avatar { width: 24px; height: 24px; font-size: 10px; }'
            +   '.lm-user-name { font-size: 12px; }'
            + '}'
            + '@media (max-width: 480px) {'
            +   '.lm-nav-fab { bottom: 12px; }'
            +   '.lm-user-badge { padding: 3px 8px; }'
            +   '.lm-user-avatar { width: 20px; height: 20px; font-size: 9px; }'
            +   '.lm-user-name { font-size: 11px; }'
            + '}'

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
            +     '<div class="lm-options">'
            +       '<label class="lm-chk"><input type="checkbox" id="lmRemember"><span class="lm-chk-box"><svg viewBox="0 0 12 12" fill="none" stroke="#08060a" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="2 6 5 9 10 3"/></svg></span>记住密码</label>'
            +       '<label class="lm-chk"><input type="checkbox" id="lmAutoLogin"><span class="lm-chk-box"><svg viewBox="0 0 12 12" fill="none" stroke="#08060a" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="2 6 5 9 10 3"/></svg></span>自动登录</label>'
            +     '</div>'
            +     '<button type="submit" class="lm-submit" id="lmSubmit">登 录</button>'
            +   '</form>'
            +   '<div class="lm-error" id="lmError"></div>'
            +   '<div class="lm-footer">'
            +     '<a href="/register" class="lm-link">没有账号？去注册</a>'
            +   '</div>'
            + '</div>';

        document.body.appendChild(overlay);

        /* Events — close only if mousedown also started on overlay (prevents drag-to-close) */
        overlay.addEventListener('mousedown', function (e) {
            overlay._downOnOverlay = (e.target === overlay);
        });
        overlay.addEventListener('click', function (e) {
            if (e.target === overlay && overlay._downOnOverlay) hide();
        });
        document.getElementById('lmClose').addEventListener('click', hide);
        document.getElementById('lmForm').addEventListener('submit', handleSubmit);

        /* Checkbox联动: 自动登录 → 记住密码 */
        document.getElementById('lmAutoLogin').addEventListener('change', function () {
            if (this.checked) document.getElementById('lmRemember').checked = true;
        });
        document.getElementById('lmRemember').addEventListener('change', function () {
            if (!this.checked) document.getElementById('lmAutoLogin').checked = false;
        });

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

        // 预填已保存的凭据 + 恢复勾选状态
        var saved = Auth.loadCredentials();
        if (saved) {
            if (saved.username) document.getElementById('lmUsername').value = saved.username;
            if (saved.password) document.getElementById('lmPassword').value = saved.password;
            document.getElementById('lmRemember').checked = saved.remember;
            document.getElementById('lmAutoLogin').checked = saved.autoLogin;
        } else {
            document.getElementById('lmRemember').checked = false;
            document.getElementById('lmAutoLogin').checked = false;
        }

        overlay.classList.add('lm-show');
        setTimeout(function () {
            var u = document.getElementById('lmUsername');
            // 自动登录：有凭据且勾选了自动登录，直接提交
            if (saved && saved.autoLogin && saved.username && saved.password) {
                document.getElementById('lmForm').dispatchEvent(new Event('submit'));
            } else if (u.value) {
                document.getElementById('lmPassword').focus();
            } else {
                u.focus();
            }
        }, 150);
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

        fetch('/page/login/api/login', {
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
            // 根据勾选状态保存/清除凭据
            var remember = document.getElementById('lmRemember').checked;
            var auto = document.getElementById('lmAutoLogin').checked;
            if (remember || auto) {
                Auth.saveCredentials(username, password, remember, auto);
            } else {
                Auth.clearCredentials();
            }
            hide();
            renderUserBadge(data.username);
            try { localStorage.removeItem(NAV_MENU_KEY); } catch (e) {}
            loadNavMenus();
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
            +   '<button class="lm-dropdown-item" id="lmProfile">个人信息</button>'
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
            // 移动端适配 — 避开状态栏，下移 48px
            if (window.innerWidth <= 480) {
                container.style.top = '62px';
                container.style.right = '12px';
            } else if (window.innerWidth <= 768) {
                container.style.top = '62px';
                container.style.right = '16px';
            }
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

        /* Profile */
        document.getElementById('lmProfile').addEventListener('click', function () {
            showProfileModal();
        });
    }

    /* ==================== PROFILE MODAL ==================== */

    function showProfileModal() {
        /* Close dropdown */
        var dropdown = document.getElementById('lmDropdown');
        if (dropdown) dropdown.classList.remove('lm-dd-show');

        /* Remove existing profile modal if any */
        var old = document.getElementById('lmProfileOverlay');
        if (old) old.remove();

        var overlay = document.createElement('div');
        overlay.id = 'lmProfileOverlay';
        overlay.className = 'lm-overlay';
        overlay.innerHTML = ''
            + '<div class="lm-modal">'
            +   '<button class="lm-close" id="lmPfClose">&times;</button>'
            +   '<div class="lm-brand">LocalPicma</div>'
            +   '<div class="lm-title">个人信息</div>'
            +   '<form id="lmPfForm" autocomplete="off">'
            +     '<div class="lm-field">'
            +       '<label>账号</label>'
            +       '<input type="text" id="lmPfUsername" readonly>'
            +     '</div>'
            +     '<div class="lm-field">'
            +       '<label>用户名称</label>'
            +       '<input type="text" id="lmPfDisplayname" placeholder="输入用户名称">'
            +     '</div>'
            +     '<div class="lm-pf-row">'
            +       '<div class="lm-field">'
            +         '<label>QQ号</label>'
            +         '<input type="text" id="lmPfQQ" placeholder="输入QQ号" inputmode="numeric" maxlength="14" pattern="\\d*">'
            +       '</div>'
            +       '<div class="lm-field">'
            +         '<label>Steam ID</label>'
            +         '<input type="text" id="lmPfSteam" placeholder="输入Steam ID" inputmode="numeric" maxlength="20" pattern="\\d*">'
            +       '</div>'
            +     '</div>'
            +     '<div class="lm-field">'
            +       '<label>角色</label>'
            +       '<input type="text" id="lmPfRole" readonly>'
            +     '</div>'
            +     '<div class="lm-btn-row">'
            +       '<button type="button" class="lm-btn-secondary" id="lmPfChangePwd">修改密码</button>'
            +       '<button type="submit" class="lm-submit lm-btn-half" id="lmPfSave">保 存</button>'
            +     '</div>'
            +   '</form>'
            +   '<div class="lm-msg" id="lmPfMsg"></div>'
            + '</div>';

        document.body.appendChild(overlay);

        /* Load data */
        loadProfileData();

        /* Events — close only if mousedown also started on overlay */
        overlay.addEventListener('mousedown', function (e) {
            overlay._downOnOverlay = (e.target === overlay);
        });
        overlay.addEventListener('click', function (e) {
            if (e.target === overlay && overlay._downOnOverlay) closeProfileModal();
        });
        document.getElementById('lmPfClose').addEventListener('click', closeProfileModal);
        document.getElementById('lmPfForm').addEventListener('submit', handleProfileSave);
        document.getElementById('lmPfChangePwd').addEventListener('click', showPasswordModal);

        /* Show */
        requestAnimationFrame(function () { overlay.classList.add('lm-show'); });
    }

    function closeProfileModal() {
        var overlay = document.getElementById('lmProfileOverlay');
        if (overlay) {
            overlay.classList.remove('lm-show');
            setTimeout(function () { overlay.remove(); }, 350);
        }
    }

    function loadProfileData() {
        Auth.authFetch('/page/login/api/user/info')
            .then(function (res) { return res.json(); })
            .then(function (data) {
                if (data.error) throw new Error(data.error);
                document.getElementById('lmPfUsername').value = data.user_name || '';
                document.getElementById('lmPfDisplayname').value = data.displayname || '';
                document.getElementById('lmPfQQ').value = data.qq_number || '';
                document.getElementById('lmPfSteam').value = data.steam_uuid || '';
                document.getElementById('lmPfRole').value = data.role || '';
            })
            .catch(function (err) {
                showPfMsg(err.message || '加载失败', true);
            });
    }

    function handleProfileSave(e) {
        e.preventDefault();
        var displayname = document.getElementById('lmPfDisplayname').value.trim();
        var qq = document.getElementById('lmPfQQ').value.trim();
        var steam = document.getElementById('lmPfSteam').value.trim();

        if (!displayname) {
            showPfMsg('用户名称不能为空', true);
            document.getElementById('lmPfDisplayname').focus();
            return;
        }
        if (qq && !/^\d{1,14}$/.test(qq)) {
            showPfMsg('QQ号仅限数字，最长14位', true);
            return;
        }
        if (steam && !/^\d{1,20}$/.test(steam)) {
            showPfMsg('Steam ID仅限数字，最长20位', true);
            return;
        }

        var body = {
            displayname: displayname,
            qq_number:   qq,
            steam_uuid:  steam
        };

        Auth.authFetch('/page/login/api/user/update', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        })
        .then(function (res) { return res.json(); })
        .then(function (data) {
            if (data.error) throw new Error(data.error);
            showPfMsg('保存成功', false);
            /* Update displayed username if displayname changed */
            if (body.displayname) {
                var badgeName = document.querySelector('.lm-user-name');
                if (badgeName) badgeName.textContent = body.displayname;
                localStorage.setItem('username', body.displayname);
            }
            /* Auto-close after short delay */
            setTimeout(closeProfileModal, 800);
        })
        .catch(function (err) {
            showPfMsg(err.message || '保存失败', true);
        });
    }

    function showPfMsg(text, isError) {
        var el = document.getElementById('lmPfMsg');
        if (!el) return;
        el.textContent = text;
        el.className = 'lm-msg ' + (isError ? 'lm-msg-err' : 'lm-msg-ok');
    }

    /* ==================== PASSWORD MODAL ==================== */

    function showPasswordModal() {
        var old = document.getElementById('lmPwdOverlay');
        if (old) old.remove();

        var overlay = document.createElement('div');
        overlay.id = 'lmPwdOverlay';
        overlay.className = 'lm-overlay';
        overlay.style.zIndex = '10001';
        overlay.innerHTML = ''
            + '<div class="lm-modal" style="max-width:380px">'
            +   '<button class="lm-close" id="lmPwdClose">&times;</button>'
            +   '<div class="lm-brand">LocalPicma</div>'
            +   '<div class="lm-title">修改密码</div>'
            +   '<form id="lmPwdForm" autocomplete="off">'
            +     '<div class="lm-field">'
            +       '<label>原密码</label>'
            +       '<input type="password" id="lmPwdOld" placeholder="输入原密码" autocomplete="current-password">'
            +     '</div>'
            +     '<div class="lm-field">'
            +       '<label>新密码</label>'
            +       '<input type="password" id="lmPwdNew" placeholder="输入新密码（至少6位）" autocomplete="new-password">'
            +     '</div>'
            +     '<div class="lm-field">'
            +       '<label>确认新密码</label>'
            +       '<input type="password" id="lmPwdConfirm" placeholder="再次输入新密码" autocomplete="new-password">'
            +     '</div>'
            +     '<button type="submit" class="lm-submit" id="lmPwdSubmit">确认修改</button>'
            +   '</form>'
            +   '<div class="lm-msg" id="lmPwdMsg"></div>'
            + '</div>';

        document.body.appendChild(overlay);

        /* Events — close only if mousedown also started on overlay */
        overlay.addEventListener('mousedown', function (e) {
            overlay._downOnOverlay = (e.target === overlay);
        });
        overlay.addEventListener('click', function (e) {
            if (e.target === overlay && overlay._downOnOverlay) closePasswordModal();
        });
        document.getElementById('lmPwdClose').addEventListener('click', closePasswordModal);
        document.getElementById('lmPwdForm').addEventListener('submit', handlePasswordChange);

        requestAnimationFrame(function () { overlay.classList.add('lm-show'); });
        setTimeout(function () { document.getElementById('lmPwdOld').focus(); }, 150);
    }

    function closePasswordModal() {
        var overlay = document.getElementById('lmPwdOverlay');
        if (overlay) {
            overlay.classList.remove('lm-show');
            setTimeout(function () { overlay.remove(); }, 350);
        }
    }

    function handlePasswordChange(e) {
        e.preventDefault();
        var msg = document.getElementById('lmPwdMsg');
        msg.textContent = '';

        var oldPwd = document.getElementById('lmPwdOld').value;
        var newPwd = document.getElementById('lmPwdNew').value;
        var confirm = document.getElementById('lmPwdConfirm').value;

        if (!oldPwd) { msg.textContent = '请输入原密码'; msg.className = 'lm-msg lm-msg-err'; return; }
        if (!newPwd || newPwd.length < 6) { msg.textContent = '新密码长度不能少于6位'; msg.className = 'lm-msg lm-msg-err'; return; }
        if (newPwd !== confirm) { msg.textContent = '两次输入的密码不一致'; msg.className = 'lm-msg lm-msg-err'; return; }

        var btn = document.getElementById('lmPwdSubmit');
        btn.disabled = true;
        btn.textContent = '提交中...';

        Auth.authFetch('/page/login/api/user/change-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ oldPassword: oldPwd, newPassword: newPwd })
        })
        .then(function (res) { return res.json(); })
        .then(function (data) {
            if (data.error) throw new Error(data.error);
            msg.textContent = '密码修改成功，请重新登录';
            msg.className = 'lm-msg lm-msg-ok';
            setTimeout(function () {
                closePasswordModal();
                closeProfileModal();
                Auth.logout();
            }, 1500);
        })
        .catch(function (err) {
            msg.textContent = err.message || '修改失败';
            msg.className = 'lm-msg lm-msg-err';
            btn.disabled = false;
            btn.textContent = '确认修改';
        });
    }

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.appendChild(document.createTextNode(str));
        return div.innerHTML;
    }

    /* ==================== NAV FAB ==================== */

    var NAV_MENU_KEY = 'lm_nav_menus';

    function injectNavFab() {
        if (document.getElementById('lmNavFab')) return;

        var fab = document.createElement('div');
        fab.id = 'lmNavFab';
        fab.className = 'lm-nav-fab';
        fab.innerHTML = ''
            + '<div class="lm-nav-menu" id="lmNavMenu"></div>'
            + '<button class="lm-nav-btn" id="lmNavBtn" title="导航菜单">&#9776;</button>';

        document.body.appendChild(fab);

        var btn = document.getElementById('lmNavBtn');
        var menu = document.getElementById('lmNavMenu');

        /* Toggle */
        btn.addEventListener('click', function (e) {
            e.stopPropagation();
            fab.classList.toggle('lm-nav-open');
        });

        /* Close on outside click */
        document.addEventListener('click', function (e) {
            if (!fab.contains(e.target)) {
                fab.classList.remove('lm-nav-open');
            }
        });

        /* Load menus */
        loadNavMenus();
    }

    function loadNavMenus() {
        var menu = document.getElementById('lmNavMenu');
        if (!menu) return;

        var headers = {};
        var token = Auth.getToken();
        if (token) headers['Authorization'] = 'Bearer ' + token;

        fetch('/page/admin/api/nav/menus', { headers: headers })
            .then(function (res) {
                if (!res.ok) throw new Error('not ok');
                return res.json();
            })
            .then(function (data) {
                if (!data || !data.length) throw new Error('empty');
                try { localStorage.setItem(NAV_MENU_KEY, JSON.stringify(data)); } catch (e) {}
                renderNavMenu(data);
            })
            .catch(function () {
                try {
                    var cached = JSON.parse(localStorage.getItem(NAV_MENU_KEY));
                    if (cached && cached.length) { renderNavMenu(cached); return; }
                } catch (e) {}
                renderNavMenu([
                    { menu_name: '首页', path: '/home', parent_id: '0' },
                    { menu_name: '后台管理', path: '/admin', parent_id: '0' }
                ]);
            });
    }

    function renderNavMenu(items) {
        var menu = document.getElementById('lmNavMenu');
        if (!menu) return;

        /* Build tree from flat list */
        var map = {};
        var roots = [];
        for (var i = 0; i < items.length; i++) {
            var m = items[i];
            m._children = [];
            var key = m.id || m.menu_code || m.menu_name;
            if (key) map[key] = m;
        }
        for (var i = 0; i < items.length; i++) {
            var m = items[i];
            var pid = m.parent_id && m.parent_id !== '0' ? m.parent_id : null;
            if (pid && map[pid]) {
                map[pid]._children.push(m);
            } else {
                roots.push(m);
            }
        }

        menu.innerHTML = buildNavTree(roots);

        /* Bind expand/collapse for groups */
        var parents = menu.querySelectorAll('.lm-nav-parent');
        for (var p = 0; p < parents.length; p++) {
            parents[p].addEventListener('click', function (e) {
                e.stopPropagation();
                var group = this.parentNode;
                group.classList.toggle('lm-nav-expanded');
            });
        }
    }

    function buildNavTree(nodes) {
        var html = '';
        for (var i = 0; i < nodes.length; i++) {
            var m = nodes[i];
            var name = escapeHtml(m.menu_name);
            var icon = m.icon ? '<span style="margin-right:4px">' + escapeHtml(m.icon) + '</span>'
                     : m.is_folder ? '<span style="margin-right:4px">📁</span>' : '';
            if (m._children && m._children.length > 0) {
                html += '<div class="lm-nav-group">';
                html += '<button class="lm-nav-parent">' + icon + name + '<span class="arrow">&#9656;</span></button>';
                html += '<div class="lm-nav-children">' + buildNavTree(m._children) + '</div>';
                html += '</div>';
            } else if (m.path) {
                html += '<a class="lm-nav-item" href="' + escapeHtml(m.path) + '">' + icon + name + '</a>';
            }
        }
        return html;
    }

    /* ==================== INIT ==================== */

    function init() {
        if (injected) return;
        injected = true;

        injectStyles();
        injectModal();
        injectNavFab();

        /* 校验 token，失败则尝试自动登录 */
        Auth.validateOrAutoLogin().then(function (valid) {
            if (valid) {
                var username = localStorage.getItem('username') || 'User';
                renderUserBadge(username);
                // 重新加载菜单（token 可能已刷新）
                try { localStorage.removeItem(NAV_MENU_KEY); } catch (e) {}
                loadNavMenus();
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
            // 移动端适配 — 避开状态栏，下移 48px
            if (window.innerWidth <= 480) {
                btn.style.top = '62px';
                btn.style.right = '12px';
            } else if (window.innerWidth <= 768) {
                btn.style.top = '62px';
                btn.style.right = '16px';
            }
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
