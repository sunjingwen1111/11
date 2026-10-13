(() => {
    const form = document.querySelector("#auth-form");
    const accountInput = document.querySelector("#account");
    const passwordInput = document.querySelector("#password");
    const nameInput = document.querySelector("#register-name");
    const errorMessage = document.querySelector("#auth-error");
    const submitButton = document.querySelector("#submit-auth");
    const switchButton = document.querySelector("#switch-button");
    let registering = false;
    let pending = false;

    function destination() {
        const next = new URLSearchParams(location.search).get("next") || "/users.html";
        try {
            const target = new URL(next, location.origin);
            if (target.origin === location.origin && target.pathname !== "/login.html") {
                return target.pathname + target.search;
            }
        } catch (_) {}
        return "/users.html";
    }

    function setMode(nextMode) {
        registering = nextMode;
        errorMessage.hidden = true;
        document.querySelector("#name-field").hidden = !registering;
        document.querySelector("#form-title").textContent = registering ? "注册新用户" : "登录账号";
        document.querySelector("#form-subtitle").textContent = registering ? "创建账号后即可进入工作台" : "使用账号和密码进入工作台";
        document.querySelector("#switch-hint").textContent = registering ? "已有账号？" : "还没有账号？";
        switchButton.textContent = registering ? "返回登录" : "注册用户";
        submitButton.firstChild.textContent = registering ? "注 册 " : "登 录 ";
        passwordInput.autocomplete = registering ? "new-password" : "current-password";
        passwordInput.value = "";
        accountInput.focus();
    }

    async function submit(event) {
        event.preventDefault();
        if (pending) return;
        const account = accountInput.value.trim();
        const password = passwordInput.value;
        if (!account || !password.trim()) {
            errorMessage.textContent = "请输入账号和密码";
            errorMessage.hidden = false;
            return;
        }

        const body = { account, passwd: password };
        if (registering) body.name = nameInput.value.trim() || null;
        pending = true;
        errorMessage.hidden = true;
        submitButton.disabled = true;
        switchButton.disabled = true;
        try {
            const response = await fetch(registering ? "/api/auth/register" : "/api/auth/login", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body)
            });
            const data = await response.json().catch(() => null);
            if (!response.ok) throw new Error(data?.message || `请求失败（${response.status}）`);
            if (!data?.tokenValue || data.tokenName !== "satoken") throw new Error("登录响应格式错误");
            DemoAuth.save(data.tokenValue);
            location.replace(destination());
        } catch (error) {
            errorMessage.textContent = error.message || "网络连接失败，请稍后重试";
            errorMessage.hidden = false;
        } finally {
            pending = false;
            submitButton.disabled = false;
            switchButton.disabled = false;
        }
    }

    switchButton.addEventListener("click", () => setMode(!registering));
    form.addEventListener("submit", submit);
    if (new URLSearchParams(location.search).get("mode") === "register") setMode(true);
    if (DemoAuth.token()) {
        fetch("/api/auth/me", { headers: { satoken: DemoAuth.token() } })
            .then((response) => { if (response.ok) location.replace(destination()); else DemoAuth.clear(); })
            .catch(() => {});
    }
    accountInput.focus();
})();
