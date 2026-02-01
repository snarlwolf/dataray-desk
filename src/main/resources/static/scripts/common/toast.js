/**
 * 通用淡入淡出提示框，可在任意页面通过 Toast.show() 调用。
 * 使用方式：
 *   Toast.show('消息不能为空');
 *   Toast.show('操作成功', { type: 'success' });
 *   Toast.show('提示', { target: element, duration: 2500 });  // 在 target 上方显示
 */
(function(global) {
    'use strict';

    var STYLE_ID = 'csdesk-toast-styles';
    var fontFamily = '-apple-system, BlinkMacSystemFont, \'Segoe UI\', Roboto, \'PingFang SC\', \'Microsoft YaHei\', sans-serif';

    function ensureStyles() {
        if (document.getElementById(STYLE_ID)) return;
        var style = document.createElement('style');
        style.id = STYLE_ID;
        style.textContent = [
            '.csdesk-toast{position:fixed;z-index:10001;padding:8px 14px;border-radius:8px;font-size:12px;font-family:' + fontFamily + ';line-height:1.4;box-shadow:0 4px 16px rgba(0,0,0,0.12);animation:csdesk-toast-fadeIn 0.3s ease forwards;}',
            '.csdesk-toast-warn{background:linear-gradient(180deg,#fef9c3 0%,#fef08a 100%);color:#854d0e;border:1px solid #facc15;}',
            '.csdesk-toast-success{background:linear-gradient(180deg,#dcfce7 0%,#bbf7d0 100%);color:#166534;border:1px solid #22c55e;}',
            '.csdesk-toast-info{background:linear-gradient(180deg,#e0f2fe 0%,#bae6fd 100%);color:#0c4a6e;border:1px solid #0ea5e9;}',
            '.csdesk-toast-fadeout{animation:csdesk-toast-fadeOut 2s ease forwards;}',
            '@keyframes csdesk-toast-fadeIn{from{opacity:0;}to{opacity:1;}}',
            '@keyframes csdesk-toast-fadeOut{from{opacity:1;}to{opacity:0;}}'
        ].join('');
        (document.head || document.documentElement).appendChild(style);
    }

    function show(message, options) {
        options = options || {};
        var type = options.type || 'warn';
        var target = options.target;

        ensureStyles();
        var el = document.createElement('div');
        el.className = 'csdesk-toast csdesk-toast-' + type;
        el.textContent = message || '';

        if (target && target.getBoundingClientRect) {
            var rect = target.getBoundingClientRect();
            var toastHeight = 44;
            var top = rect.top - toastHeight - 10;
            if (top < 20) top = 20;
            el.style.top = top + 'px';
            el.style.left = (rect.left + rect.width / 2) + 'px';
            el.style.transform = 'translateX(-50%)';
        } else {
            el.style.top = '80px';
            el.style.left = '50%';
            el.style.transform = 'translateX(-50%)';
        }

        document.body.appendChild(el);

        var showMs = options.duration != null ? options.duration : 300;
        var fadeOutMs = 2000;
        var t = setTimeout(function() {
            clearTimeout(t);
            el.classList.add('csdesk-toast-fadeout');
            setTimeout(function() {
                if (el.parentNode) el.parentNode.removeChild(el);
            }, fadeOutMs);
        }, showMs);
    }

    global.Toast = {
        show: show
    };
})(typeof window !== 'undefined' ? window : this);
