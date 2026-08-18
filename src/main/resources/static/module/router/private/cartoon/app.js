/* =================================================================
   漫画管理 — 前端逻辑（接入本地 API）
   ================================================================= */

;(function (Vue, axios) {
    'use strict';

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
    var BASE = '/page/cartoon/api';

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

    new Vue({
        el: '#app',

        data: function () {
            return {
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

                tagLoading: false,
                tagOptions: [],
                catLoading: false,
                catOptions: [],

                comicList: [],
                total: 0,
                loading: false,

                currentPage: 1,
                pageInput: 1,
                pageSize: 12,

                /* 章节弹窗 */
                chapterDlg: false,
                chapterComic: null,
                chapterList: [],
                chapterLoading: false,
                chapterImages: [],
                chapterName: '',
                imageViewerVisible: false,
                currentImageIndex: 0,

                /* 全屏阅读器 */
                readerVisible: false,
                readerImages: [],
                readerTitle: '',
                readerCurrentPage: 1
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
                        sortOrder:        self.searchForm.sortOrder || 'desc'
                    }
                };

                apiSearchComic(body)
                    .then(function (res) {
                        self.comicList = res.items;
                        self.total = res.total;
                    })
                    .catch(function (err) {
                        console.error('获取漫画列表失败:', err);
                        self.$message.error('加载失败，请稍后重试');
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

            /* 打开章节弹窗 */
            openChapters: function (comic) {
                this.chapterComic = comic;
                this.chapterList = (comic.chapters || []).slice().sort(function (a, b) {
                    return (a.index || 0) - (b.index || 0);
                });
                this.chapterImages = [];
                this.chapterName = '';
                this.chapterDlg = true;
            },

            /* 加载章节图片 */
            loadChapter: function (chapterIndex, chapterName) {
                var self = this;
                self.chapterLoading = true;
                self.chapterName = chapterName;
                self.chapterImages = [];
                apiChapterImages(self.chapterComic.id, chapterIndex)
                    .then(function (data) {
                        var keys = data.imageKeys || [];
                        if (!keys.length) {
                            self.$message.warning('该章节暂无图片');
                            return;
                        }
                        // 根据 Valkey key 生成图片 URL
                        var urls = keys.map(function (k) {
                            return BASE + '/pageImage?key=' + k;
                        });
                        self.chapterImages = urls;
                        // 全屏阅读
                        self.openReader(urls, chapterName);
                    })
                    .catch(function () { self.$message.error('加载章节失败'); })
                    .finally(function () { self.chapterLoading = false; });
            },

            /* 全屏阅读器 — 打开 */
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
            /* 全屏阅读器 — 关闭 */
            closeReader: function () {
                this.readerVisible = false;
                document.body.style.overflow = '';
                if (this._onReaderKeydown) {
                    document.removeEventListener('keydown', this._onReaderKeydown);
                    this._onReaderKeydown = null;
                }
            },
            /* 全屏阅读器 — 滚动追踪当前页 */
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

            /* 图片查看器 */
            openViewer: function (idx) {
                this.currentImageIndex = idx;
                this.imageViewerVisible = true;
            },
            prevImage: function () {
                if (this.currentImageIndex > 0) this.currentImageIndex--;
            },
            nextImage: function () {
                if (this.currentImageIndex < this.chapterImages.length - 1) this.currentImageIndex++;
            }
        }
    });

})(Vue, axios);
