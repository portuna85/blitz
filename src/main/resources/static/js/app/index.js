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

    function fillCommentContent(container, comment, includeReplyButton) {
        container.dataset.commentId = String(comment.id);
        container.dataset.version = String(comment.version);
        container.dataset.owner = String(comment.owner);
        container.dataset.deleted = String(comment.deleted);
        container.textContent = '';

        if (comment.deleted) {
            const body = document.createElement('p');
            body.className = 'comment__body';
            body.textContent = '삭제된 댓글입니다.';
            container.appendChild(body);
            return;
        }

        const meta = document.createElement('p');
        meta.className = 'comment__meta';
        const author = document.createElement('span');
        author.className = 'comment__author';
        author.textContent = comment.author;
        meta.appendChild(author);
        const time = document.createElement('time');
        time.dateTime = comment.createdDate;
        time.textContent = comment.createdDate;
        meta.appendChild(time);
        container.appendChild(meta);

        const body = document.createElement('p');
        body.className = 'comment__body comment-content';
        body.textContent = comment.content;
        container.appendChild(body);

        const actions = document.createElement('div');
        actions.className = 'comment__actions';
        if (includeReplyButton) {
            const replyBtn = document.createElement('button');
            replyBtn.type = 'button';
            replyBtn.className = 'button button--quiet comment-reply-btn';
            replyBtn.textContent = '답글';
            actions.appendChild(replyBtn);
        }
        if (comment.owner) {
            const editBtn = document.createElement('button');
            editBtn.type = 'button';
            editBtn.className = 'button button--quiet comment-edit-btn';
            editBtn.textContent = '수정';
            actions.appendChild(editBtn);

            const deleteBtn = document.createElement('button');
            deleteBtn.type = 'button';
            deleteBtn.className = 'button button--danger comment-delete-btn';
            deleteBtn.textContent = '삭제';
            actions.appendChild(deleteBtn);
        }
        container.appendChild(actions);
    }

    function buildThreadNode(thread, canComment) {
        const li = document.createElement('li');
        li.className = 'comment-thread';

        const commentDiv = document.createElement('div');
        commentDiv.className = 'comment';
        fillCommentContent(commentDiv, thread.comment, canComment);
        li.appendChild(commentDiv);

        if (thread.replies && thread.replies.length > 0) {
            const repliesList = document.createElement('ul');
            repliesList.className = 'comment-replies';
            thread.replies.forEach((reply) => {
                const replyLi = document.createElement('li');
                replyLi.className = 'comment comment--reply';
                fillCommentContent(replyLi, reply, false);
                repliesList.appendChild(replyLi);
            });
            li.appendChild(repliesList);
        }

        return li;
    }

    function renderCommentPage(section, pageData) {
        const canComment = section.dataset.canComment === 'true';
        const countEl = section.querySelector('#comment-active-count');
        if (countEl) {
            countEl.textContent = String(pageData.activeCount);
        }

        const region = section.querySelector('#comment-list-region');
        if (!region) {
            return;
        }
        region.textContent = '';

        if (!pageData.content || pageData.content.length === 0) {
            const empty = document.createElement('p');
            empty.className = 'empty-state';
            empty.textContent = '첫 댓글을 남겨보세요.';
            region.appendChild(empty);
            section.dataset.currentPage = String(pageData.page);
            return;
        }

        const list = document.createElement('ul');
        list.id = 'comment-list';
        list.className = 'comment-thread-list';
        pageData.content.forEach((thread) => list.appendChild(buildThreadNode(thread, canComment)));
        region.appendChild(list);

        const nav = document.createElement('nav');
        nav.className = 'pagination comment-pagination';
        nav.setAttribute('aria-label', '댓글 페이지');
        if (pageData.hasPrevious) {
            const prev = document.createElement('a');
            prev.className = 'pagination__link';
            prev.href = '#';
            prev.dataset.commentPage = String(pageData.page - 1);
            prev.textContent = '이전';
            nav.appendChild(prev);
        }
        const status = document.createElement('span');
        status.className = 'pagination__status';
        status.textContent = `${pageData.page + 1} / ${pageData.totalPages}`;
        nav.appendChild(status);
        if (pageData.hasNext) {
            const next = document.createElement('a');
            next.className = 'pagination__link';
            next.href = '#';
            next.dataset.commentPage = String(pageData.page + 1);
            next.textContent = '다음';
            nav.appendChild(next);
        }
        region.appendChild(nav);

        section.dataset.currentPage = String(pageData.page);
    }

    function bindCommentSection() {
        const section = document.querySelector('.comments');
        if (!section) {
            return;
        }

        const apiBase = section.dataset.commentsApiBase;
        const message = section.querySelector('#comment-message');

        async function loadPage(page, historyMode = 'push') {
            try {
                const result = await apiRequest(`${apiBase}?page=${page}`);
                renderCommentPage(section, result.body);
                const url = new URL(window.location.href);
                url.searchParams.set('commentPage', String(page));
                if (historyMode === 'push') {
                    window.history.pushState({commentPage: page}, '', url);
                } else if (historyMode === 'replace') {
                    window.history.replaceState({commentPage: page}, '', url);
                }
            } catch (error) {
                console.error('댓글 페이지를 불러오지 못했습니다.', error);
                showMessage(message, 'error', errorMessage(error));
            }
        }

        function toggleReplyForm(button) {
            const commentEl = button.closest('.comment');
            const existing = commentEl.querySelector('.reply-form');
            if (existing) {
                existing.remove();
                return;
            }

            const form = document.createElement('form');
            form.className = 'comment-form reply-form';
            const textarea = document.createElement('textarea');
            textarea.name = 'content';
            textarea.required = true;
            textarea.maxLength = 1000;
            textarea.rows = 2;
            textarea.setAttribute('aria-label', '답글 내용');
            form.appendChild(textarea);
            const actions = document.createElement('div');
            actions.className = 'form-actions';
            const submit = document.createElement('button');
            submit.type = 'submit';
            submit.className = 'button button--primary';
            submit.textContent = '답글 등록';
            actions.appendChild(submit);
            form.appendChild(actions);

            form.addEventListener('submit', async (event) => {
                event.preventDefault();
                const content = textarea.value.trim();
                if (!content) {
                    textarea.setCustomValidity('공백이 아닌 내용을 입력해 주세요.');
                    form.reportValidity();
                    return;
                }
                textarea.setCustomValidity('');

                const parentId = Number(commentEl.dataset.commentId);
                setBusy(form, true);
                try {
                    const result = await apiRequest(apiBase, {
                        method: 'POST',
                        body: JSON.stringify({content, parentId})
                    });
                    showMessage(message, 'success', '답글을 등록했습니다.');
                    await loadPage(result.body.targetPage);
                } catch (error) {
                    console.error('답글 등록에 실패했습니다.', error);
                    showMessage(message, 'error', errorMessage(error));
                    setBusy(form, false);
                }
            });

            commentEl.appendChild(form);
            textarea.focus();
        }

        function toggleEditForm(button) {
            const commentEl = button.closest('.comment');
            const existing = commentEl.querySelector('.edit-form');
            if (existing) {
                existing.remove();
                return;
            }

            const contentEl = commentEl.querySelector('.comment-content');
            const currentText = contentEl ? contentEl.textContent : '';

            const form = document.createElement('form');
            form.className = 'comment-form edit-form';
            const textarea = document.createElement('textarea');
            textarea.name = 'content';
            textarea.required = true;
            textarea.maxLength = 1000;
            textarea.rows = 3;
            textarea.value = currentText;
            textarea.setAttribute('aria-label', '댓글 수정');
            form.appendChild(textarea);
            const actions = document.createElement('div');
            actions.className = 'form-actions';
            const submit = document.createElement('button');
            submit.type = 'submit';
            submit.className = 'button button--primary';
            submit.textContent = '저장';
            actions.appendChild(submit);
            const cancel = document.createElement('button');
            cancel.type = 'button';
            cancel.className = 'button button--quiet';
            cancel.textContent = '취소';
            cancel.addEventListener('click', () => form.remove());
            actions.appendChild(cancel);
            form.appendChild(actions);

            form.addEventListener('submit', async (event) => {
                event.preventDefault();
                const content = textarea.value.trim();
                if (!content) {
                    textarea.setCustomValidity('공백이 아닌 내용을 입력해 주세요.');
                    form.reportValidity();
                    return;
                }
                textarea.setCustomValidity('');

                const commentId = commentEl.dataset.commentId;
                const version = Number(commentEl.dataset.version);
                setBusy(form, true);
                try {
                    const result = await apiRequest(`${apiBase}/${commentId}`, {
                        method: 'PUT',
                        body: JSON.stringify({content, version})
                    });
                    showMessage(message, 'success', '댓글을 수정했습니다.');
                    await loadPage(result.body.targetPage);
                } catch (error) {
                    console.error('댓글 수정에 실패했습니다.', error);
                    showMessage(message, 'error', errorMessage(error));
                    setBusy(form, false);
                }
            });

            commentEl.appendChild(form);
            textarea.focus();
        }

        async function handleDelete(button) {
            if (!window.confirm('이 댓글을 삭제하시겠습니까? 삭제한 댓글은 복구할 수 없습니다.')) {
                return;
            }

            const commentEl = button.closest('.comment');
            const commentId = commentEl.dataset.commentId;
            const version = commentEl.dataset.version;

            setBusy(section, true);
            try {
                const deleteUrl = new URL(`${apiBase}/${commentId}`, window.location.origin);
                deleteUrl.searchParams.set('version', version);
                const result = await apiRequest(deleteUrl.toString(), {method: 'DELETE'});
                showMessage(message, 'success', '댓글을 삭제했습니다.');
                await loadPage(result.body.targetPage);
            } catch (error) {
                console.error('댓글 삭제에 실패했습니다.', error);
                showMessage(message, 'error', errorMessage(error));
            } finally {
                setBusy(section, false);
            }
        }

        section.addEventListener('click', async (event) => {
            const pageLink = event.target.closest('[data-comment-page]');
            if (pageLink) {
                event.preventDefault();
                await loadPage(Number(pageLink.dataset.commentPage));
                return;
            }

            const replyBtn = event.target.closest('.comment-reply-btn');
            if (replyBtn) {
                toggleReplyForm(replyBtn);
                return;
            }

            const editBtn = event.target.closest('.comment-edit-btn');
            if (editBtn) {
                toggleEditForm(editBtn);
                return;
            }

            const deleteBtn = event.target.closest('.comment-delete-btn');
            if (deleteBtn) {
                await handleDelete(deleteBtn);
            }
        });

        const createForm = document.getElementById('commentCreateForm');
        if (createForm) {
            createForm.addEventListener('submit', async (event) => {
                event.preventDefault();
                const textarea = createForm.querySelector('textarea[name="content"]');
                const content = textarea.value.trim();
                if (!content) {
                    textarea.setCustomValidity('공백이 아닌 내용을 입력해 주세요.');
                    createForm.reportValidity();
                    return;
                }
                textarea.setCustomValidity('');

                setBusy(createForm, true);
                try {
                    const result = await apiRequest(apiBase, {
                        method: 'POST',
                        body: JSON.stringify({content, parentId: null})
                    });
                    textarea.value = '';
                    showMessage(message, 'success', '댓글을 등록했습니다.');
                    await loadPage(result.body.targetPage);
                } catch (error) {
                    console.error('댓글 등록에 실패했습니다.', error);
                    showMessage(message, 'error', errorMessage(error));
                } finally {
                    setBusy(createForm, false);
                }
            });
        }

        window.addEventListener('popstate', () => {
            const params = new URLSearchParams(window.location.search);
            const parsed = Number.parseInt(params.get('commentPage'), 10);
            const page = Number.isNaN(parsed) || parsed < 0 ? 0 : parsed;
            loadPage(page, 'none');
        });
    }

    function init() {
        renderStoredFlash();
        bindSaveForm();
        bindUpdateForm();
        bindCommentSection();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init, {once: true});
    } else {
        init();
    }
})();
