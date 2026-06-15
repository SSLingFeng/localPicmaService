/* =================================================================
   漫画管理 — 前端逻辑
   =================================================================
   API 端点 (全部 POST):
   1. 标签搜索   /web/comicManagement/searchTags
      请求: { name:string }
      响应: { success:bool, msg:string, items:[{tag:string}], total:int }

   2. 类别搜索   /web/comicManagement/searchCategories
      请求: { name:string }
      响应: 同上

   3. 漫画列表   /web/comicManagement/searchComic
      请求: {
        _from:int,        // SQL LIMIT 偏移量 (0-based)
        _to:int,          // _from + size
        size:int,         // 每页条数
        params:{
          searchTitle:string,
          searchType:string,       // "漫画" | "coser"
          searchtags:string[],     // 标签数组
          searchCategories:string[]// 类别数组
        }
      }
      响应: {
        items:[{
          id:string, title:string, type:string,
          tags:string[], description:string,
          pages_count:int, author:string,
          chinese_team:string, creator:string,
          time:string, categories:string[]
          // cover:string  —— 若后端返回封面URL，会自动使用
        }],
        total:int
      }
   ================================================================= */

;(function (Vue, axios) {
    'use strict';

    /* =============================================================
       工具
       ============================================================= */

    /** 防抖 */
    function debounce(fn, ms) {
        var timer;
        return function () {
            var ctx = this, args = arguments;
            clearTimeout(timer);
            timer = setTimeout(function () { fn.apply(ctx, args); }, ms);
        };
    }

    /** 从 items 数组提取标签名（兼容 tag / name / category 字段） */
    function extractNames(items) {
        return (items || []).map(function (item) {
            return item.tag || item.name || item.category || '';
        }).filter(Boolean);
    }

    /* =============================================================
       API 封装
       ============================================================= */

    var BASE = ''; // 同源部署留空；跨域时改为 "http://ip:port"

    /** 搜索标签 */
    function apiSearchTags(name) {
        return axios.post(BASE + '/web/comicManagement/searchTags', { name: name })
            .then(function (res) {
                var d = res.data || {};
                if (d.success === false) throw new Error(d.msg || '标签搜索失败');
                return extractNames(d.items);
            });
    }

    /** 搜索类别 */
    function apiSearchCategories(name) {
        return axios.post(BASE + '/web/comicManagement/searchCategories', { name: name })
            .then(function (res) {
                var d = res.data || {};
                if (d.success === false) throw new Error(d.msg || '类别搜索失败');
                return extractNames(d.items);
            });
    }

    /** 获取漫画列表 */
    function apiSearchComic(body) {
        return axios.post(BASE + '/web/comicManagement/searchComic', body)
            .then(function (res) {
                var d = res.data || {};
                return { items: d.items || [], total: d.total || 0 };
            });
    }

    /* =============================================================
       Vue 实例
       ============================================================= */

    new Vue({
        el: '#app',

        data: function () {
            return {
                /* —— 搜索 —— */
                searchForm: {
                    title: '',
                    type: '',
                    tags: [],
                    categories: []
                },
                typeOptions: [
                    { label: '漫画', value: '漫画' },
                    { label: 'Coser', value: 'coser' }
                ],

                /* —— 标签远程搜索 —— */
                tagLoading: false,
                tagOptions: [],

                /* —— 类别远程搜索 —— */
                catLoading: false,
                catOptions: [],

                /* —— 列表 —— */
                comicList: [],
                total: 0,
                loading: false,

                /* —— 分页 —— */
                currentPage: 1,
                pageInput: 1,
                pageSize: 12
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

        /* ---- 在 created 中创建防抖版本 ---- */
        created: function () {
            this._debTagSearch = debounce(this._doTagSearch, 350);
            this._debCatSearch = debounce(this._doCatSearch, 350);
        },

        methods: {

            /* =========================================================
               获取漫画列表
               ========================================================= */
            fetchComics: function () {
                var self = this;
                self.loading = true;

                var from = (self.currentPage - 1) * self.pageSize;

                var body = {
                    _from: from,
                    _to:   from + self.pageSize,
                    size:  self.pageSize,
                    params: {
                        searchTitle:     self.searchForm.title || '',
                        searchType:      self.searchForm.type  || '',
                        searchtags:      self.searchForm.tags       || [],
                        searchCategories: self.searchForm.categories || []
                    }
                };

                apiSearchComic(body)
                    .then(function (res) {
                        self.comicList = res.items;
                        self.total     = res.total;
                    })
                    .catch(function (err) {
                        console.error('获取漫画列表失败:', err);
                        self.$message.error('加载失败，请稍后重试');
                        self.comicList = [];
                        self.total     = 0;
                    })
                    .finally(function () {
                        self.loading = false;
                    });
            },

            /* =========================================================
               标签远程搜索
               ========================================================= */
            onTagSearch: function (query) {
                this._debTagSearch(query);
            },
            _doTagSearch: function (query) {
                var self = this;
                self.tagLoading = true;

                apiSearchTags(query || '')
                    .then(function (names) {
                        // 保留已选中项，合并去重
                        var merged = names.concat(self.searchForm.tags);
                        var unique = [];
                        merged.forEach(function (n) { if (unique.indexOf(n) === -1) unique.push(n); });
                        self.tagOptions = unique;
                    })
                    .catch(function (err) {
                        console.error('标签搜索失败:', err);
                        self.tagOptions = self.searchForm.tags.slice();
                    })
                    .finally(function () {
                        self.tagLoading = false;
                    });
            },
            onTagDropVisible: function (visible) {
                if (visible && this.tagOptions.length === 0) {
                    this._doTagSearch('');
                }
            },

            /* =========================================================
               类别远程搜索
               ========================================================= */
            onCatSearch: function (query) {
                this._debCatSearch(query);
            },
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
                    .catch(function (err) {
                        console.error('类别搜索失败:', err);
                        self.catOptions = self.searchForm.categories.slice();
                    })
                    .finally(function () {
                        self.catLoading = false;
                    });
            },
            onCatDropVisible: function (visible) {
                if (visible && this.catOptions.length === 0) {
                    this._doCatSearch('');
                }
            },

            /* =========================================================
               搜索 / 重置
               ========================================================= */
            doSearch: function () {
                this.currentPage = 1;
                this.fetchComics();
            },
            doReset: function () {
                this.searchForm.title      = '';
                this.searchForm.type       = '';
                this.searchForm.tags       = [];
                this.searchForm.categories = [];
                this.tagOptions = [];
                this.catOptions = [];
                this.currentPage = 1;
                this.fetchComics();
            },

            /* =========================================================
               分页
               ========================================================= */
            goPage: function (p) {
                if (p < 1 || p > this.totalPages || p === this.currentPage) return;
                this.currentPage = p;
                this.fetchComics();
                window.scrollTo({ top: 0, behavior: 'smooth' });
            },
            onPageJump: function (val) {
                if (val == null || val === this.currentPage) return;
                this.goPage(val);
            },

            /* =========================================================
               封面 URL（按实际接口修改此方法）
               ========================================================= */
            getCoverUrl: function (comic) {
                // 优先使用 API 返回的封面字段
                // 若后端无 cover 字段，可改为 return '/covers/' + comic.id + '.jpg';
                return comic.cover || comic.cover_url || comic.thumb || comic.image || '';
            },

            /* =========================================================
               标签颜色哈希
               ========================================================= */
            tagColor: function (tag) {
                var types = ['', 'success', 'warning', 'danger', 'info'];
                var h = 0;
                for (var i = 0; i < tag.length; i++) {
                    h = ((h << 5) - h) + tag.charCodeAt(i);
                    h |= 0;
                }
                return types[Math.abs(h) % types.length];
            }
        }
    });

})(Vue, axios);
