// "Make It Funnier" for the server-rendered Create Post page. Calls POST /api/funnyfy with the
// ACCESS_TOKEN cookie (the /api chain is stateless, so no CSRF token is needed).
(function () {
    const textarea = document.getElementById('content');
    const button = document.getElementById('funny-btn');
    const undo = document.getElementById('funny-undo');
    const toast = document.getElementById('funny-toast');
    if (!textarea || !button) return;

    let last = null; // { before, after }
    let toastTimer = null;

    function showToast(kind, message) {
        clearTimeout(toastTimer);
        toast.className = 'toast ' + kind;
        toast.setAttribute('role', kind === 'error' ? 'alert' : 'status');
        toast.textContent = message;
        toast.hidden = false;
        toastTimer = setTimeout(() => { toast.hidden = true; }, kind === 'error' ? 6000 : 5000);
    }

    function refresh() {
        button.disabled = button.classList.contains('loading') || !textarea.value.trim();
        undo.hidden = !(last && textarea.value === last.after);
    }

    function setLoading(loading) {
        button.classList.toggle('loading', loading);
        button.innerHTML = loading
            ? '<span class="spinner" aria-hidden="true"></span> Making it funnier...'
            : 'Make It Funnier';
        textarea.disabled = loading;
        refresh();
    }

    button.addEventListener('click', async () => {
        const before = textarea.value;
        if (!before.trim()) return;
        setLoading(true);
        try {
            const res = await fetch('/api/funnyfy', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                body: JSON.stringify({ text: before })
            });
            const body = await res.json().catch(() => ({}));
            if (!res.ok) {
                throw new Error(res.status === 401 || res.status === 403
                    ? 'Please log in again to use Make It Funnier.'
                    : (body.detail || 'Could not make it funnier right now. Your text was not changed.'));
            }
            textarea.value = body.text;
            last = { before, after: body.text };
            showToast('success', 'Your post just got funnier \u{1F60E}');
        } catch (e) {
            showToast('error', e instanceof TypeError
                ? 'Could not reach the server. Your text was not changed.'
                : e.message);
        } finally {
            setLoading(false);
        }
    });

    undo.addEventListener('click', () => {
        if (!last) return;
        textarea.value = last.before;
        last = null;
        toast.hidden = true;
        refresh();
    });

    textarea.addEventListener('input', refresh);
    refresh();
})();
