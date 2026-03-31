// CSRF：与后端 CookieCsrfTokenRepository 一致，POST 等请求自动带 X-XSRF-TOKEN
if (typeof axios !== 'undefined') {
    axios.defaults.xsrfCookieName = 'XSRF-TOKEN';
    axios.defaults.xsrfHeaderName = 'X-XSRF-TOKEN';
}

new Vue({
    el: '#app',
    data: {
        username: '',
        password: '',
        captcha: '',
        captchaImage: '',
        loading: false
    },
    mounted: function() {
        // 必须与 checkLogin 串行：首次无 JSESSIONID 时若并行请求 captcha，会生成两个 Session，
        // 验证码与最终 Cookie 可能不一致，导致第一次必错、第二次才对。
        this.checkLoginStatus();
    },
    methods: {
        checkLoginStatus: function() {
            var self = this;
            return axios.get('/desk/checkLogin')
                .then(function(response) {
                    if (response.data.loggedIn) {
                        window.location.href = response.data.redirectUrl;
                    } else {
                        return self.refreshCaptcha();
                    }
                })
                .catch(function(error) {
                    console.error('Check login error:', error);
                    return self.refreshCaptcha();
                });
        },
        refreshCaptcha: function() {
            var self = this;
            return axios.get('/desk/captcha')
                .then(function(response) {
                    self.captchaImage = response.data.image;
                    self.captcha = '';
                })
                .catch(function(error) {
                    console.error('Get captcha error:', error);
                });
        },
        handleLogin: function(kickOtherSession) {
            var self = this;
            kickOtherSession = !!kickOtherSession;

            if (!this.username || !this.password || !this.captcha) {
                Toast.show('Please fill in all fields', { type: 'warn', target: this.$refs.loginBtn });
                return;
            }

            this.loading = true;

            var params = new URLSearchParams();
            params.append('username', this.username);
            params.append('password', this.password);
            params.append('captcha', this.captcha);
            if (kickOtherSession) params.append('kickOtherSession', 'true');

            axios.post('/desk/sysUserLogin', params, {
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                }
            })
            .then(function(response) {
                if (response.data.success) {
                    window.location.href = response.data.redirectUrl;
                } else if (response.data.alreadyLoggedInElsewhere) {
                    if (typeof Dialog !== 'undefined' && Dialog.confirm) {
                        Dialog.confirm({
                            title: '提示',
                            message: '本账号已在其他地方登录，你确定要将其挤下吗？'
                        }).then(function(ok) {
                            if (ok) self.handleLogin(true);
                        });
                    } else {
                        Toast.show('本账号已在其他地方登录', { type: 'warn', target: self.$refs.loginBtn });
                        self.refreshCaptcha();
                    }
                } else {
                    Toast.show(response.data.message || 'Login failed', { type: 'warn', target: self.$refs.loginBtn });
                    self.refreshCaptcha();
                }
            })
            .catch(function(error) {
                console.error('Login error:', error);
                Toast.show('Network error, please try again', { type: 'warn', target: self.$refs.loginBtn });
                self.refreshCaptcha();
            })
            .finally(function() {
                self.loading = false;
            });
        }
    }
});
