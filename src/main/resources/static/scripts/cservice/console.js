var COMMON_EMOJIS = ['😀','😃','😄','😁','😅','😂','🤣','😊','😇','🙂','😉','😌','😍','🥰','😘','😗','😙','😚','😋','😛','😜','🤪','😝','🤑','🤗','🤭','🤫','🤔','😐','😑','😶','😏','👍','👎','👏','🙌','🤝','🙏','❤️','🧡','💛','💚','💙','💜','🖤','🤍','🤎','💔','❣️','💕','💞','💓','💗','💖','💘','💝','😺','😸','😹','😻','😼','😽','🙀','😿','😾'];
var REACTION_EMOJIS = ['👍','❤️','😂','😮','😢','🙏'];

// CSRF：与后端 CookieCsrfTokenRepository 一致，POST 等请求自动带 X-XSRF-TOKEN
if (typeof axios !== 'undefined') {
    axios.defaults.xsrfCookieName = 'XSRF-TOKEN';
    axios.defaults.xsrfHeaderName = 'X-XSRF-TOKEN';
}

new Vue({
    el: '#app',
    data: {
        /** 语言：en | zh | ms，从 cookie 读取，默认 en */
        lang: typeof I18N_CONSOLE !== 'undefined' ? I18N_CONSOLE.getLang() : 'en',
        userId: '',
        username: '',
        nickname: '',
        leftTab: 'user',
        conversationList: [],
        colleaguesList: [],
        selectedConversationId: null,
        selectedFromId: null,
        ws: null,
        /** 按会话 key(conversationId||fromId) 存储消息列表 */
        messageListByConversation: {},
        /** 发送框输入内容 */
        sendText: '',
        /** 是否正在发送（防重复点击） */
        sending: false,
        /** 当前满屏预览的图片 URL（为空则隐藏） */
        previewImageUrl: '',
        /** emoji 选择面板是否展开 */
        emojiOpen: false,
        /** 常用 emoji 列表 */
        commonEmojis: COMMON_EMOJIS,
        /** 右键点赞可选表情 */
        reactionEmojis: REACTION_EMOJIS,
        /** 右键菜单：是否显示、位置、当前消息 */
        contextMenuShow: false,
        contextMenuX: 0,
        contextMenuY: 0,
        contextMenuMessage: null,
        /** 回复某条消息：{ messageId, preview }，有值时输入框上方显示回复条 */
        replyingTo: null,
        /** 点赞表情选择器：当前针对的消息 messageId，有值时显示表情选择 */
        reactionPickerFor: null,
        /** 收到消息提示音：单例 Audio，避免重叠播放 */
        _receivedSoundAudio: null,
        _receivedSoundPlaying: false
    },
    computed: {
        pageTitle: function() {
            return this.t('pageTitle');
        },
        currentMessages: function() {
            var key = this.selectedConversationId || this.selectedFromId;
            if (!key) return [];
            return this.messageListByConversation[key] || [];
        },
        selectedConversationDisplayName: function() {
            var id = this.selectedConversationId;
            var fromId = this.selectedFromId;
            var item = this.conversationList.find(function(c) {
                return c.id === id || c.fromId === fromId;
            });
            return item ? (item.displayName || item.fromId || id || fromId || '') : (fromId || id || '');
        },
        conversationUnreadTotal: function() {
            return this.conversationList.reduce(function(sum, item) {
                return sum + (item.unread || 0);
            }, 0);
        },
        conversationUnreadDisplay: function() {
            var n = this.conversationUnreadTotal;
            return n > 99 ? '99+' : String(n);
        },
        colleaguesUnreadTotal: function() {
            return this.colleaguesList.reduce(function(sum, item) {
                return sum + (item.unread || 0);
            }, 0);
        },
        colleaguesUnreadDisplay: function() {
            var n = this.colleaguesUnreadTotal;
            return n > 99 ? '99+' : String(n);
        }
    },
    watch: {
        lang: function() {
            document.title = this.t('pageTitle');
            if (this._langSelectApi) this._langSelectApi.setValue(this.lang);
        },
        currentMessages: function() {
            var self = this;
            this.$nextTick(function() {
                var el = self.$refs.messageList;
                if (el) el.scrollTop = el.scrollHeight;
            });
        }
    },
    mounted: function() {
        document.title = this.t('pageTitle');
        this.loadUserInfo();
        var self = this;
        if (typeof Dialog !== 'undefined' && Dialog.Select && self.$refs.langSelectMount) {
            self._langSelectApi = Dialog.Select.mount(self.$refs.langSelectMount, {
                items: [ { value: 'en', label: 'EN' }, { value: 'zh', label: '中文' }, { value: 'ms', label: 'BM' } ],
                value: self.lang,
                onChange: function(value) { self.setLang(value); },
                theme: 'minimal'
            });
        }
        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape' && self.previewImageUrl) self.closeImagePreview();
            if (e.key === 'Escape' && self.emojiOpen) self.emojiOpen = false;
        });
        document.addEventListener('click', function() {
            self.emojiOpen = false;
        });
    },
    beforeDestroy: function() {
        if (this._langSelectApi && this._langSelectApi.destroy) this._langSelectApi.destroy();
        if (this._wsHeartbeatIntervalId) {
            clearInterval(this._wsHeartbeatIntervalId);
            this._wsHeartbeatIntervalId = null;
        }
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.close();
        }
    },
    methods: {
        t: function(key) {
            var m = typeof I18N_CONSOLE !== 'undefined' ? I18N_CONSOLE.messages[this.lang] : null;
            return (m && m[key]) || key;
        },
        setLang: function(lang) {
            this.lang = I18N_CONSOLE.setLang(lang);
        },
        getLastCustomerMessagePreview: function(item) {
            var key = item.id || item.fromId;
            var list = this.messageListByConversation[key] || [];
            var msg = null;
            for (var i = list.length - 1; i >= 0; i--) {
                if (!list[i].isStaff) {
                    msg = list[i];
                    break;
                }
            }
            if (!msg) return '';
            if (msg.textBody && msg.textBody.trim()) return msg.textBody.trim();
            var type = msg.messageType || '';
            if (type === 'IMAGE') return this.t('image');
            if (type === 'VIDEO') return '[Video]';
            if (type === 'AUDIO') return '[Audio]';
            return (this.t('fileDownload').split('(')[0] || 'File').trim();
        },
        insertEmoji: function(emo) {
            this.sendText = (this.sendText || '') + emo;
            this.emojiOpen = false;
        },
        loadUserInfo: function() {
            var self = this;
            axios.get('/desk/currentUser')
                .then(function(response) {
                    if (response.data.success) {
                        self.userId = response.data.userId;
                        self.username = response.data.username;
                        self.nickname = response.data.nickname;
                        self.loadConversationsFromServer();
                        self.connectWebSocket();
                    } else {
                        window.location.href = '/csdesk/cserviceLogin.html';
                    }
                })
                .catch(function(error) {
                    console.error('Load user info error:', error);
                    window.location.href = '/csdesk/cserviceLogin.html';
                });
        },
        /** 刷新/登录后从服务端拉取当前用户的会话列表及每个会话的消息（Redis） */
        loadConversationsFromServer: function() {
            var self = this;
            self.conversationList = [];
            self.messageListByConversation = {};
            axios.get('/desk/conversation/list')
                .then(function(res) {
                    if (!res.data.success || !res.data.conversations) return;
                    var conversations = res.data.conversations;
                    if (conversations.length === 0) return;
                    conversations.forEach(function(conv) {
                        var convId = conv.id || conv.fromId;
                        if (!convId) return;
                        var exists = self.conversationList.some(function(c) { return (c.id || c.fromId) === convId; });
                        if (!exists) {
                            self.conversationList.push({
                                id: convId,
                                fromId: convId,
                                displayName: conv.displayName || convId,
                                unread: 0
                            });
                        }
                        self.$set(self.messageListByConversation, convId, []);
                    });
                    conversations.forEach(function(conv) {
                        var convId = conv.id || conv.fromId;
                        if (!convId) return;
                        axios.get('/desk/conversation/messages', { params: { conversationId: convId } })
                            .then(function(msgRes) {
                                if (!msgRes.data.success || !msgRes.data.messages) return;
                                self.$set(self.messageListByConversation, convId, []);
                                var list = self.messageListByConversation[convId];
                                var messages = msgRes.data.messages;
                                var pendingStatus = [];
                                var pendingReaction = [];
                                messages.forEach(function(jsonStr) {
                                    try {
                                        var msg = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr;
                                        if (msg.kind === 'message_status') {
                                            pendingStatus.push(msg);
                                            return;
                                        }
                                        if (msg.kind === 'reaction') {
                                            pendingReaction.push(msg);
                                            return;
                                        }
                                        list.push({
                                            messageId: msg.messageId || null,
                                            textBody: msg.textBody || '',
                                            fromId: msg.fromId || '',
                                            fromProfileName: msg.fromProfileName || msg.fromId || '',
                                            isStaff: msg.isStaff === true,
                                            isSystemReply: msg.isSystemReply === true,
                                            timestamp: msg.timestamp ? msg.timestamp * 1000 : Date.now(),
                                            messageType: msg.messageType || 'TEXT',
                                            messageStatus: msg.messageStatus || null,
                                            mediaUrl: msg.mediaUrl || null,
                                            mediaCaption: msg.mediaCaption || null,
                                            reactions: msg.reactions || [],
                                            quotedMessageId: msg.quotedMessageId || null,
                                            latitude: msg.latitude != null ? msg.latitude : null,
                                            longitude: msg.longitude != null ? msg.longitude : null
                                        });
                                        var status = msg.messageStatus || '';
                                        if ((status === 'NORMAL' || status === 'UNSUPPORTED') && (msg.fromProfileName || msg.fromId)) {
                                            var item = self.conversationList.find(function(c) { return (c.id || c.fromId) === convId; });
                                            if (item && (item.displayName === convId || item.displayName === item.fromId || !item.displayName)) {
                                                self.$set(item, 'displayName', msg.fromProfileName || msg.fromId || convId);
                                            }
                                        }
                                    } catch (e) {
                                        console.error('Parse message error', e);
                                    }
                                });
                                var rank = { SENT: 0, DELIVERED: 1, READ: 2 };
                                function sameId(a, b) {
                                    if (a == null && b == null) return true;
                                    if (a == null || b == null) return false;
                                    return String(a).trim() === String(b).trim();
                                }
                                pendingStatus.forEach(function(msg) {
                                    var mid = msg.messageId;
                                    var newStatus = msg.messageStatus || 'SENT';
                                    var newRank = rank[newStatus] != null ? rank[newStatus] : 0;
                                    for (var i = 0; i < list.length; i++) {
                                        if (sameId(list[i].messageId, mid)) {
                                            var cur = list[i].messageStatus;
                                            var curRank = (cur != null && rank[cur] != null) ? rank[cur] : -1;
                                            if (newRank >= curRank) self.$set(list[i], 'messageStatus', newStatus);
                                            break;
                                        }
                                    }
                                });
                                pendingReaction.forEach(function(msg) {
                                    var targetMid = msg.messageId;
                                    var emoji = (msg.emoji != null && msg.emoji !== '') ? msg.emoji : '👍';
                                    for (var j = 0; j < list.length; j++) {
                                        if (sameId(list[j].messageId, targetMid)) {
                                            list[j].reactions = [emoji];
                                            break;
                                        }
                                    }
                                });
                                if (pendingStatus.length > 0 || pendingReaction.length > 0) {
                                    self.$set(self.messageListByConversation, convId, list.slice());
                                }
                            })
                            .catch(function(err) {
                                console.error('Load messages error for ' + convId, err);
                            });
                    });
                })
                .catch(function(err) {
                    console.error('Load conversation list error', err);
                });
        },
        connectWebSocket: function() {
            var self = this;
            if (self._wsHeartbeatIntervalId) {
                clearInterval(self._wsHeartbeatIntervalId);
                self._wsHeartbeatIntervalId = null;
            }
            var protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            var wsUrl = protocol + '//' + window.location.host + '/ws/cs';
            try {
                self.ws = new WebSocket(wsUrl);
                self.ws.onopen = function() {
                    console.log('WebSocket connected');
                    self.loadConversationsFromServer();
                    var HEARTBEAT_INTERVAL_MS = 25000;
                    self._wsHeartbeatIntervalId = setInterval(function() {
                        if (self.ws && self.ws.readyState === WebSocket.OPEN) {
                            self.ws.send(JSON.stringify({ kind: 'ping' }));
                        }
                    }, HEARTBEAT_INTERVAL_MS);
                };
                self.ws.onmessage = function(ev) {
                    try {
                        var msg = JSON.parse(ev.data);
                        if (msg.kind === 'pong') return;
                        // 状态更新：只更新对应 messageId 的勾选，不追加新消息；只允许往高走（已读不能变回已送达）
                        if (msg.kind === 'message_status') {
                            var key = msg.conversationId;
                            if (!key) return;
                            var list = self.messageListByConversation[key];
                            if (!list) return;
                            var messageId = msg.messageId;
                            var newStatus = msg.messageStatus || 'SENT';
                            var rank = { SENT: 0, DELIVERED: 1, READ: 2 };
                            var newRank = rank[newStatus] != null ? rank[newStatus] : 0;
                            for (var i = 0; i < list.length; i++) {
                                if (list[i].messageId === messageId) {
                                    var cur = list[i].messageStatus;
                                    var curRank = rank[cur] != null ? rank[cur] : 0;
                                    if (newRank >= curRank) {
                                        self.$set(list[i], 'messageStatus', newStatus);
                                    }
                                    return;
                                }
                            }
                            return;
                        }
                        // 点赞/反应：找到被点赞的原消息，在其右下方显示 emoji
                        if (msg.kind === 'reaction') {
                            var key = msg.conversationId;
                            if (!key) return;
                            var list = self.messageListByConversation[key];
                            if (!list) return;
                            var targetMessageId = msg.messageId;
                            var emoji = (msg.emoji != null && msg.emoji !== '') ? msg.emoji : '👍';
                            for (var j = 0; j < list.length; j++) {
                                if (list[j].messageId === targetMessageId) {
                                    self.$set(list[j], 'reactions', [emoji]);
                                    return;
                                }
                            }
                            return;
                        }
                        var fromId = msg.fromId || '';
                        var conversationId = msg.conversationId || '';
                        var displayName = msg.fromProfileName || fromId;
                        var key = conversationId || fromId;
                        var list = self.messageListByConversation[key];
                        if (!list) {
                            self.$set(self.messageListByConversation, key, []);
                            list = self.messageListByConversation[key];
                        }
                        list.push({
                            messageId: msg.messageId || null,
                            textBody: msg.textBody || '',
                            fromId: fromId,
                            fromProfileName: displayName,
                            isStaff: msg.isStaff === true,
                            isSystemReply: msg.isSystemReply === true,
                            timestamp: msg.timestamp ? msg.timestamp * 1000 : Date.now(),
                            messageType: msg.messageType || 'TEXT',
                            mediaUrl: msg.mediaUrl || null,
                            mediaCaption: msg.mediaCaption || null,
                            reactions: [],
                            quotedMessageId: msg.quotedMessageId || null,
                            latitude: msg.latitude != null ? msg.latitude : null,
                            longitude: msg.longitude != null ? msg.longitude : null
                        });
                        if (msg.isStaff !== true) {
                            self.playReceivedSound();
                        }
                        var item = self.conversationList.find(function(c) {
                            var cKey = c.id || c.fromId;
                            return cKey === key || cKey === conversationId || cKey === fromId;
                        });
                        if (item) {
                            var isCurrentConversation = (key === self.selectedConversationId || key === self.selectedFromId);
                            self.$set(item, 'unread', isCurrentConversation ? 0 : (item.unread || 0) + 1);
                            if (conversationId) item.id = conversationId;
                            if (fromId) item.fromId = fromId;
                            // 建立会话后昵称不再变更，仅新建会话时从首条 NORMAL/UNSUPPORTED 消息取昵称
                        } else {
                            self.conversationList.push({
                                id: conversationId || fromId,
                                fromId: fromId,
                                displayName: (displayName && (msg.messageStatus === 'NORMAL' || msg.messageStatus === 'UNSUPPORTED')) ? displayName : (fromId || conversationId || ''),
                                unread: 1
                            });
                        }
                    } catch (e) {
                        console.error('WebSocket message parse error', e);
                    }
                };
                self.ws.onclose = function(ev) {
                    if (self._wsHeartbeatIntervalId) {
                        clearInterval(self._wsHeartbeatIntervalId);
                        self._wsHeartbeatIntervalId = null;
                    }
                    if (ev && ev.code === 4000) {
                        // 被挤下线：不弹提示，直接清空服务端登录信息并跳转登录页
                        axios.post('/desk/logout').finally(function() {
                            window.location.href = '/csdesk/cserviceLogin.html';
                        });
                    } else {
                        console.log('WebSocket closed');
                    }
                };
                self.ws.onerror = function(err) {
                    console.error('WebSocket error', err);
                };
            } catch (e) {
                console.error('WebSocket connect error', e);
            }
        },
        handleLogout: function() {
            if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                this.ws.close();
            }
            axios.post('/desk/logout')
                .then(function(response) {
                    if (response.data.success) {
                        window.location.href = response.data.redirectUrl;
                    }
                })
                .catch(function(error) {
                    console.error('Logout error:', error);
                    window.location.href = '/csdesk/cserviceLogin.html';
                });
        },
        selectConversation: function(item) {
            this.selectedConversationId = item.id;
            this.selectedFromId = item.fromId;
            this.$set(item, 'unread', 0);
        },
        sendMessage: function() {
            var self = this;
            if (this.sending) return;
            var text = (this.sendText || '').trim();
            if (!text) {
                Toast.show(self.t('messageCannotBeEmpty'), { target: self.$refs.sendBtn, type: 'warn' });
                return;
            }
            if (!this.selectedConversationId) {
                Dialog.alert({ title: this.t('alertTitle'), message: this.t('selectConversationFirst') });
                return;
            }
            var quotedMessageId = self.replyingTo ? self.replyingTo.messageId : null;
            this.sending = true;
            var payload = {
                conversationId: this.selectedConversationId,
                textBody: text,
                fromId: this.selectedFromId
            };
            if (quotedMessageId) payload.quotedMessageId = quotedMessageId;
            axios.post('/desk/message/send', payload)
                .then(function(res) {
                    if (res.data.success) {
                        var key = self.selectedConversationId || self.selectedFromId;
                        if (!self.messageListByConversation[key]) {
                            self.$set(self.messageListByConversation, key, []);
                        }
                        self.messageListByConversation[key].push({
                            messageId: res.data.messageId || null,
                            textBody: text,
                            isStaff: true,
                            timestamp: Date.now(),
                            messageStatus: null,
                            quotedMessageId: quotedMessageId || null
                        });
                        self.sendText = '';
                        self.replyingTo = null;
                    } else {
                        Dialog.alert({ title: self.t('sendFailed'), message: res.data.message || self.t('sendFailed') });
                    }
                })
                .catch(function(err) {
                    console.error('Send message error', err);
                    Dialog.alert({ title: self.t('sendFailed'), message: self.t('sendFailed') });
                })
                .finally(function() {
                    self.sending = false;
                });
        },
        clearStorageForConversation: function(conversationId) {
            if (!conversationId) return;
            this.$delete(this.messageListByConversation, conversationId);
            this.conversationList = this.conversationList.filter(function(c) {
                return (c.id || c.fromId) !== conversationId;
            });
            if (this.selectedConversationId === conversationId || this.selectedFromId === conversationId) {
                this.selectedConversationId = null;
                this.selectedFromId = null;
            }
        },
        formatMessageTime: function(ts) {
            if (!ts) return '';
            var d = new Date(ts);
            var y = d.getFullYear();
            var M = (d.getMonth() + 1 < 10 ? '0' : '') + (d.getMonth() + 1);
            var day = (d.getDate() < 10 ? '0' : '') + d.getDate();
            var H = (d.getHours() < 10 ? '0' : '') + d.getHours();
            var m = (d.getMinutes() < 10 ? '0' : '') + d.getMinutes();
            var s = (d.getSeconds() < 10 ? '0' : '') + d.getSeconds();
            return y + '-' + M + '-' + day + ' ' + H + ':' + m + ':' + s;
        },
        /** 收到消息时播放提示音；若正在播放则不重复播放，避免多条消息卡死/共振 */
        playReceivedSound: function() {
            if (this._receivedSoundPlaying) return;
            var self = this;
            if (!this._receivedSoundAudio) {
                this._receivedSoundAudio = new Audio('/csdesk/media/recieved.mp3');
                this._receivedSoundAudio.addEventListener('ended', function() {
                    self._receivedSoundPlaying = false;
                    console.log('[recieved.mp3] 播放结束');
                });
                this._receivedSoundAudio.addEventListener('error', function(e) {
                    self._receivedSoundPlaying = false;
                    console.warn('[recieved.mp3] 加载/播放失败', e.target && e.target.error);
                });
                this._receivedSoundAudio.addEventListener('playing', function() {
                    console.log('[recieved.mp3] 开始播放');
                });
            }
            this._receivedSoundPlaying = true;
            console.log('[recieved.mp3] 尝试播放');
            this._receivedSoundAudio.play().then(function() {
                console.log('[recieved.mp3] play() 成功');
            }).catch(function(err) {
                self._receivedSoundPlaying = false;
                console.warn('[recieved.mp3] play() 失败', err && err.name, err && err.message);
                if (typeof Toast !== 'undefined') {
                    Toast.show('消息提示音未播放（请先点击页面任意处以允许声音）', { type: 'warn' });
                }
            });
        },
        /** 被回复消息的预览文案（从当前会话消息列表查找原消息） */
        getQuotedPreview: function(quotedMessageId) {
            if (!quotedMessageId) return this.t('quotedMessage');
            var list = this.currentMessages;
            for (var i = 0; i < list.length; i++) {
                if (list[i].messageId === quotedMessageId) {
                    var s = list[i].textBody || list[i].mediaCaption || '';
                    if (s) return s.length > 50 ? s.slice(0, 50) + '…' : s;
                    return this.t('quotedMessage');
                }
            }
            return this.t('quotedMessage');
        },
        /** 点击引用块时滚动到原消息 */
        scrollToMessage: function(messageId) {
            if (!messageId || !this.$refs.messageList) return;
            var escaped = typeof CSS !== 'undefined' && CSS.escape ? CSS.escape(messageId) : messageId.replace(/"/g, '\\"');
            var el = this.$refs.messageList.querySelector('[data-message-id="' + escaped + '"]');
            if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        },
        /** 右键消息：显示菜单（点赞表情、回复） */
        onMessageContextMenu: function(ev, msg) {
            ev.preventDefault();
            this.contextMenuMessage = msg;
            this.contextMenuX = ev.clientX;
            this.contextMenuY = ev.clientY;
            this.contextMenuShow = true;
            this.reactionPickerFor = null;
            var self = this;
            setTimeout(function() {
                var handler = function() { self.closeContextMenu(); };
                self._contextMenuCloseHandler = handler;
                document.addEventListener('click', handler);
            }, 0);
        },
        closeContextMenu: function() {
            this.contextMenuShow = false;
            this.contextMenuMessage = null;
            this.reactionPickerFor = null;
            if (this._contextMenuCloseHandler) {
                document.removeEventListener('click', this._contextMenuCloseHandler);
                this._contextMenuCloseHandler = null;
            }
        },
        /** 菜单：点赞表情 -> 显示表情选择 */
        onContextMenuReaction: function() {
            if (!this.contextMenuMessage) return;
            this.reactionPickerFor = this.contextMenuMessage.messageId;
        },
        /** 菜单：回复 -> 设置 replyingTo 并关闭菜单 */
        onContextMenuReply: function() {
            if (!this.contextMenuMessage) return;
            var msg = this.contextMenuMessage;
            var preview = msg.textBody || msg.mediaCaption || '';
            if (preview && preview.length > 40) preview = preview.slice(0, 40) + '…';
            this.replyingTo = { messageId: msg.messageId, preview: preview || this.t('quotedMessage') };
            this.closeContextMenu();
        },
        /** 选择表情后发送 reaction；先立即关闭选择框，再发请求，避免连续多点 */
        onReactionPick: function(emoji) {
            var self = this;
            var msgId = this.reactionPickerFor;
            var convId = this.selectedConversationId || this.selectedFromId;
            if (!msgId || !convId) { self.closeContextMenu(); return; }
            self.closeContextMenu();
            axios.post('/desk/message/reaction', {
                conversationId: convId,
                messageId: msgId,
                emoji: emoji
            }).then(function(res) {
                if (res.data.success) {
                    var list = self.messageListByConversation[convId];
                    if (list) {
                        for (var i = 0; i < list.length; i++) {
                            if (list[i].messageId === msgId) {
                                self.$set(list[i], 'reactions', [emoji]);
                                break;
                            }
                        }
                    }
                }
            });
        },
        /** 关闭回复条 */
        clearReplyingTo: function() {
            this.replyingTo = null;
        },
        /** 格式化位置显示：纬度, 经度（保留 3 位小数） */
        formatLocation: function(lat, lng) {
            var a = (lat != null && !isNaN(lat)) ? Number(lat).toFixed(3) : '';
            var b = (lng != null && !isNaN(lng)) ? Number(lng).toFixed(3) : '';
            if (a && b) return a + ', ' + b;
            if (a) return a;
            if (b) return b;
            return '';
        },
        /** 点击「在地图中查看」：弹出选择框（Google/苹果/Waze/必应），选择后在新标签打开对应地图 */
        openLocationInMap: function(lat, lng) {
            var self = this;
            var la = lat != null && !isNaN(lat) ? Number(lat) : null;
            var lo = lng != null && !isNaN(lng) ? Number(lng) : null;
            if (la == null && lo == null) return;
            var latVal = (la != null ? la : 0);
            var lngVal = (lo != null ? lo : 0);
            var urlByValue = {
                google: 'https://www.google.com/maps?q=' + latVal + ',' + lngVal,
                apple: 'https://maps.apple.com/?q=' + latVal + ',' + lngVal,
                waze: 'https://www.waze.com/ul?ll=' + latVal + ',' + lngVal + '&navigate=yes',
                bing: 'https://www.bing.com/maps?q=' + latVal + ',' + lngVal
            };
            Dialog.choose({
                title: self.t('mapChoiceTitle'),
                options: [
                    { text: self.t('mapChoiceGoogle'), value: 'google' },
                    { text: self.t('mapChoiceApple'), value: 'apple' },
                    { text: self.t('mapChoiceWaze'), value: 'waze' },
                    { text: self.t('mapChoiceBing'), value: 'bing' }
                ],
                cancelText: self.t('close'),
                optionsListStyle: true,
                theme: 'darkHeader'
            }).then(function(value) {
                if (value && urlByValue[value]) window.open(urlByValue[value], '_blank', 'noopener,noreferrer');
            });
        },
        mediaProxyUrl: function(url) {
            if (!url) return '';
            return '/desk/media/proxy?url=' + encodeURIComponent(url);
        },
        openImagePreview: function(url) {
            if (url) this.previewImageUrl = url;
        },
        closeImagePreview: function() {
            this.previewImageUrl = '';
        },
        onImageError: function(ev) {
            if (ev && ev.target) {
                ev.target.alt = '图片加载失败';
                ev.target.style.background = '#f1f5f9';
                ev.target.style.minWidth = '120px';
                ev.target.style.minHeight = '80px';
            }
        },
        onMediaError: function(ev) {
            if (ev && ev.target) {
                ev.target.style.display = 'none';
                var span = document.createElement('span');
                span.className = 'chat-message-media-fail';
                span.textContent = '视频/音频加载失败';
                ev.target.parentNode.appendChild(span);
            }
        },
        endSession: function() {
            var self = this;
            if (!this.selectedConversationId) {
                Dialog.alert({ title: self.t('alertTitle'), message: self.t('selectConversationFirst') });
                return;
            }
            var convId = this.selectedConversationId;
            var fromId = self.selectedFromId || convId;
            var item = this.conversationList.find(function(c) {
                return c.id === convId || c.fromId === self.selectedFromId;
            });
            var nickname = item ? (item.displayName || item.fromId || fromId) : fromId;
            var msg = self.t('confirmEndMessage').replace('{nickname}', nickname).replace('{fromId}', fromId);
            Dialog.confirm({
                title: self.t('confirmEndTitle'),
                message: msg
            }).then(function(ok) {
                if (!ok) return;
                axios.post('/desk/conversation/end', { conversationId: convId })
                    .then(function(res) {
                        if (res.data.success) {
                            self.clearStorageForConversation(convId);
                        } else {
                            Dialog.alert({ title: self.t('endSessionFailed'), message: res.data.message || self.t('endSessionFailed') });
                        }
                    })
                    .catch(function(err) {
                        console.error('End session error', err);
                        Dialog.alert({ title: self.t('endSessionFailed'), message: self.t('endSessionFailed') });
                    });
            });
        },
        handleTransferClick: function() {
            var toUserId = prompt(this.t('enterToUserId'));
            if (toUserId != null && toUserId.trim()) {
                this.transferSession(toUserId.trim());
            }
        },
        transferSession: function(toUserId) {
            var self = this;
            if (!this.selectedConversationId || !toUserId) {
                Dialog.alert({ title: this.t('alertTitle'), message: this.t('selectConversationFirst') });
                return;
            }
            var convId = this.selectedConversationId;
            axios.post('/desk/conversation/transfer', {
                conversationId: convId,
                toUserId: toUserId
            })
                .then(function(res) {
                    if (res.data.success) {
                        self.clearStorageForConversation(convId);
                        Dialog.alert({ title: self.t('transferSuccess'), message: self.t('transferSuccess') });
                    } else {
                        Dialog.alert({ title: self.t('transferFailed'), message: res.data.message || self.t('transferFailed') });
                    }
                })
                .catch(function(err) {
                    console.error('Transfer error', err);
                    Dialog.alert({ title: self.t('transferFailed'), message: self.t('transferFailed') });
                });
        },
        unreadDisplay: function(n) {
            return n > 99 ? '99+' : String(n);
        }
    }
});
