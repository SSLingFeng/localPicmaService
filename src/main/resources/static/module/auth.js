var Auth = (function() {

    var TOKEN_KEY    = 'token';
    var USER_KEY     = 'username';
    var REDIRECT_KEY = 'redirectAfterLogin';

    function getToken() {
        return localStorage.getItem(TOKEN_KEY);
    }

    function setAuth(token, username) {
        localStorage.setItem(TOKEN_KEY, token);
        localStorage.setItem(USER_KEY, username || '');
    }

    function clearAuth() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
        localStorage.removeItem(REDIRECT_KEY);
        // 同时清除 Cookie
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

    // ★ 核心：向后端真正验证 token 是否有效
    function checkToken() {
        var token = getToken();
        if (!token) {
            return Promise.resolve(false);
        }

        // 先做本地快速检查
        try {
            var payload = JSON.parse(atob(token.split('.')[1]));
            if (payload.exp && payload.exp * 1000 < Date.now()) {
                clearAuth();
                return Promise.resolve(false);
            }
        } catch (e) {
            // 不是标准 JWT，跳过本地检查
        }

        // 向后端确认
        return fetch('/apicheck-token', {
            headers: { 'Authorization': 'Bearer ' + token }
        })
            .then(function(res) {
                if (res.ok) {
                    return res.json().then(function(data) {
                        // 更新用户名
                        if (data.username) {
                            localStorage.setItem(USER_KEY, data.username);
                        }
                        return true;
                    });
                }
                // token 无效
                clearAuth();
                return false;
            })
            .catch(function() {
                // 网络错误，信任本地检查结果
                return true;
            });
    }

    /**
     * 登录页调用：已登录则跳走
     */
    function guardLoginPage() {
        checkToken().then(function(valid) {
            if (valid) {
                window.location.replace(getRedirectUrl());
            }
            // 无效则什么都不做，正常显示登录页
        });
    }

    /**
     * 自动带 token 的请求
     */
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
                clearAuth();
                saveRedirectUrl(window.location.pathname + window.location.search);
                window.location.replace('/login');
                return Promise.reject(new Error('认证过期'));
            }
            return response;
        });
    }

    function logout() {
        clearAuth();
        window.location.replace('/login');
    }

    return {
        getToken:           getToken,
        setAuth:            setAuth,
        clearAuth:          clearAuth,
        guardLoginPage:     guardLoginPage,
        authFetch:          authFetch,
        getRedirectUrl:     getRedirectUrl,
        logout:             logout
    };
})();
