(() => {
    'use strict';

    const FLASH_STORAGE_KEY = 'blitz.flash.v1';

    class ApiError extends Error {
        constructor(status, body) {
            super('API request failed with status ' + status);
            this.name = 'ApiError';
            this.status = status;
            this.body = body;
        }
    }

    function metaContent(name) {
        const element = document.querySelector(`meta[name="${name}"]`);
        return element ? element.content : '';
    }

    function appUrl(path) {
        const base = (metaContent('app-base-url') || '/').replace(/\/+$/, '');
        const suffix = path.startsWith('/') ? path : `/${path}`;
        return `${base}${suffix}` || '/';
    }

    async function readResponseBody(response) {
        if (response.status === 204) {
            return null;
        }

        const text = await response.text();
        if (!text) {
            return null;
        }

        const contentType = response.headers.get('content-type') || '';
        if (contentType.includes('json')) {
            try {
                return JSON.parse(text);
            } catch (error) {
                console.error('JSON 응답을 해석하지 못했습니다.', error);
                return null;
            }
        }

        return text;
    }

    async function apiRequest(url, options = {}) {
        const headers = new Headers(options.headers || {});
        headers.set('Accept', 'application/json');

        if (options.body !== undefined) {
            headers.set('Content-Type', 'application/json; charset=UTF-8');
        }

        const csrfToken = metaContent('_csrf');
        const csrfHeader = metaContent('_csrf_header');
        const method = (options.method || 'GET').toUpperCase();
        if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
            if (!csrfToken || !csrfHeader) {
                throw new ApiError(403, {message: '보안 정보가 만료되었습니다. 페이지를 새로고침해 주세요.'});
            }
            headers.set(csrfHeader, csrfToken);
        }

        const response = await fetch(url, {
            ...options,
            headers,
            credentials: 'same-origin'
        });

        if (response.redirected) {
            throw new ApiError(401, {message: '로그인이 만료되었습니다. 다시 로그인해 주세요.'});
        }

        const body = await readResponseBody(response);
        if (!response.ok) {
            throw new ApiError(response.status, body);
        }

        return {response, body};
    }

    function setBusy(form, busy) {
        form.setAttribute('aria-busy', String(busy));
        form.querySelectorAll('button').forEach((button) => {
            if (busy) {
                button.dataset.disabledBeforeRequest = String(button.disabled);
                button.disabled = true;
            } else {
                button.disabled = button.dataset.disabledBeforeRequest === 'true';
                delete button.dataset.disabledBeforeRequest;
            }
        });
    }

    function showMessage(element, type, message) {
        if (!element) {
            return;
        }

        element.hidden = false;
        element.className = `notice notice--${type}`;
        element.setAttribute('role', type === 'error' ? 'alert' : 'status');
        element.textContent = message;
        element.focus({preventScroll: false});
    }

    function errorMessage(error) {
        if (error instanceof ApiError) {
            const body = error.body;
            if (body && typeof body === 'object') {
                const fieldMessages = body.errors && typeof body.errors === 'object'
                    ? Object.values(body.errors)
                        .flatMap((value) => Array.isArray(value) ? value : [value])
                        .filter((value) => typeof value === 'string' && value.trim())
                    : [];
                if (fieldMessages.length > 0) {
                    return fieldMessages.join(' ');
                }
                if (typeof body.message === 'string' && body.message.trim()) {
                    return body.message;
                }
            }

            if (error.status === 400) {
                return '입력값을 다시 확인해 주세요.';
            }
            if (error.status === 401) {
                return '로그인이 만료되었습니다. 다시 로그인해 주세요.';
            }
            if (error.status === 403) {
                return '이 작업을 수행할 권한이 없거나 보안 정보가 만료되었습니다.';
            }
            if (error.status === 409) {
                return '다른 사용자가 먼저 게시글을 변경했습니다. 새로고침 후 다시 시도해 주세요.';
            }
        }

        return '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.';
    }

    function storeFlash(type, message) {
        try {
            sessionStorage.setItem(FLASH_STORAGE_KEY, JSON.stringify({type, message}));
        } catch (error) {
            console.debug('브라우저 저장소에 알림을 보관하지 못했습니다.', error);
        }
    }

    function renderStoredFlash() {
        let flash = null;
        try {
            const stored = sessionStorage.getItem(FLASH_STORAGE_KEY);
            if (stored) {
                flash = JSON.parse(stored);
                sessionStorage.removeItem(FLASH_STORAGE_KEY);
            }
        } catch (error) {
            console.debug('저장된 알림을 불러오지 못했습니다.', error);
        }

        if (flash && typeof flash.message === 'string') {
            showMessage(document.querySelector('[data-flash-message]'), flash.type || 'success', flash.message);
        }
    }

    function ensureMeaningfulText(form) {
        const fields = form.querySelectorAll('input[name="title"], textarea[name="content"]');
        let valid = true;

        fields.forEach((field) => {
            const blank = !field.value.trim();
            field.setCustomValidity(blank ? '공백이 아닌 내용을 입력해 주세요.' : '');
            valid = valid && !blank;
        });

        if (!valid) {
            form.reportValidity();
        }
        return valid;
    }

    function clearCustomValidityOnInput(form) {
        form.querySelectorAll('input[name="title"], textarea[name="content"]').forEach((field) => {
            field.addEventListener('input', () => field.setCustomValidity(''));
        });
    }

    function resourceId(result) {
        const locationHeader = result.response.headers.get('Location');
        if (locationHeader) {
            try {
                const location = new URL(locationHeader, window.location.origin);
                if (location.origin === window.location.origin) {
                    const match = location.pathname.match(/\/(\d+)\/?$/);
                    if (match) {
                        return match[1];
                    }
                }
            } catch (error) {
                console.debug('Location 헤더를 해석하지 못했습니다.', error);
            }
        }

        if (result.body && typeof result.body === 'object' && result.body.id !== undefined) {
            return String(result.body.id);
        }
        if (typeof result.body === 'number' || (typeof result.body === 'string' && /^\d+$/.test(result.body))) {
            return String(result.body);
        }
        return null;
    }

    function bindSaveForm() {
        const form = document.getElementById('postSaveForm');
        if (!form) {
            return;
        }

        clearCustomValidityOnInput(form);
        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (!ensureMeaningfulText(form)) {
                return;
            }

            const message = document.getElementById('form-message');
            setBusy(form, true);
            try {
                const result = await apiRequest(form.dataset.createUrl, {
                    method: 'POST',
                    body: JSON.stringify({
                        title: form.elements.title.value,
                        content: form.elements.content.value
                    })
                });
                const id = resourceId(result);
                storeFlash('success', '게시글을 등록했습니다.');
                window.location.assign(id ? appUrl(`/posts/${encodeURIComponent(id)}`) : appUrl('/'));
            } catch (error) {
                console.error('게시글 등록 요청에 실패했습니다.', error);
                showMessage(message, 'error', errorMessage(error));
                setBusy(form, false);
            }
        });
    }

    function bindUpdateForm() {
        const form = document.getElementById('postUpdateForm');
        if (!form) {
            return;
        }

        const message = document.getElementById('form-message');
        const versionField = document.getElementById('post-version');
        clearCustomValidityOnInput(form);

        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (!ensureMeaningfulText(form)) {
                return;
            }

            setBusy(form, true);
            try {
                await apiRequest(form.dataset.updateUrl, {
                    method: 'PUT',
                    body: JSON.stringify({
                        title: form.elements.title.value,
                        content: form.elements.content.value,
                        version: Number(versionField.value)
                    })
                });
                storeFlash('success', '게시글을 수정했습니다.');
                window.location.assign(form.dataset.detailUrl);
            } catch (error) {
                console.error('게시글 수정 요청에 실패했습니다.', error);
                showMessage(message, 'error', errorMessage(error));
                setBusy(form, false);
            }
        });

        const deleteButton = document.getElementById('btn-delete');
        if (deleteButton) {
            deleteButton.addEventListener('click', async () => {
                if (!window.confirm('이 게시글을 삭제하시겠습니까? 삭제한 글은 복구할 수 없습니다.')) {
                    return;
                }

                setBusy(form, true);
                try {
                    const deleteUrl = new URL(form.dataset.deleteUrl, window.location.origin);
                    deleteUrl.searchParams.set('version', versionField.value);
                    await apiRequest(deleteUrl.toString(), {method: 'DELETE'});
                    storeFlash('success', '게시글을 삭제했습니다.');
                    window.location.assign(appUrl('/'));
                } catch (error) {
                    console.error('게시글 삭제 요청에 실패했습니다.', error);
                    showMessage(message, 'error', errorMessage(error));
                    setBusy(form, false);
                }
            });
        }
    }

    function init() {
        renderStoredFlash();
        bindSaveForm();
        bindUpdateForm();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init, {once: true});
    } else {
        init();
    }
})();
