;(function (Vue, axios, ElementPlus) {
    'use strict';

    var ElMessage = ElementPlus.ElMessage;
    var ElMessageBox = ElementPlus.ElMessageBox;

    Vue.createApp({

        data: function () {
            return {
                activeTab: 'rustfs',

                // RustFS
                uploadPath: '',
                selectedFile: null,
                uploading: false,
                fileList: [],
                filesLoading: false,

                // Valkey
                vkSetKey: '',
                vkSetValue: '',
                vkSetTtl: 0,
                vkGetKey: '',
                vkResult: null,
                vkQueried: false
            };
        },

        mounted: function () {
            this.loadFiles();
        },

        methods: {

            // ======================== RustFS ========================

            onFileSelected: function (file) {
                this.selectedFile = file.raw;
                if (!this.uploadPath) {
                    this.uploadPath = file.raw.name;
                }
            },

            doUpload: function () {
                if (!this.selectedFile) return;
                if (!this.uploadPath) {
                    ElMessage.warning('请输入存储路径');
                    return;
                }
                var self = this;
                self.uploading = true;
                var fd = new FormData();
                fd.append('file', self.selectedFile);
                fd.append('objectPath', self.uploadPath);

                axios.post('/page/rustfs/api/upload', fd, {
                    headers: { 'Content-Type': 'multipart/form-data' }
                }).then(function (res) {
                    var d = res.data;
                    if (d.success) {
                        ElMessage.success('上传成功');
                        self.selectedFile = null;
                        self.uploadPath = '';
                        self.loadFiles();
                    } else {
                        ElMessage.error(d.error || '上传失败');
                    }
                }).catch(function (e) {
                    ElMessage.error('请求失败: ' + (e.response && e.response.data && e.response.data.error || e.message));
                }).finally(function () {
                    self.uploading = false;
                });
            },

            loadFiles: function () {
                var self = this;
                self.filesLoading = true;
                axios.get('/page/rustfs/api/list').then(function (res) {
                    var d = res.data;
                    self.fileList = d.success ? (d.items || []) : [];
                }).catch(function () {
                    self.fileList = [];
                }).finally(function () {
                    self.filesLoading = false;
                });
            },

            doDownload: function (id) {
                window.open('/page/rustfs/api/download?id=' + id, '_blank');
            },

            doDelete: function (row) {
                var self = this;
                ElMessageBox.confirm('确定删除文件 "' + row.file_name + '" ？', '确认', {
                    type: 'warning'
                }).then(function () {
                    axios.post('/page/rustfs/api/delete', { id: row.id }).then(function (res) {
                        var d = res.data;
                        if (d.success) {
                            ElMessage.success('已删除');
                            self.loadFiles();
                        } else {
                            ElMessage.error(d.error || '删除失败');
                        }
                    }).catch(function () {
                        ElMessage.error('请求失败');
                    });
                }).catch(function () {});
            },

            // ======================== Valkey ========================

            doSet: function () {
                var self = this;
                if (!self.vkSetKey) { ElMessage.warning('请输入键名'); return; }
                axios.post('/page/valkey/api/set', {
                    key: self.vkSetKey,
                    value: self.vkSetValue,
                    ttl: self.vkSetTtl || 0
                }).then(function (res) {
                    var d = res.data;
                    if (d.success) {
                        ElMessage.success('设置成功');
                    } else {
                        ElMessage.error(d.error || '设置失败');
                    }
                }).catch(function () {
                    ElMessage.error('请求失败');
                });
            },

            doGet: function () {
                var self = this;
                if (!self.vkGetKey) { ElMessage.warning('请输入键名'); return; }
                self.vkResult = null;
                self.vkQueried = false;
                axios.get('/page/valkey/api/get', { params: { key: self.vkGetKey } }).then(function (res) {
                    var d = res.data;
                    if (d.success) {
                        self.vkResult = d.data;
                        self.vkQueried = false;
                    } else {
                        self.vkResult = null;
                        self.vkQueried = true;
                    }
                }).catch(function () {
                    self.vkResult = null;
                    self.vkQueried = true;
                });
            },

            doDelKey: function () {
                var self = this;
                ElMessageBox.confirm('确定删除键 "' + self.vkGetKey + '" ？', '确认', {
                    type: 'warning'
                }).then(function () {
                    axios.post('/page/valkey/api/del', { key: self.vkGetKey }).then(function (res) {
                        var d = res.data;
                        if (d.success) {
                            ElMessage.success('已删除');
                            self.vkResult = null;
                            self.vkQueried = false;
                        } else {
                            ElMessage.error(d.error || '删除失败');
                        }
                    }).catch(function () {
                        ElMessage.error('请求失败');
                    });
                }).catch(function () {});
            },

            // ======================== 工具 ========================

            formatSize: function (bytes) {
                if (!bytes || bytes === 0) return '0 B';
                var units = ['B', 'KB', 'MB', 'GB'];
                var i = Math.floor(Math.log(bytes) / Math.log(1024));
                return (bytes / Math.pow(1024, i)).toFixed(i > 0 ? 1 : 0) + ' ' + units[i];
            },

            formatTime: function (t) {
                if (!t) return '';
                var d = new Date(t);
                if (isNaN(d.getTime())) return String(t);
                var pad = function (n) { return n < 10 ? '0' + n : '' + n; };
                return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
                    + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
            }
        }
    }).use(ElementPlus).mount('#app');

})(Vue, axios, ElementPlus);
