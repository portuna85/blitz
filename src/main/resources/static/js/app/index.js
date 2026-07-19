var main = {
    init : function () {
        var _this = this;

        $('#postSaveForm').on('submit', function (event) {
            event.preventDefault();
            if (this.reportValidity()) {
                _this.save();
            }
        });

        $('#postUpdateForm').on('submit', function (event) {
            event.preventDefault();
            if (this.reportValidity()) {
                _this.update();
            }
        });

        $('#btn-delete').on('click', function () {
            if (window.confirm('정말 삭제하시겠습니까?')) {
                _this.delete();
            }
        });
    },
    getCsrfToken : function () {
        var match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
        return match ? decodeURIComponent(match[1]) : null;
    },
    showMessage : function (type, text) {
        $('#form-message')
            .removeClass('d-none alert-success alert-danger')
            .addClass('alert-' + type)
            .text(text);
    },
    withSubmitDisabled : function ($button, task) {
        $button.prop('disabled', true);
        return task().always(function () {
            $button.prop('disabled', false);
        });
    },
    handleError : function (error) {
        console.error('요청 처리 중 오류가 발생했습니다.', error);
        var message = (error.responseJSON && error.responseJSON.message)
            ? error.responseJSON.message
            : '요청 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.';
        this.showMessage('danger', message);
    },
    save : function () {
        var _this = this;
        var data = {
            title: $('#title').val(),
            content: $('#content').val()
        };

        this.withSubmitDisabled($('#btn-save'), function () {
            return $.ajax({
                type: 'POST',
                url: '/api/v1/posts',
                dataType: 'json',
                contentType: 'application/json; charset=utf-8',
                headers: { 'X-XSRF-TOKEN': _this.getCsrfToken() },
                data: JSON.stringify(data)
            }).done(function () {
                _this.showMessage('success', '글이 등록되었습니다.');
                window.location.href = '/';
            }).fail(function (error) {
                _this.handleError(error);
            });
        });
    },
    update : function () {
        var _this = this;
        var data = {
            title: $('#title').val(),
            content: $('#content').val()
        };

        var id = $('#id').val();

        this.withSubmitDisabled($('#btn-update'), function () {
            return $.ajax({
                type: 'PUT',
                url: '/api/v1/posts/' + encodeURIComponent(id),
                dataType: 'json',
                contentType: 'application/json; charset=utf-8',
                headers: { 'X-XSRF-TOKEN': _this.getCsrfToken() },
                data: JSON.stringify(data)
            }).done(function () {
                _this.showMessage('success', '글이 수정되었습니다.');
                window.location.href = '/';
            }).fail(function (error) {
                _this.handleError(error);
            });
        });
    },
    delete : function () {
        var _this = this;
        var id = $('#id').val();

        this.withSubmitDisabled($('#btn-delete'), function () {
            return $.ajax({
                type: 'DELETE',
                url: '/api/v1/posts/' + encodeURIComponent(id),
                dataType: 'json',
                contentType: 'application/json; charset=utf-8',
                headers: { 'X-XSRF-TOKEN': _this.getCsrfToken() }
            }).done(function () {
                _this.showMessage('success', '글이 삭제되었습니다.');
                window.location.href = '/';
            }).fail(function (error) {
                _this.handleError(error);
            });
        });
    }

};

main.init();
