/* =================================================================
   漫画管理（后台） — 前端逻辑
   ================================================================= */

;(function (Vue, axios, ElementPlus) {
    'use strict';

    var ElMessage = ElementPlus.ElMessage;
    var ElMessageBox = ElementPlus.ElMessageBox;

    function debounce(fn, ms) {
        var timer;
        return function () {
            var ctx = this, args = arguments;
            clearTimeout(timer);
            timer = setTimeout(function () { fn.apply(ctx, args); }, ms);
        };
    }

    function extractNames(items) {
        return (items || []).map(function (item) {
            return item.tag || item.name || item.category || '';
        }).filter(Boolean);
    }

    /* =============================================================
       API 封装
       ============================================================= */
    var BASE = '/page/cartoon/admin/api';

    function apiSearchTags(name) {
        return axios.post(BASE + '/searchTags', { name: name })
            .then(function (res) {
                var d = res.data || {};
                if (d.success === false) throw new Error(d.msg || '标签搜索失败');
                return extractNames(d.items);
            });
    }

    function apiSearchCategories(name) {
        return axios.post(BASE + '/searchCategories', { name: name })
            .then(function (res) {
                var d = res.data || {};
                if (d.success === false) throw new Error(d.msg || '类别搜索失败');
                return extractNames(d.items);
            });
    }

    function apiSearchComic(body) {
        return axios.post(BASE + '/list', body)
            .then(function (res) {
                var d = res.data || {};
                return { items: d.items || [], total: d.total || 0 };
            });
    }

    function apiChapterImages(comicId, chapterIndex) {
        return axios.post(BASE + '/chapterImages', { comicId: comicId, chapterIndex: chapterIndex })
            .then(function (res) { return res.data || {}; });
    }

    /* =============================================================
       Vue 实例
       ============================================================= */

    Vue.createApp({

        data: function () {
            return {
                activeTab: 'manage',

                /* 漫画管理 */
                searchForm: { title: '', type: '', tags: [], categories: [], sortField: '', sortOrder: 'desc' },
                sortOptions: [
                    { label: '默认（时间）', value: '' },
                    { label: '标题', value: 'title' },
                    { label: '副标题', value: 'subtitle' }
                ],
                typeOptions: [
                    { label: '漫画', value: '漫画' },
                    { label: 'Coser', value: 'coser' }
                ],
                tagLoading: false, tagOptions: [],
                catLoading: false, catOptions: [],
                comicList: [], total: 0, loading: false,
                currentPage: 1, pageInput: 1, pageSize: 12,
                filterDel: null,

                /* 章节弹窗 */
                chapterDlg: false, chapterComic: null, chapterList: [],
                chapterLoading: false, chapterImages: [], chapterName: '',
                imageViewerVisible: false, currentImageIndex: 0,

                /* 阅读器 */
                readerVisible: false, readerImages: [], readerTitle: '', readerCurrentPage: 1,

                /* 删除弹窗 */
                deleteDlg: false, deleteTarget: null, deleteReason: '', deleteLoading: false,

                /* SQLite 导入 */
                importForm: { file: null, type: '漫画', path: '' },
                importStatus: { running: false, status: '空闲', logs: [] },
                importTimer: null,

                /* 章节压缩 */
                zipStatus: { running: false, status: '空闲', logs: [] },
                zipTimer: null,

                /* 漫画去重 */
                dedupScanning: false,
                dedupExecuting: false,
                dedupResult: { duplicates: [], totalGroups: 0, totalToRemove: 0 },
                dedupLogs: []
            };
        },

        computed: {
            totalPages: function () {
                return Math.max(1, Math.ceil(this.total / this.pageSize));
            }
        },

        watch: {
            currentPage: function (v) { this.pageInput = v; }
        },

        mounted: function () {
            this.fetchComics();
            this.fetchImportStatus();
            this.fetchZipStatus();
            this.importTimer = setInterval(this.fetchImportStatus, 2000);
            this.zipTimer = setInterval(this.fetchZipStatus, 2000);
        },

        created: function () {
            this._debTagSearch = debounce(this._doTagSearch, 350);
            this._debCatSearch = debounce(this._doCatSearch, 350);
        },

        methods: {

            fetchComics: function () {
                var self = this;
                self.loading = true;
                var from = (self.currentPage - 1) * self.pageSize;

                var body = {
                    _from: from,
                    size: self.pageSize,
                    params: {
                        searchTitle:      self.searchForm.title || '',
                        searchType:       self.searchForm.type || '',
                        searchtags:       self.searchForm.tags || [],
                        searchCategories: self.searchForm.categories || [],
                        sortField:        self.searchForm.sortField || '',
                        sortOrder:        self.searchForm.sortOrder || 'desc',
                        filterDel:        self.filterDel
                    }
                };

                apiSearchComic(body)
                    .then(function (res) {
                        self.comicList = res.items;
                        self.total = res.total;
                    })
                    .catch(function (err) {
                        console.error('获取漫画列表失败:', err);
                        ElMessage.error('加载失败，请稍后重试');
                        self.comicList = [];
                        self.total = 0;
                    })
                    .finally(function () { self.loading = false; });
            },

            /* 标签远程搜索 */
            onTagSearch: function (query) { this._debTagSearch(query); },
            _doTagSearch: function (query) {
                var self = this;
                self.tagLoading = true;
                apiSearchTags(query || '')
                    .then(function (names) {
                        var merged = names.concat(self.searchForm.tags);
                        var unique = [];
                        merged.forEach(function (n) { if (unique.indexOf(n) === -1) unique.push(n); });
                        self.tagOptions = unique;
                    })
                    .catch(function () { self.tagOptions = self.searchForm.tags.slice(); })
                    .finally(function () { self.tagLoading = false; });
            },
            onTagDropVisible: function (v) { if (v && !this.tagOptions.length) this._doTagSearch(''); },

            /* 类别远程搜索 */
            onCatSearch: function (query) { this._debCatSearch(query); },
            _doCatSearch: function (query) {
                var self = this;
                self.catLoading = true;
                apiSearchCategories(query || '')
                    .then(function (names) {
                        var merged = names.concat(self.searchForm.categories);
                        var unique = [];
                        merged.forEach(function (n) { if (unique.indexOf(n) === -1) unique.push(n); });
                        self.catOptions = unique;
                    })
                    .catch(function () { self.catOptions = self.searchForm.categories.slice(); })
                    .finally(function () { self.catLoading = false; });
            },
            onCatDropVisible: function (v) { if (v && !this.catOptions.length) this._doCatSearch(''); },

            /* 搜索 / 重置 */
            doSearch: function () { this.currentPage = 1; this.fetchComics(); },
            doReset: function () {
                this.searchForm = { title: '', type: '', tags: [], categories: [], sortField: '', sortOrder: 'desc' };
                this.tagOptions = [];
                this.catOptions = [];
                this.filterDel = null;
                this.currentPage = 1;
                this.fetchComics();
            },

            /* 分页 */
            goPage: function (p) {
                if (p < 1 || p > this.totalPages || p === this.currentPage) return;
                this.currentPage = p;
                this.fetchComics();
                window.scrollTo({ top: 0, behavior: 'smooth' });
            },
            onPageJump: function (val) { if (val != null) this.goPage(val); },

            /* 封面 URL */
            getCoverUrl: function (comic) {
                if (comic.cover_key) return BASE + '/cover?key=' + comic.cover_key;
                return '';
            },

            /* 标签颜色 */
            tagColor: function (tag) {
                var types = ['', 'success', 'warning', 'danger', 'info'];
                var h = 0;
                for (var i = 0; i < tag.length; i++) { h = ((h << 5) - h) + tag.charCodeAt(i); h |= 0; }
                return types[Math.abs(h) % types.length];
            },

            /* 时间格式化 */
            formatTime: function (t) {
                if (!t) return '';
                var d = new Date(t);
                if (isNaN(d.getTime())) return String(t);
                var pad = function (n) { return n < 10 ? '0' + n : '' + n; };
                return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
                    + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
            },

            /* ======================== 删除 / 恢复 ======================== */

            openDeleteDlg: function (comic) {
                this.deleteTarget = comic;
                this.deleteReason = '';
                this.deleteDlg = true;
            },

            doDelete: function () {
                var self = this;
                if (!self.deleteTarget) return;
                self.deleteLoading = true;

                axios.post(BASE + '/softDelete', {
                    id: self.deleteTarget.id,
                    reason: self.deleteReason
                }).then(function (res) {
                    if (res.data.success) {
                        ElMessage.success('已删除');
                        self.deleteDlg = false;
                        self.fetchComics();
                    } else {
                        ElMessage.error(res.data.error || '删除失败');
                    }
                }).catch(function () {
                    ElMessage.error('请求失败');
                }).finally(function () { self.deleteLoading = false; });
            },

            doRestore: function (comic) {
                var self = this;
                ElMessageBox.confirm('确定要恢复「' + comic.title + '」吗？', '恢复漫画', { type: 'success' })
                    .then(function () {
                        axios.post(BASE + '/restore', { id: comic.id })
                            .then(function (res) {
                                if (res.data.success) {
                                    ElMessage.success('已恢复');
                                    self.fetchComics();
                                } else {
                                    ElMessage.error(res.data.error || '恢复失败');
                                }
                            });
                    }).catch(function () {});
            },

            /* ======================== 章节 / 阅读器 ======================== */

            openChapters: function (comic) {
                this.chapterComic = comic;
                this.chapterList = (comic.chapters || []).slice().sort(function (a, b) {
                    return (a.index || 0) - (b.index || 0);
                });
                this.chapterImages = [];
                this.chapterName = '';
                this.chapterDlg = true;
            },

            loadChapter: function (chapterIndex, chapterName) {
                var self = this;
                self.chapterLoading = true;
                self.chapterName = chapterName;
                self.chapterImages = [];
                apiChapterImages(self.chapterComic.id, chapterIndex)
                    .then(function (data) {
                        var keys = data.imageKeys || [];
                        if (!keys.length) {
                            ElMessage.warning('该章节暂无图片');
                            return;
                        }
                        var urls = keys.map(function (k) {
                            return BASE + '/pageImage?key=' + k;
                        });
                        self.chapterImages = urls;
                        self.openReader(urls, chapterName);
                    })
                    .catch(function () { ElMessage.error('加载章节失败'); })
                    .finally(function () { self.chapterLoading = false; });
            },

            openReader: function (urls, chapterName) {
                this.readerImages = urls;
                this.readerTitle = (this.chapterComic ? this.chapterComic.title : '') + ' — ' + (chapterName || '');
                this.readerCurrentPage = 1;
                this.readerVisible = true;
                document.body.style.overflow = 'hidden';
                var self = this;
                this._onReaderKeydown = function (e) {
                    if (e.key === 'Escape') self.closeReader();
                };
                document.addEventListener('keydown', this._onReaderKeydown);
            },
            closeReader: function () {
                this.readerVisible = false;
                document.body.style.overflow = '';
                if (this._onReaderKeydown) {
                    document.removeEventListener('keydown', this._onReaderKeydown);
                    this._onReaderKeydown = null;
                }
            },
            onReaderScroll: function (e) {
                var container = e.target;
                var imgs = container.querySelectorAll('.reader-img');
                var scrollTop = container.scrollTop;
                var viewH = container.clientHeight;
                var current = 1;
                for (var i = 0; i < imgs.length; i++) {
                    var imgTop = imgs[i].offsetTop - container.offsetTop;
                    if (imgTop <= scrollTop + viewH * 0.4) {
                        current = i + 1;
                    }
                }
                this.readerCurrentPage = current;
            },

            openViewer: function (idx) {
                this.currentImageIndex = idx;
                this.imageViewerVisible = true;
            },
            prevImage: function () {
                if (this.currentImageIndex > 0) this.currentImageIndex--;
            },
            nextImage: function () {
                if (this.currentImageIndex < this.chapterImages.length - 1) this.currentImageIndex++;
            },

            /* ======================== SQLite 导入 ======================== */

            onFileChange: function (file) {
                this.importForm.file = file.raw;
            },
            onExceedImport: function () {
                ElMessage.warning('只能上传一个文件');
            },
            fetchImportStatus: function () {
                var self = this;
                axios.get('/page/comic/import/api/status').then(function (res) {
                    self.importStatus = res.data;
                    self.$nextTick(function () {
                        var box = self.$refs.importLogBox;
                        if (box) box.scrollTop = box.scrollHeight;
                    });
                });
            },
            startImport: function () {
                var self = this;
                if (!self.importForm.file) { ElMessage.error('请选择文件'); return; }
                if (!self.importForm.type) { ElMessage.error('请选择类型'); return; }
                if (!self.importForm.path.trim()) { ElMessage.error('请输入路径'); return; }

                ElMessageBox.confirm(
                    '确定要导入吗？将读取 SQLite 数据并写入 manga_source，随后自动压缩章节。',
                    '确认导入', { type: 'warning' }
                ).then(function () {
                    var fd = new FormData();
                    fd.append('file', self.importForm.file);
                    fd.append('type', self.importForm.type);
                    fd.append('path', self.importForm.path.trim());
                    axios.post('/page/comic/import/api/upload', fd, {
                        headers: { 'Content-Type': 'multipart/form-data' }
                    }).then(function (res) {
                        if (res.data.success) { ElMessage.success('任务已启动'); }
                        else { ElMessage.error(res.data.error || '启动失败'); }
                    }).catch(function () { ElMessage.error('请求失败'); });
                }).catch(function () {});
            },
            stopImport: function () {
                axios.post('/page/comic/import/api/stop');
            },

            /* ======================== 章节压缩 ======================== */

            fetchZipStatus: function () {
                var self = this;
                axios.get('/tool/manga-zip/status').then(function (res) {
                    self.zipStatus = res.data;
                    self.$nextTick(function () {
                        var box = self.$refs.zipLogBox;
                        if (box) box.scrollTop = box.scrollHeight;
                    });
                });
            },
            startZip: function (overwrite) {
                var self = this;
                var msg = overwrite ? '确定要强制覆盖已有 zip 文件吗？' : '确定要开始压缩吗？';
                ElMessageBox.confirm(msg, '确认').then(function () {
                    axios.post('/tool/manga-zip/start', { overwrite: overwrite });
                }).catch(function () {});
            },
            stopZip: function () {
                axios.post('/tool/manga-zip/stop');
            },

            /* ======================== 漫画去重 ======================== */

            scanDedup: function () {
                var self = this;
                self.dedupScanning = true;
                self.dedupResult = { duplicates: [], totalGroups: 0, totalToRemove: 0 };
                self.dedupLogs = [];

                axios.get(BASE + '/dedup/scan').then(function (res) {
                    if (res.data.success) {
                        self.dedupResult = res.data;
                        if (res.data.duplicates.length === 0) {
                            ElMessage.info('没有发现重复数据');
                        } else {
                            ElMessage.warning('发现 ' + res.data.totalGroups + ' 组重复，共 ' + res.data.totalToRemove + ' 条待删除');
                        }
                    } else {
                        ElMessage.error(res.data.error || '扫描失败');
                    }
                }).catch(function () {
                    ElMessage.error('扫描请求失败');
                }).finally(function () {
                    self.dedupScanning = false;
                });
            },

            executeDedup: function () {
                var self = this;
                ElMessageBox.confirm(
                    '确定要执行去重吗？将保留每个 picg_id 中时间最晚的一条，删除其余条目及对应文件夹。此操作不可撤销！',
                    '确认去重',
                    { type: 'error', confirmButtonText: '确认执行', cancelButtonText: '取消' }
                ).then(function () {
                    self.dedupExecuting = true;
                    self.dedupLogs = ['开始执行去重...'];

                    axios.post(BASE + '/dedup/execute').then(function (res) {
                        if (res.data.success) {
                            self.dedupLogs = self.dedupLogs.concat(res.data.logs || []);
                            self.dedupLogs.push('去重完成：共删除 ' + res.data.removed + ' 条记录');
                            ElMessage.success('去重完成，删除 ' + res.data.removed + ' 条');
                            // 自动重新扫描
                            self.scanDedup();
                        } else {
                            self.dedupLogs.push('去重失败: ' + (res.data.error || '未知错误'));
                            ElMessage.error(res.data.error || '去重失败');
                        }
                    }).catch(function () {
                        self.dedupLogs.push('请求失败');
                        ElMessage.error('请求失败');
                    }).finally(function () {
                        self.dedupExecuting = false;
                    });
                }).catch(function () {});
            }
        }
    }).use(ElementPlus).mount('#app');

})(Vue, axios, ElementPlus);
