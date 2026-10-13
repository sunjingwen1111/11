(() => {
    const TOKEN_KEY = "demo.auth.token";

    function redirectToLogin() {
        const next = location.pathname === "/login.html" ? "/users.html" : location.pathname + location.search;
        location.replace(`/login.html?next=${encodeURIComponent(next)}`);
    }

    function requireLogin() {
        if (sessionStorage.getItem(TOKEN_KEY)) return true;
        redirectToLogin();
        return false;
    }

    async function authenticatedFetch(url, options = {}) {
        const token = sessionStorage.getItem(TOKEN_KEY);
        if (!token) {
            redirectToLogin();
            throw new Error("请先登录");
        }
        const response = await fetch(url, {
            ...options,
            headers: { ...options.headers, satoken: token }
        });
        if (response.status === 401) {
            sessionStorage.removeItem(TOKEN_KEY);
            redirectToLogin();
            throw new Error("登录已失效，请重新登录");
        }
        return response;
    }

    window.DemoAuth = Object.freeze({
        requireLogin,
        fetch: authenticatedFetch,
        token: () => sessionStorage.getItem(TOKEN_KEY),
        save: (token) => sessionStorage.setItem(TOKEN_KEY, token),
        clear: () => sessionStorage.removeItem(TOKEN_KEY)
    });
})();
