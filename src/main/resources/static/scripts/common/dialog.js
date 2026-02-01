/**
 * 通用对话框：与页面风格一致的 confirm / alert / choose，可在任意页面通过 Dialog 调用。
 * 使用方式：
 *   Dialog.confirm({ title: '标题', message: '确认内容' }).then(function(ok) { if (ok) { ... } });
 *   Dialog.alert({ title: '提示', message: '内容' }).then(function() { ... });
 *   Dialog.choose({ title: '选择', message: '说明', options: [{ text: '选项A', value: 'a' }, ...], cancelText: '取消' }).then(function(value) { if (value) { ... } });
 */
(function(global) {
    'use strict';

    /** 将字符串转义为安全 HTML，防止 DOM XSS（用于写入 innerHTML 的用户输入） */
    function escapeHtml(str) {
        if (str == null) return '';
        var s = String(str);
        return s
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    var STYLE_ID = 'csdesk-dialog-styles';
    var fontFamily = '-apple-system, BlinkMacSystemFont, \'Segoe UI\', Roboto, \'PingFang SC\', \'Microsoft YaHei\', sans-serif';

    function ensureStyles() {
        if (document.getElementById(STYLE_ID)) return;
        var style = document.createElement('style');
        style.id = STYLE_ID;
        style.textContent = [
            '.csdesk-dialog-overlay{position:fixed;inset:0;z-index:10000;background:rgba(0,0,0,0.4);display:flex;align-items:center;justify-content:center;padding:20px;box-sizing:border-box;animation:csdesk-dialog-fadeIn 0.2s ease;}',
            '.csdesk-dialog-box{background:#fff;border-radius:12px;box-shadow:0 8px 32px rgba(0,0,0,0.15);min-width:320px;max-width:420px;overflow:hidden;animation:csdesk-dialog-scaleIn 0.2s ease;}',
            '.csdesk-dialog-header{display:flex;align-items:center;padding:16px 20px;border-bottom:1px solid #e0e6ed;font-size:0.875em;font-weight:normal;color:#334155;}',
            '.csdesk-dialog-header-icon{margin-right:0.5em;font-size:1em;}',
            '.csdesk-dialog-body{padding:20px;font-size:16px;line-height:1.6;color:#475569;}',
            '.csdesk-dialog-footer{display:flex;justify-content:flex-end;gap:12px;padding:14px 20px;border-top:1px solid #e0e6ed;background:#f8fafc;}',
            '.csdesk-dialog-btn{display:inline-flex;align-items:center;gap:0.35em;padding:8px 18px;border-radius:8px;font-size:14px;cursor:pointer;border:none;font-family:' + fontFamily + ';transition:background 0.2s,color 0.2s;}',
            '.csdesk-dialog-btn-icon{font-size:1em;}',
            '.csdesk-dialog-btn-cancel{background:#fff;color:#64748b;border:1px solid #e0e6ed;}',
            '.csdesk-dialog-btn-cancel:hover{background:#f1f5f9;}',
            '.csdesk-dialog-btn-primary{background:#0284c7;color:#fff;}',
            '.csdesk-dialog-btn-primary:hover{background:#0369a1;}',
            '.csdesk-dialog-choose-options{display:flex;flex-direction:column;gap:8px;margin-top:12px;}',
            '.csdesk-dialog-choose-options-list{gap:0;margin-top:0;}',
            '.csdesk-dialog-choose-options-list .csdesk-dialog-choose-btn{border-radius:0;border-top:none;}',
            '.csdesk-dialog-choose-options-list .csdesk-dialog-choose-btn:first-child{border-radius:8px 8px 0 0;border-top:1px solid #e0e6ed;}',
            '.csdesk-dialog-choose-options-list .csdesk-dialog-choose-btn:last-child{border-radius:0 0 8px 8px;}',
            '.csdesk-dialog-choose-btn{display:block;width:100%;padding:10px 16px;text-align:left;font-size:14px;cursor:pointer;border:1px solid #e0e6ed;border-radius:8px;background:#fff;color:#334155;font-family:' + fontFamily + ';transition:background 0.2s,color 0.2s,border-color 0.2s;}',
            '.csdesk-dialog-choose-btn:hover{background:#f1f5f9;border-color:#cbd5e1;}',
            '.csdesk-dialog-choose-dark-header .csdesk-dialog-header{background:#0369a1;color:#fff;border-bottom:none;border-radius:12px 12px 0 0;}',
            '.csdesk-dialog-choose-dark-header .csdesk-dialog-body{padding:0;}',
            '.csdesk-dialog-choose-dark-header .csdesk-dialog-body.csdesk-dialog-body-message{padding:1em 1em 2em 1em;}',
            '.csdesk-dialog-choose-dark-header .csdesk-dialog-footer{background:#0369a1;border-top:none;border-radius:0 0 12px 12px;justify-content:center;}',
            '.csdesk-dialog-choose-dark-header .csdesk-dialog-footer .csdesk-dialog-btn{background:transparent;border:none;color:#fff;}',
            '.csdesk-dialog-choose-dark-header .csdesk-dialog-footer .csdesk-dialog-btn:hover{background:rgba(255,255,255,0.15);}',
            '.csdesk-dialog-choose-dark-header .csdesk-dialog-choose-options-list .csdesk-dialog-choose-btn{border:none;border-radius:0;border-bottom:1px solid #bae6fd;}',
            '.csdesk-dialog-choose-dark-header .csdesk-dialog-choose-options-list .csdesk-dialog-choose-btn:last-child{border-bottom:none;}',
            '.csdesk-dialog-choose-dark-header .csdesk-dialog-choose-options-list .csdesk-dialog-choose-btn:first-child{border-radius:0;}',
            '.csdesk-dialog-choose-dark-header .csdesk-dialog-choose-btn:hover{background:#e0f2fe;}',
            '@keyframes csdesk-dialog-fadeIn{from{opacity:0;}to{opacity:1;}}',
            '@keyframes csdesk-dialog-scaleIn{from{opacity:0;transform:scale(0.95);}to{opacity:1;transform:scale(1);}}'
        ].join('');
        (document.head || document.documentElement).appendChild(style);
    }

    var SELECT_STYLE_ID = 'csdesk-select-styles';
    function ensureSelectStyles() {
        if (document.getElementById(SELECT_STYLE_ID)) return;
        var style = document.createElement('style');
        style.id = SELECT_STYLE_ID;
        style.textContent = [
            '.csdesk-select-wrap{position:relative;display:inline-flex;align-items:center;min-height:36px;min-width:64px;font-family:' + fontFamily + ';}',
            '.csdesk-select-trigger{display:inline-flex;align-items:center;gap:8px;padding:0 28px 0 12px;width:100%;min-height:36px;cursor:pointer;font-size:14px;color:#334155;background:#fff;border:1px solid #e0e6ed;border-radius:8px;box-sizing:border-box;transition:background 0.2s,border-color 0.2s;}',
            '.csdesk-select-trigger:hover{border-color:#94a3b8;background:#f8fafc;}',
            '.csdesk-select-trigger .csdesk-select-caret{font-size:0.75rem;opacity:0.8;margin-left:auto;}',
            '.csdesk-select-trigger-minimal{background:transparent;border:none;color:inherit;}',
            '.csdesk-select-trigger-minimal:hover{background:transparent;border:none;}',
            '.csdesk-select-dropdown{position:absolute;left:0;top:100%;margin-top:4px;min-width:100%;background:#fff;border:1px solid #e0e6ed;border-radius:8px;box-shadow:0 4px 16px rgba(0,0,0,0.12);z-index:1000;overflow:hidden;opacity:0;}',
            '.csdesk-select-dropdown-visible{animation:csdesk-select-fadeIn 0.3s ease forwards;}',
            '@keyframes csdesk-select-fadeIn{from{opacity:0;}to{opacity:1;}}',
            '.csdesk-select-option{padding:10px 14px;font-size:14px;color:#334155;cursor:pointer;transition:background 0.15s;}',
            '.csdesk-select-option:hover{background:#f1f5f9;}',
            '.csdesk-select-option-active{background:#e0f2fe;}',
            '.csdesk-select-option-active:hover{background:#bae6fd;}'
        ].join('');
        (document.head || document.documentElement).appendChild(style);
    }

    function createOverlay() {
        var overlay = document.createElement('div');
        overlay.className = 'csdesk-dialog-overlay';
        return overlay;
    }

    function setHeaderWithIcon(headerEl, title) {
        headerEl.innerHTML = '';
        var icon = document.createElement('i');
        icon.className = 'bi bi-exclamation-triangle-fill csdesk-dialog-header-icon';
        icon.setAttribute('aria-hidden', 'true');
        var span = document.createElement('span');
        span.textContent = title || '提示';
        headerEl.appendChild(icon);
        headerEl.appendChild(span);
    }

    function createBox(title, message, buttons, useDarkHeader) {
        var box = document.createElement('div');
        box.className = 'csdesk-dialog-box' + (useDarkHeader ? ' csdesk-dialog-choose-dark-header' : '');
        box.innerHTML =
            '<div class="csdesk-dialog-header"></div>' +
            '<div class="csdesk-dialog-body"></div>' +
            '<div class="csdesk-dialog-footer"></div>';
        setHeaderWithIcon(box.querySelector('.csdesk-dialog-header'), title || '提示');
        var bodyEl = box.querySelector('.csdesk-dialog-body');
        bodyEl.textContent = message || '';
        if (useDarkHeader) bodyEl.className += ' csdesk-dialog-body-message';
        var footer = box.querySelector('.csdesk-dialog-footer');
        buttons.forEach(function(b) {
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'csdesk-dialog-btn ' + (b.primary ? 'csdesk-dialog-btn-primary' : 'csdesk-dialog-btn-cancel');
            var iconClass = b.primary ? 'bi-check' : 'bi-x';
            btn.innerHTML = '<span>' + escapeHtml(b.text || '') + '</span><i class="bi ' + iconClass + ' csdesk-dialog-btn-icon" aria-hidden="true"></i>';
            btn.onclick = function() {
                b.onClick();
            };
            footer.appendChild(btn);
        });
        return box;
    }

    function close(overlay) {
        if (overlay && overlay.parentNode) {
            overlay.parentNode.removeChild(overlay);
        }
    }

    /**
     * 确认框：确定 / 取消
     * @param {Object} options - { title?: string, message?: string }
     * @returns {Promise<boolean>} 点击确定 resolve(true)，取消 resolve(false)
     */
    function confirm(options) {
        options = options || {};
        var title = options.title != null ? options.title : '确认';
        var message = options.message != null ? options.message : '';
        ensureStyles();
        return new Promise(function(resolve) {
            var overlay = createOverlay();
            var resolveOnce = function(val) {
                return function() {
                    close(overlay);
                    resolve(val);
                };
            };
            var box = createBox(title, message, [
                { text: '取消', primary: false, onClick: resolveOnce(false) },
                { text: '确定', primary: true, onClick: resolveOnce(true) }
            ], true);
            overlay.appendChild(box);
            overlay.addEventListener('click', function(e) {
                if (e.target === overlay) resolveOnce(false)();
            });
            document.body.appendChild(overlay);
        });
    }

    /**
     * 选择框：多选项，风格与 confirm/alert 一致
     * @param {Object} options - { title?: string, message?: string, options: [{ text: string, value: any }], cancelText?: string, optionsListStyle?: boolean, theme?: 'darkHeader' } theme 为 darkHeader 时顶底深蓝、白底列表、浅蓝分隔线
     * @returns {Promise<any|null>} 点击某选项 resolve(option.value)，点击取消 resolve(null)
     */
    function choose(options) {
        options = options || {};
        var title = options.title != null ? options.title : '选择';
        var message = options.message != null ? options.message : '';
        var items = options.options || [];
        var cancelText = options.cancelText != null ? options.cancelText : '取消';
        var optionsListStyle = options.optionsListStyle === true;
        var theme = options.theme === 'darkHeader' ? 'darkHeader' : '';
        ensureStyles();
        return new Promise(function(resolve) {
            var overlay = createOverlay();
            var resolveOnce = function(val) {
                return function() {
                    close(overlay);
                    resolve(val);
                };
            };
            var box = document.createElement('div');
            box.className = 'csdesk-dialog-box' + (theme === 'darkHeader' ? ' csdesk-dialog-choose-dark-header' : '');
            box.innerHTML =
                '<div class="csdesk-dialog-header"></div>' +
                '<div class="csdesk-dialog-body"></div>' +
                '<div class="csdesk-dialog-footer"></div>';
            setHeaderWithIcon(box.querySelector('.csdesk-dialog-header'), title);
            var body = box.querySelector('.csdesk-dialog-body');
            if (message) {
                var msgEl = document.createElement('div');
                msgEl.textContent = message;
                body.appendChild(msgEl);
            }
            var optionsWrap = document.createElement('div');
            optionsWrap.className = 'csdesk-dialog-choose-options' + (optionsListStyle ? ' csdesk-dialog-choose-options-list' : '');
            items.forEach(function(item) {
                var btn = document.createElement('button');
                btn.type = 'button';
                btn.className = 'csdesk-dialog-choose-btn';
                btn.textContent = item.text;
                btn.onclick = function() {
                    resolveOnce(item.value)();
                };
                optionsWrap.appendChild(btn);
            });
            body.appendChild(optionsWrap);
            var footer = box.querySelector('.csdesk-dialog-footer');
            var cancelBtn = document.createElement('button');
            cancelBtn.type = 'button';
            cancelBtn.className = 'csdesk-dialog-btn csdesk-dialog-btn-cancel';
            cancelBtn.innerHTML = '<span>' + escapeHtml(cancelText) + '</span><i class="bi bi-x csdesk-dialog-btn-icon" aria-hidden="true"></i>';
            cancelBtn.onclick = resolveOnce(null);
            footer.appendChild(cancelBtn);
            overlay.appendChild(box);
            overlay.addEventListener('click', function(e) {
                if (e.target === overlay) resolveOnce(null)();
            });
            document.body.appendChild(overlay);
        });
    }

    /**
     * 提示框：仅确定
     * @param {Object} options - { title?: string, message?: string }
     * @returns {Promise<void>} 点击确定后 resolve
     */
    function alert(options) {
        options = options || {};
        var title = options.title != null ? options.title : '提示';
        var message = options.message != null ? options.message : '';
        ensureStyles();
        return new Promise(function(resolve) {
            var overlay = createOverlay();
            var box = createBox(title, message, [
                { text: '确定', primary: true, onClick: function() {
                    close(overlay);
                    resolve();
                } }
            ], true);
            overlay.appendChild(box);
            overlay.addEventListener('click', function(e) {
                if (e.target === overlay) {
                    close(overlay);
                    resolve();
                }
            });
            document.body.appendChild(overlay);
        });
    }

    /**
     * 自建选择组件：与系统风格一致的下拉选择
     * @param {HTMLElement} container - 挂载容器
     * @param {Object} options - { items: [{ value, label }], value: string, onChange: function(value){}, theme?: 'minimal'|'default' }
     * @returns {{ setValue: function(value): void, destroy: function(): void }}
     */
    function selectMount(container, options) {
        options = options || {};
        var items = options.items || [];
        var currentValue = options.value != null ? options.value : (items[0] && items[0].value);
        var onChange = typeof options.onChange === 'function' ? options.onChange : function() {};
        var theme = options.theme === 'minimal' ? 'minimal' : 'default';
        ensureSelectStyles();

        var wrap = document.createElement('div');
        wrap.className = 'csdesk-select-wrap';

        var trigger = document.createElement('div');
        trigger.className = 'csdesk-select-trigger' + (theme === 'minimal' ? ' csdesk-select-trigger-minimal' : '');
        trigger.setAttribute('role', 'button');
        trigger.setAttribute('tabindex', '0');
        trigger.setAttribute('aria-haspopup', 'listbox');
        trigger.setAttribute('aria-expanded', 'false');

        var triggerLabel = document.createElement('span');
        triggerLabel.className = 'csdesk-select-label';

        var triggerCaret = document.createElement('i');
        triggerCaret.className = 'bi bi-caret-down-fill csdesk-select-caret';
        triggerCaret.setAttribute('aria-hidden', 'true');

        trigger.appendChild(triggerLabel);
        trigger.appendChild(triggerCaret);
        wrap.appendChild(trigger);

        var dropdown = document.createElement('div');
        dropdown.className = 'csdesk-select-dropdown';
        dropdown.setAttribute('role', 'listbox');
        dropdown.style.display = 'none';
        items.forEach(function(item) {
            var opt = document.createElement('div');
            opt.className = 'csdesk-select-option' + (item.value === currentValue ? ' csdesk-select-option-active' : '');
            opt.setAttribute('role', 'option');
            opt.setAttribute('data-value', item.value);
            opt.textContent = item.label;
            opt.addEventListener('click', function(e) {
                e.stopPropagation();
                var v = item.value;
                currentValue = v;
                setLabel(v);
                close();
                updateOptionActive();
                onChange(v);
            });
            dropdown.appendChild(opt);
        });
        wrap.appendChild(dropdown);

        function getLabel(value) {
            var item = items.filter(function(i) { return i.value === value; })[0];
            return item ? item.label : value;
        }
        function setLabel(value) {
            triggerLabel.textContent = getLabel(value);
        }
        function updateOptionActive() {
            var opts = dropdown.querySelectorAll('.csdesk-select-option');
            opts.forEach(function(o) {
                var v = o.getAttribute('data-value');
                o.classList.toggle('csdesk-select-option-active', v === currentValue);
            });
        }
        setLabel(currentValue);

        function open() {
            dropdown.classList.remove('csdesk-select-dropdown-visible');
            dropdown.style.display = 'block';
            trigger.setAttribute('aria-expanded', 'true');
            toggleDocListener(true);
            requestAnimationFrame(function() {
                requestAnimationFrame(function() {
                    dropdown.classList.add('csdesk-select-dropdown-visible');
                });
            });
        }
        function close() {
            dropdown.classList.remove('csdesk-select-dropdown-visible');
            dropdown.style.display = 'none';
            trigger.setAttribute('aria-expanded', 'false');
            toggleDocListener(false);
        }
        function toggle() {
            if (dropdown.style.display === 'none') open(); else close();
        }

        var docListener = function(e) {
            if (!wrap.contains(e.target)) {
                close();
            }
        };
        function toggleDocListener(on) {
            if (on) {
                setTimeout(function() { document.addEventListener('click', docListener); }, 0);
            } else {
                document.removeEventListener('click', docListener);
            }
        }

        trigger.addEventListener('click', function(e) {
            e.stopPropagation();
            toggle();
        });
        trigger.addEventListener('keydown', function(e) {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                toggle();
            }
            if (e.key === 'Escape') close();
        });

        container.innerHTML = '';
        container.appendChild(wrap);

        return {
            setValue: function(value) {
                currentValue = value;
                setLabel(value);
                updateOptionActive();
            },
            destroy: function() {
                toggleDocListener(false);
                if (wrap.parentNode) wrap.parentNode.removeChild(wrap);
            }
        };
    }

    global.Dialog = {
        confirm: confirm,
        alert: alert,
        choose: choose,
        Select: { mount: selectMount }
    };
})(typeof window !== 'undefined' ? window : this);
