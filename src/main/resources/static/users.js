(() => {
    if (!DemoAuth.requireLogin()) return;
    const $ = (selector) => document.querySelector(selector);
    const listState = $("#list-state");
    const tableArea = $("#table-area");
    const rows = $("#user-rows");
    const editor = $("#editor-dialog");
    const deleteDialog = $("#delete-dialog");
    const form = $("#user-form");
    const nameInput = $("#user-name");
    const accountInput = $("#user-account");
    const passwordInput = $("#user-password");
    const toast = $("#toast");
    const numberFormatter = new Intl.NumberFormat("zh-CN");

    const initialPage = Number(new URLSearchParams(location.search).get("page") ?? 1);
    let currentPage = Number.isInteger(initialPage) && initialPage > 0 && initialPage <= 2147483647 ? initialPage : 1;
    let currentItems = [];
    let totalPages = 0;
    let listRequest = null;
    let editedId = null;
    let pending = false;
    let toastTimer;

    function notify(message, error = false) {
        clearTimeout(toastTimer);
        toast.textContent = message;
        toast.classList.toggle("error", error);
        toast.hidden = false;
        toastTimer = setTimeout(() => { toast.hidden = true; }, 4000);
    }

    async function api(path, options = {}) {
        let response;
        try {
            response = await DemoAuth.fetch(`/api/users${path}`, { ...options, headers: { "Accept": "application/json", ...options.headers } });
        } catch (error) {
            if (error.name === "AbortError") throw error;
            throw new Error("网络连接失败，请检查服务后重试");
        }
        const data = response.status === 204 ? null : await response.json().catch(() => null);
        if (!response.ok) throw new Error(data?.message || `请求失败（${response.status}）`);
        return data;
    }

    function showState(title, description, actionText, action) {
        $("#state-title").textContent = title;
        $("#state-description").textContent = description;
        const button = $("#state-action");
        button.hidden = !actionText;
        button.textContent = actionText || "";
        button.onclick = action || null;
        listState.hidden = false;
        tableArea.hidden = true;
    }

    function element(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        node.textContent = text;
        return node;
    }

    function renderRow(user) {
        const tr = document.createElement("tr");
        const name = user.name?.trim() || "未设置姓名";
        const identity = element("td", "", "");
        const userCell = element("div", "user-cell", "");
        const avatar = element("span", `avatar tone-${user.id % 4}`, (user.name?.trim() || user.account || "?").slice(0, 1));
        avatar.setAttribute("aria-hidden", "true");
        const displayName = element("span", user.name?.trim() ? "user-name" : "user-name muted-name", name);
        displayName.title = name;
        userCell.append(avatar, displayName);
        identity.append(userCell);

        const accountCell = document.createElement("td");
        const account = element("span", "account-text", user.account || "—");
        account.title = user.account || "";
        accountCell.append(account);
        const idCell = document.createElement("td");
        idCell.append(element("span", "id-text", String(user.id)));
        const actionsCell = document.createElement("td");
        const actions = element("div", "row-actions", "");
        for (const [actionName, text] of [["edit", "编辑"], ["delete", "删除"]]) {
            const button = element("button", actionName === "delete" ? "text-button danger" : "text-button", text);
            button.type = "button";
            button.dataset.action = actionName;
            button.dataset.id = String(user.id);
            button.setAttribute("aria-label", `${text}用户 ${name}`);
            actions.append(button);
        }
        actionsCell.append(actions);
        tr.append(identity, accountCell, idCell, actionsCell);
        return tr;
    }

    function renderPage(data) {
        currentPage = data.page;
        totalPages = data.totalPages;
        currentItems = data.items;
        $("#user-count").textContent = numberFormatter.format(data.total);
        history.replaceState(null, "", currentPage === 1 ? location.pathname : `${location.pathname}?page=${currentPage}`);

        if (data.total === 0) {
            showState("还没有用户", "点击新增用户，创建第一条用户记录。", "新增用户", () => openEditor());
            return;
        }
        rows.replaceChildren(...data.items.map(renderRow));
        listState.hidden = true;
        tableArea.hidden = false;
        const start = (data.page - 1) * data.pageSize + 1;
        const end = start + data.items.length - 1;
        $("#page-summary").textContent = data.items.length
            ? `显示 ${numberFormatter.format(start)}–${numberFormatter.format(end)} 条，共 ${numberFormatter.format(data.total)} 条`
            : `本页没有数据，共 ${numberFormatter.format(data.total)} 条`;
        $("#page-indicator").textContent = `${data.page} / ${data.totalPages} 页`;
        $("#previous-page").disabled = data.page <= 1;
        $("#next-page").disabled = data.page >= data.totalPages;
    }

    async function loadPage(page = currentPage) {
        listRequest?.abort();
        const request = new AbortController();
        listRequest = request;
        showState("正在加载用户", "请稍候，正在获取最新数据。", null, null);
        try {
            const data = await api(`?page=${page}`, { signal: request.signal });
            if (request !== listRequest) return;
            if (!Array.isArray(data?.items) || !Number.isInteger(data.totalPages)) {
                throw new Error("服务器返回的列表格式不正确");
            }
            if (data.total > 0 && page > data.totalPages) {
                await loadPage(data.totalPages);
                return;
            }
            renderPage(data);
        } catch (error) {
            if (request !== listRequest || error.name === "AbortError") return;
            showState("加载失败", error.message, "重试", () => loadPage(page));
        } finally {
            if (request === listRequest) listRequest = null;
        }
    }

    function openEditor(user = null) {
        editedId = user?.id ?? null;
        form.reset();
        $("#form-error").hidden = true;
        $("#editor-title").textContent = user ? "编辑用户" : "新增用户";
        $("#editor-description").textContent = user ? `正在编辑用户 #${user.id}` : "填写以下信息以创建用户。";
        $("#save-user").textContent = user ? "保存修改" : "创建用户";
        $("#password-required").hidden = !!user;
        $("#password-hint").textContent = user ? "留空则不修改原密码。" : "创建用户时需要设置密码。";
        passwordInput.required = !user;
        passwordInput.placeholder = user ? "留空则不修改密码" : "请输入密码";
        if (user) {
            nameInput.value = user.name ?? "";
            accountInput.value = user.account ?? "";
        }
        editor.showModal();
        (user ? nameInput : accountInput).focus();
    }

    async function editUser(id) {
        try {
            const user = await api(`/${id}`);
            openEditor(user);
        } catch (error) {
            notify(error.message, true);
            loadPage();
        }
    }

    function showFormError(message) {
        const error = $("#form-error");
        error.textContent = message;
        error.hidden = false;
    }

    async function saveUser(event) {
        event.preventDefault();
        if (pending) return;
        const name = nameInput.value.trim() || null;
        const account = accountInput.value.trim();
        const password = passwordInput.value;
        if (!account) { showFormError("请输入登录账号"); accountInput.focus(); return; }
        if (!editedId && !password.trim()) { showFormError("请输入密码"); passwordInput.focus(); return; }
        if (password && !password.trim()) { showFormError("密码不能只包含空格"); passwordInput.focus(); return; }

        const updating = editedId !== null;
        const body = { name, account };
        if (password) body.passwd = password;
        pending = true;
        $("#form-error").hidden = true;
        $("#save-user").disabled = true;
        $("#cancel-editor").disabled = true;
        $("#close-editor").disabled = true;
        try {
            await api(updating ? `/${editedId}` : "", {
                method: updating ? "PUT" : "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body)
            });
            editor.close();
            form.reset();
            notify(updating ? "用户信息已更新" : "用户创建成功");
            await loadPage(updating ? currentPage : 1);
        } catch (error) {
            showFormError(error.message);
        } finally {
            pending = false;
            $("#save-user").disabled = false;
            $("#cancel-editor").disabled = false;
            $("#close-editor").disabled = false;
        }
    }

    let deletionId = null;
    function askDelete(user) {
        deletionId = user.id;
        $("#delete-description").textContent = `用户「${user.name || user.account || user.id}」将被永久删除，此操作不可撤销。`;
        $("#delete-error").hidden = true;
        deleteDialog.showModal();
        $("#cancel-delete").focus();
    }

    async function deleteUser() {
        if (pending || deletionId === null) return;
        pending = true;
        $("#confirm-delete").disabled = true;
        $("#cancel-delete").disabled = true;
        try {
            await api(`/${deletionId}`, { method: "DELETE" });
            deleteDialog.close();
            deletionId = null;
            notify("用户已删除");
            await loadPage();
        } catch (error) {
            const message = $("#delete-error");
            message.textContent = error.message;
            message.hidden = false;
        } finally {
            pending = false;
            $("#confirm-delete").disabled = false;
            $("#cancel-delete").disabled = false;
        }
    }

    $("#add-user").addEventListener("click", () => openEditor());
    $("#logout-button").addEventListener("click", async (event) => {
        event.currentTarget.disabled = true;
        try {
            await DemoAuth.fetch("/api/auth/logout", { method: "POST" });
        } finally {
            DemoAuth.clear();
            location.replace("/login.html");
        }
    });
    $("#refresh-users").addEventListener("click", () => loadPage());
    $("#previous-page").addEventListener("click", () => { if (currentPage > 1) loadPage(currentPage - 1); });
    $("#next-page").addEventListener("click", () => { if (currentPage < totalPages) loadPage(currentPage + 1); });
    rows.addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (!button) return;
        const user = currentItems.find((item) => item.id === Number(button.dataset.id));
        if (!user) return;
        if (button.dataset.action === "edit") editUser(user.id);
        else askDelete(user);
    });
    form.addEventListener("submit", saveUser);
    $("#close-editor").addEventListener("click", () => editor.close());
    $("#cancel-editor").addEventListener("click", () => editor.close());
    $("#cancel-delete").addEventListener("click", () => deleteDialog.close());
    $("#confirm-delete").addEventListener("click", deleteUser);
    for (const dialog of [editor, deleteDialog]) {
        dialog.addEventListener("cancel", (event) => { if (pending) event.preventDefault(); });
    }
    DemoAuth.fetch("/api/auth/me")
        .then((response) => response.json())
        .then((user) => { $("#current-account").textContent = user.account; })
        .catch(() => {});
    loadPage(currentPage);
})();
