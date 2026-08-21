var Auth = (function() {

    var TOKEN_KEY    = 'token';
    var USER_KEY     = 'username';
    var REDIRECT_KEY = 'redirectAfterLogin';
    var CRED_KEY     = 'lp_cred';

    // ==================== 加密/解密 ====================
    // 使用 XOR + Base64 进行简单混淆（防肉眼窥探，非密码学安全）
    var XOR_KEY = 'LocalPicma#2024';

    function xorEncode(str) {
        var result = '';
        for (var i = 0; i < str.length; i++) {
            result += String.fromCharCode(str.charCodeAt(i) ^ XOR_KEY.charCodeAt(i % XOR_KEY.length));
        }
        return result;
    }

    function encrypt(plainText) {
        try {
            var xored = xorEncode(plainText);
            return btoa(unescape(encodeURIComponent(xored)));
        } catch (e) { return ''; }
    }

    function decrypt(cipherText) {
        try {
            var xored = decodeURIComponent(escape(atob(cipherText)));
            return xorEncode(xored);
        } catch (e) { return ''; }
    }

    // ==================== 凭据存取 ====================
    /**
     * 保存登录凭据到 localStorage（加密存储）
     * @param {string} username 用户名
     * @param {string} password 密码
     * @param {boolean} remember  是否记住密码
     * @param {boolean} autoLogin 是否自动登录
     */
    function saveCredentials(username, password, remember, autoLogin) {
        var cred = {
            u: encrypt(username || ''),
            p: encrypt(password || ''),
            r: remember ? 1 : 0,
            a: autoLogin ? 1 : 0
        };
        try {
            localStorage.setItem(CRED_KEY, JSON.stringify(cred));
        } catch (e) {}
    }

    /**
     * 读取已保存的凭据
     * @returns {{ username:string, password:string, remember:boolean, autoLogin:boolean } | null}
     */
    function loadCredentials() {
        try {
            var raw = localStorage.getItem(CRED_KEY);
            if (!raw) return null;
            var cred = JSON.parse(raw);
            return {
                username:  decrypt(cred.u || ''),
                password:  decrypt(cred.p || ''),
                remember:  cred.r === 1,
                autoLogin: cred.a === 1
            };
        } catch (e) { return null; }
    }

    /** 清除保存的凭据 */
    function clearCredentials() {
        try { localStorage.removeItem(CRED_KEY); } catch (e) {}
    }

    // ==================== Token 管理 ====================

    function getToken() {
        return localStorage.getItem(TOKEN_KEY);
    }

    function setAuth(token, username) {
        localStorage.setItem(TOKEN_KEY, token);
        localStorage.setItem(USER_KEY, username || '');
        // 同步写入 cookie（后端页面跳转时靠 cookie 认证）
        document.cookie = 'AUTH_TOKEN=' + token + '; path=/; max-age=86400; SameSite=Lax';
    }

    function clearAuth() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
        localStorage.removeItem(REDIRECT_KEY);
        document.cookie = 'AUTH_TOKEN=; path=/; max-age=0';
    }

    function saveRedirectUrl(url) {
        if (url && url.indexOf('/login') === -1) {
            localStorage.setItem(REDIRECT_KEY, url);
        }
    }

    function getRedirectUrl() {
        var url = localStorage.getItem(REDIRECT_KEY);
        localStorage.removeItem(REDIRECT_KEY);
        return url || '/home';
    }

    function checkToken() {
        var token = getToken();
        if (!token) {
            return Promise.resolve(false);
        }

        try {
            var payload = JSON.parse(atob(token.split('.')[1]));
            if (payload.exp && payload.exp * 1000 < Date.now()) {
                clearAuth();
                return Promise.resolve(false);
            }
        } catch (e) {}

        return fetch('/page/login/api/check-token', {
            headers: { 'Authorization': 'Bearer ' + token }
        })
            .then(function(res) {
                if (res.ok) {
                    return res.json().then(function(data) {
                        if (data.username) {
                            localStorage.setItem(USER_KEY, data.username);
                        }
                        // 刷新 cookie，确保页面导航时可用
                        document.cookie = 'AUTH_TOKEN=' + token + '; path=/; max-age=86400; SameSite=Lax';
                        return true;
                    });
                }
                // 服务端明确返回 401，token 确实无效
                if (res.status === 401) {
                    clearAuth();
                    return false;
                }
                // 其他错误（500 等），不清除认证，依赖 cookie 继续导航
                return true;
            })
            .catch(function() {
                // 网络不可达，不清除认证，依赖 cookie 继续页面导航
                return false;
            });
    }

    function guardLoginPage() {
        checkToken().then(function(valid) {
            if (valid) {
                window.location.replace(getRedirectUrl());
            }
        });
    }

    /**
     * 页面加载时调用：校验 token，失败则尝试自动登录。
     * @returns {Promise<boolean>} 是否处于登录状态
     */
    function validateOrAutoLogin() {
        return checkToken().then(function (valid) {
            if (valid) return true;

            // token 无效，尝试用保存的凭据自动登录
            var saved = loadCredentials();
            if (!saved || !saved.autoLogin || !saved.username || !saved.password) {
                return false;
            }

            return fetch('/page/login/api/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: saved.username, password: saved.password })
            })
            .then(function (res) { return res.json(); })
            .then(function (data) {
                if (!data.token) return false;
                setAuth(data.token, data.username);
                return true;
            })
            .catch(function () { return false; });
        });
    }

    function authFetch(url, options) {
        options = options || {};
        var headers = options.headers || {};
        var token = getToken();
        if (token) {
            headers['Authorization'] = 'Bearer ' + token;
        }
        options.headers = headers;

        return fetch(url, options).then(function(response) {
            if (response.status === 401) {
                // 尝试自动登录后再重试
                var saved = loadCredentials();
                if (saved && saved.autoLogin && saved.username && saved.password) {
                    return fetch('/page/login/api/login', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ name: saved.username, password: saved.password })
                    })
                    .then(function (res) { return res.json(); })
                    .then(function (data) {
                        if (!data.token) throw new Error('auto-login failed');
                        setAuth(data.token, data.username);
                        // 用新 token 重试原始请求
                        var retryOpts = Object.assign({}, options);
                        var retryHeaders = Object.assign({}, options.headers || {});
                        retryHeaders['Authorization'] = 'Bearer ' + data.token;
                        retryOpts.headers = retryHeaders;
                        return fetch(url, retryOpts);
                    })
                    .catch(function () {
                        // 自动登录也失败，跳转登录页
                        clearAuth();
                        saveRedirectUrl(window.location.pathname + window.location.search);
                        window.location.replace('/login');
                        return Promise.reject(new Error('认证过期'));
                    });
                }
                // 没有自动登录凭据，直接跳转
                clearAuth();
                saveRedirectUrl(window.location.pathname + window.location.search);
                window.location.replace('/login');
                return Promise.reject(new Error('认证过期'));
            }
            return response;
        });
    }

    function logout() {
        // 退出时不清除凭据（记住密码/自动登录需要保留）
        clearAuth();
        window.location.replace('/login');
    }

    return {
        getToken:           getToken,
        setAuth:            setAuth,
        clearAuth:          clearAuth,
        checkToken:         checkToken,
        validateOrAutoLogin: validateOrAutoLogin,
        guardLoginPage:     guardLoginPage,
        authFetch:          authFetch,
        getRedirectUrl:     getRedirectUrl,
        logout:             logout,
        saveCredentials:    saveCredentials,
        loadCredentials:    loadCredentials,
        clearCredentials:   clearCredentials
    };
})();
