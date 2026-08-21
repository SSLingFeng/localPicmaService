;(function (Vue, axios, ElementPlus) {
    'use strict';

    var ElMessage = ElementPlus.ElMessage;
    var ElMessageBox = ElementPlus.ElMessageBox;
    var BASE = '/home/admin/api';

    Vue.createApp({

        data: function () {
            return {
                configs: [],
                activeModule: 'games',
                modules: [
                    { key: 'games',          label: '游戏日志' },
                    { key: 'photos_featured', label: '精选照片' },
                    { key: 'photos_recent',  label: '近期照片' },
                    { key: 'life',           label: '生活碎片' },
                    { key: 'work',           label: '工作轨迹' }
                ],
                contentList: [],
                contentLoading: false,

                // 编辑弹窗
                editDlg: false,
                editMode: 'add',
                editId: null,
                editForm: { title: '', content: '', date_time: '', order_num: 0, sort_order: 0 },
                saving: false,

                // 多图管理
                editImages: [],   // [{file_id, order_num}]
                uploading: false,

                // 配置加载守卫
                configsReady: false
            };
        },

        mounted: function () {
            this.loadConfigs();
            this.loadContent();
        },

        watch: {
            activeModule: function () {
                this.loadContent();
            }
        },

        methods: {

            // ======================== 模块配置 ========================

            loadConfigs: function () {
                var self = this;
                self.configsReady = false;
                axios.get(BASE + '/configs').then(function (res) {
                    var d = res.data;
                    self.configs = d.success ? (d.items || []) : [];
                    self.$nextTick(function () {
                        self.configsReady = true;
                    });
                });
            },

            saveConfig: function (row) {
                if (!this.configsReady) return;
                axios.post(BASE + '/config/save', {
                    module_type: row.module_type,
                    max_display: row.max_display,
                    enabled: row.enabled
                }).then(function (res) {
                    if (res.data.success) {
                        ElMessage.success('已保存');
                    }
                });
            },

            moduleLabel: function (key) {
                var map = { games: '游戏日志', photos: '光影视界', life: '生活碎片', work: '工作轨迹' };
                return map[key] || key;
            },

            // ======================== 内容列表 ========================

            loadContent: function () {
                var self = this;
                self.contentLoading = true;
                axios.get(BASE + '/list', { params: { moduleType: self.activeModule } })
                    .then(function (res) {
                        var d = res.data;
                        self.contentList = d.success ? (d.items || []) : [];
                    })
                    .catch(function () { self.contentList = []; })
                    .finally(function () { self.contentLoading = false; });
            },

            parseImageData: function (data) {
                if (!data) return [];
                if (Array.isArray(data)) return data;
                try { return JSON.parse(data); } catch (e) { return []; }
            },

            // ======================== 新增 / 编辑 ========================

            openAdd: function () {
                this.editMode = 'add';
                this.editId = null;
                this.editForm = { title: '', content: '', date_time: '', order_num: 0, sort_order: 0 };
                this.editImages = [];
                this.editDlg = true;
                var self = this;
                this.$nextTick(function () {
                    if (self.$refs.imageUpload) self.$refs.imageUpload.clearFiles();
                });
            },

            openEdit: function (row) {
                this.editMode = 'edit';
                this.editId = row.id;
                this.editForm = {
                    title: row.title || '',
                    content: row.content || '',
                    date_time: row.date_time || '',
                    order_num: row.order_num || 0,
                    sort_order: row.sort_order || 0
                };
                // 解析图片数组
                this.editImages = this.parseImageData(row.data).map(function (item, idx) {
                    return { file_id: item.file_id, order_num: item.order_num != null ? item.order_num : idx };
                });
                this.editDlg = true;
                var self = this;
                this.$nextTick(function () {
                    if (self.$refs.imageUpload) self.$refs.imageUpload.clearFiles();
                });
            },

            doSave: function () {
                var self = this;
                if (!self.editForm.title.trim()) {
                    ElMessage.error('请输入标题');
                    return;
                }
                self.saving = true;

                // 按 order_num 排序图片
                self.editImages.sort(function (a, b) { return a.order_num - b.order_num; });

                var body = {
                    module_type: self.activeModule,
                    title: self.editForm.title.trim(),
                    content: self.editForm.content.trim(),
                    date_time: self.editForm.date_time || '',
                    order_num: self.editForm.order_num || 0,
                    sort_order: self.editForm.sort_order || 0,
                    images: self.editImages.map(function (img) {
                        return { file_id: img.file_id, order_num: img.order_num };
                    })
                };

                var url;
                if (self.editMode === 'add') {
                    url = BASE + '/add';
                } else {
                    url = BASE + '/update';
                    body.id = self.editId;
                }

                axios.post(url, body).then(function (res) {
                    if (res.data.success) {
                        ElMessage.success(self.editMode === 'add' ? '已新增' : '已保存');
                        self.editDlg = false;
                        self.loadContent();
                    } else {
                        ElMessage.error(res.data.error || '操作失败');
                    }
                }).catch(function () {
                    ElMessage.error('请求失败');
                }).finally(function () { self.saving = false; });
            },

            doDelete: function (row) {
                var self = this;
                ElMessageBox.confirm('确定删除此条内容？', '确认', { type: 'warning' }).then(function () {
                    axios.post(BASE + '/delete', { id: row.id }).then(function (res) {
                        if (res.data.success) {
                            ElMessage.success('已删除');
                            self.loadContent();
                        } else {
                            ElMessage.error(res.data.error || '删除失败');
                        }
                    });
                }).catch(function () {});
            },

            // ======================== 图片上传 ========================

            onImageSelected: function (file) {
                var self = this;
                if (!file.raw.type.startsWith('image/')) {
                    ElMessage.error('只能上传图片文件');
                    return;
                }
                self.uploading = true;

                var ext = '';
                var name = file.name || '';
                if (name.lastIndexOf('.') >= 0) ext = name.substring(name.lastIndexOf('.'));
                var targetPath = 'home/' + self.activeModule + '/' + Date.now() + ext;

                Upload.file(file.raw, targetPath, function () {}).then(function (res) {
                    if (res.id) {
                        var nextOrder = self.editImages.length;
                        self.editImages.push({ file_id: res.id, order_num: nextOrder });
                        ElMessage.success('图片已上传');
                    }
                }).catch(function (e) {
                    ElMessage.error(e.message || '上传失败');
                }).finally(function () {
                    self.uploading = false;
                    if (self.$refs.imageUpload) {
                        self.$refs.imageUpload.clearFiles();
                    }
                });
            },

            removeImage: function (index) {
                this.editImages.splice(index, 1);
                // 重新编号
                this.editImages.forEach(function (img, idx) { img.order_num = idx; });
            },

            sortImages: function () {
                this.editImages.sort(function (a, b) { return a.order_num - b.order_num; });
            },

            // ======================== 工具 ========================

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
