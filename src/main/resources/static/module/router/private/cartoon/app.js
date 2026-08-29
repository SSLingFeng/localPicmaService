/* =================================================================
   漫画模块 — 推荐 / 搜索 / 我的
   ================================================================= */

;(function (Vue, axios, ElementPlus) {
    'use strict';

    var ElMessage = ElementPlus.ElMessage;

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
       API
       ============================================================= */
    var BASE = '/page/cartoon/api';

    function apiRecommend() {
        return axios.get(BASE + '/recommend').then(function (res) {
            var d = res.data || {};
            return d.items || [];
        });
    }

    function apiFavorites() {
        return axios.get(BASE + '/favorites').then(function (res) {
            var d = res.data || {};
            return d.items || [];
        });
    }

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

    function apiToggleFavorite(picgId) {
        return axios.post(BASE + '/toggleFavorite', { picgId: picgId })
            .then(function (res) { return res.data || {}; });
    }

    function apiToggleDislike(picgId) {
        return axios.post(BASE + '/toggleDislike', { picgId: picgId })
            .then(function (res) { return res.data || {}; });
    }

    function apiChapterImages(comicId, chapterIndex) {
        return axios.post(BASE + '/chapterImages', { comicId: comicId, chapterIndex: chapterIndex })
            .then(function (res) { return res.data || {}; });
    }

    function apiDetail(id) {
        return axios.get(BASE + '/detail', { params: { id: id } })
            .then(function (res) {
                var d = res.data || {};
                if (d.success === false) throw new Error(d.error || '获取详情失败');
                return d.data;
            });
    }

    /* =============================================================
       Vue 实例
       ============================================================= */

    Vue.createApp({

        data: function () {
            return {
                isMobile: window.innerWidth <= 700,
                activeTab: 'recommend',

                /* 推荐 */
                recommendList: [],
                recommendLoading: false,

                /* 搜索 */
                searchForm: { title: '', type: '', tags: [], categories: [], sortField: '', sortOrder: 'desc', prefFilter: '' },
                sortOptions: [
                    { label: '默认（时间）', value: '' },
                    { label: '标题', value: 'title' },
                    { label: '副标题', value: 'subtitle' }
                ],
                prefOptions: [
                    { label: '默认', value: '' },
                    { label: '收藏', value: 'favorite' },
                    { label: '厌恶', value: 'dislike' }
                ],
                typeOptions: [
                    { label: '漫画', value: '漫画' },
                    { label: 'Coser', value: 'coser' }
                ],
                tagLoading: false, tagOptions: [],
                catLoading: false, catOptions: [],
                comicList: [], total: 0, loading: false,
                currentPage: 1, pageInput: 1, pageSize: 12,

                /* 我的 */
                favoritesList: [],
                favoritesLoading: false,

                /* 章节弹窗 */
                chapterDlg: false,
                chapterComic: null,
                chapterList: [],
                chapterLoading: false,
                chapterImages: [],
                chapterName: '',
                imageViewerVisible: false,
                currentImageIndex: 0,

                /* 阅读器 */
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
            currentPage: function (v) { this.pageInput = v; },
            chapterDlg: function (val) {
                this.setOverlayElements(val);
            },
            readerVisible: function (val) {
                this.setOverlayElements(val);
            }
        },

        mounted: function () {
            this.loadRecommend();
            var self = this;
            this._onResize = function () { self.isMobile = window.innerWidth <= 700; };
            window.addEventListener('resize', this._onResize);
        },

        created: function () {
            this._debTagSearch = debounce(this._doTagSearch, 350);
            this._debCatSearch = debounce(this._doCatSearch, 350);
        },

        methods: {

            /** 弹窗/阅读器打开时隐藏浮动元素 */
            setOverlayElements: function (hidden) {
                var badge = document.getElementById('lmUserBadgeWrap');
                var loginBtn = document.getElementById('lmLoginBtn');
                var navFab = document.getElementById('lmNavFab');
                if (badge) badge.style.display = hidden ? 'none' : '';
                if (loginBtn) loginBtn.style.display = hidden ? 'none' : '';
                if (navFab) navFab.style.display = hidden ? 'none' : '';
            },

            /* ======================== Tab 切换 ======================== */

            switchTab: function (tab) {
                this.activeTab = tab;
                if (tab === 'recommend' && !this.recommendList.length) this.loadRecommend();
                if (tab === 'search' && !this.comicList.length) this.fetchComics();
                if (tab === 'profile' && !this.favoritesList.length) this.loadFavorites();
            },

            /* ======================== 推荐 ======================== */

            loadRecommend: function () {
                var self = this;
                self.recommendLoading = true;
                apiRecommend()
                    .then(function (data) { self.recommendList = data; })
                    .catch(function () { self.recommendList = []; })
                    .finally(function () { self.recommendLoading = false; });
            },

            /* ======================== 搜索 ======================== */

            fetchComics: function () {
                var self = this;
                self.loading = true;
                var from = (self.currentPage - 1) * self.pageSize;
                var body = {
                    _from: from, size: self.pageSize,
                    params: {
                        searchTitle: self.searchForm.title || '',
                        searchType: self.searchForm.type || '',
                        searchtags: self.searchForm.tags || [],
                        searchCategories: self.searchForm.categories || [],
                        sortField: self.searchForm.sortField || '',
                        sortOrder: self.searchForm.sortOrder || 'desc',
                        prefFilter: self.searchForm.prefFilter || ''
                    }
                };
                apiSearchComic(body)
                    .then(function (res) { self.comicList = res.items; self.total = res.total; })
                    .catch(function () { self.comicList = []; self.total = 0; })
                    .finally(function () { self.loading = false; });
            },

            onTagSearch: function (q) { this._debTagSearch(q); },
            _doTagSearch: function (q) {
                var self = this;
                self.tagLoading = true;
                apiSearchTags(q || '')
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

            onCatSearch: function (q) { this._debCatSearch(q); },
            _doCatSearch: function (q) {
                var self = this;
                self.catLoading = true;
                apiSearchCategories(q || '')
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

            doSearch: function () { this.currentPage = 1; this.fetchComics(); },
            doReset: function () {
                this.searchForm = { title: '', type: '', tags: [], categories: [], sortField: '', sortOrder: 'desc', prefFilter: '' };
                this.tagOptions = []; this.catOptions = [];
                this.currentPage = 1; this.fetchComics();
            },

            goPage: function (p) {
                if (p < 1 || p > this.totalPages || p === this.currentPage) return;
                this.currentPage = p; this.fetchComics();
                window.scrollTo({ top: 0, behavior: 'smooth' });
            },
            onPageJump: function (val) { if (val != null) this.goPage(val); },

            /* ======================== 我的 ======================== */

            loadFavorites: function () {
                var self = this;
                self.favoritesLoading = true;
                apiFavorites()
                    .then(function (data) { self.favoritesList = data; })
                    .catch(function () { self.favoritesList = []; })
                    .finally(function () { self.favoritesLoading = false; });
            },

            /* ======================== 收藏/厌恶 ======================== */

            toggleFavorite: function (comic) {
                apiToggleFavorite(comic.picg_id).then(function (res) {
                    if (res.success) {
                        if (res.action === 'favorite') { comic.favorited = true; comic.disliked = false; ElMessage.success('已收藏'); }
                        else if (res.action === 'unfavorite') { comic.favorited = false; ElMessage.info('已取消收藏'); }
                    } else { ElMessage.error(res.error || '操作失败'); }
                }).catch(function () { ElMessage.error('请求失败'); });
            },

            toggleDislike: function (comic) {
                var self = this;
                apiToggleDislike(comic.picg_id).then(function (res) {
                    if (res.success) {
                        if (res.action === 'dislike') {
                            comic.disliked = true; comic.favorited = false;
                            ElMessage.success('已标记厌恶');
                            if (!self.searchForm.prefFilter) {
                                self.comicList = self.comicList.filter(function (c) { return c.id !== comic.id; });
                            }
                        } else if (res.action === 'undislike') { comic.disliked = false; ElMessage.info('已取消厌恶'); }
                    } else { ElMessage.error(res.error || '操作失败'); }
                }).catch(function () { ElMessage.error('请求失败'); });
            },

            /* ======================== 封面/标签/时间 ======================== */

            getCoverUrl: function (comic) {
                if (comic.cover_key) return BASE + '/cover?key=' + comic.cover_key;
                return '';
            },

            tagColor: function (tag) {
                var types = ['', 'success', 'warning', 'danger', 'info'];
                var h = 0;
                for (var i = 0; i < tag.length; i++) { h = ((h << 5) - h) + tag.charCodeAt(i); h |= 0; }
                return types[Math.abs(h) % types.length];
            },

            formatTime: function (t) {
                if (!t) return '';
                var d = new Date(t);
                if (isNaN(d.getTime())) return String(t);
                var pad = function (n) { return n < 10 ? '0' + n : '' + n; };
                return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
                    + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
            },

            /* ======================== 章节/阅读器 ======================== */

            openChapters: function (comic) {
                var self = this;
                self.chapterComic = comic; // 先用卡片数据展示基本信息
                self.chapterList = [];
                self.chapterImages = []; self.chapterName = '';
                self.chapterDlg = true;

                // 请求详情接口获取完整信息（含章节）
                apiDetail(comic.id).then(function (detail) {
                    self.chapterComic = detail;
                    self.chapterList = (detail.chapters || []).slice().sort(function (a, b) {
                        return (a.index || 0) - (b.index || 0);
                    });
                }).catch(function (err) {
                    ElMessage.error(err.message || '获取详情失败');
                });
            },

            loadChapter: function (chapterIndex, chapterName) {
                var self = this;
                self.chapterLoading = true; self.chapterName = chapterName; self.chapterImages = [];
                apiChapterImages(self.chapterComic.id, chapterIndex)
                    .then(function (data) {
                        var keys = data.imageKeys || [];
                        if (!keys.length) { ElMessage.warning('该章节暂无图片'); return; }
                        var urls = keys.map(function (k) { return BASE + '/pageImage?key=' + k; });
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
                this._onReaderKeydown = function (e) { if (e.key === 'Escape') self.closeReader(); };
                document.addEventListener('keydown', this._onReaderKeydown);
            },
            closeReader: function () {
                this.readerVisible = false;
                document.body.style.overflow = '';
                if (this._onReaderKeydown) { document.removeEventListener('keydown', this._onReaderKeydown); this._onReaderKeydown = null; }
            },
            onReaderScroll: function (e) {
                var c = e.target, imgs = c.querySelectorAll('.reader-img');
                var st = c.scrollTop, vh = c.clientHeight, cur = 1;
                for (var i = 0; i < imgs.length; i++) {
                    if (imgs[i].offsetTop - c.offsetTop <= st + vh * 0.4) cur = i + 1;
                }
                this.readerCurrentPage = cur;
            },

            openViewer: function (idx) { this.currentImageIndex = idx; this.imageViewerVisible = true; },
            prevImage: function () { if (this.currentImageIndex > 0) this.currentImageIndex--; },
            nextImage: function () { if (this.currentImageIndex < this.chapterImages.length - 1) this.currentImageIndex++; }
        }
    }).use(ElementPlus).mount('#app');

})(Vue, axios, ElementPlus);
