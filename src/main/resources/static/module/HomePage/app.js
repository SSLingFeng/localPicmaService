/* =============================================
   MAIN VUE APP
   ============================================= */

new Vue({
  el: '#app',

  data: function () {
    return {
      /* Author */
      author: {
        name: 'Nocan',
        tagline: '写代码、拍照、玩游戏、偶尔烤面包的工程师',
        bio: '后端开发 / 业余摄影爱好者 / Steam 收藏家 / 咖啡与面包的信徒。记录技术思考、生活碎片和一切值得记住的瞬间。'
      },

      /* Loading flags */
      loading: {
        gaming: true,
        photo:  true,
        life:   true,
        work:   true
      },

      /* Sections data */
      games:        [],
      featuredPhotos: [],
      recentPhotos:   [],
      lifePosts:    [],
      projects:     [],

      /* UI state */
      activeSection: 'home',
      isScrolled:    false,
      activeProject: '',
      year: new Date().getFullYear()
    };
  },

  mounted: function () {
    this.fetchAll();
    this.initScrollSpy();
    this.initSectionReveal();
  },

  methods: {
    /* -------- Data Fetching -------- */
    fetchAll: function () {
      var self = this;

      BlogAPI.fetchGames().then(function (data) {
        self.games = data;
        self.loading.gaming = false;
      });

      BlogAPI.fetchPhotos().then(function (data) {
        if (data) {
          self.featuredPhotos = data.featured || [];
          self.recentPhotos   = data.recent   || [];
        }
        self.loading.photo = false;
      });

      BlogAPI.fetchLife().then(function (data) {
        self.lifePosts = data;
        self.loading.life = false;
      });

      BlogAPI.fetchWork().then(function (data) {
        self.projects = data;
        self.loading.work = false;
      });
    },

    /* -------- Navigation -------- */
    handleNav: function (index) {
      this.scrollTo(index);
    },
    scrollTo: function (id) {
      var el = document.getElementById(id);
      if (el) {
        var top = el.getBoundingClientRect().top + window.pageYOffset - 70;
        window.scrollTo({ top: top, behavior: 'smooth' });
      }
    },

    /* -------- Scroll Spy -------- */
    initScrollSpy: function () {
      var self = this;
      var sections = ['home', 'gaming', 'photo', 'life', 'work'];

      window.addEventListener('scroll', function () {
        /* Header scroll state */
        self.isScrolled = window.scrollY > 30;

        /* Active section detection */
        var scrollPos = window.scrollY + 120;
        for (var i = sections.length - 1; i >= 0; i--) {
          var el = document.getElementById(sections[i]);
          if (el && el.offsetTop <= scrollPos) {
            self.activeSection = sections[i];
            break;
          }
        }
      });
    },

    /* -------- Intersection Observer for section reveal -------- */
    initSectionReveal: function () {
      var self = this;
      this.$nextTick(function () {
        var observer = new IntersectionObserver(
          function (entries) {
            entries.forEach(function (entry) {
              if (entry.isIntersecting) {
                entry.target.classList.add('visible');
              }
            });
          },
          { threshold: 0.08 }
        );

        document.querySelectorAll('.section-block').forEach(function (el) {
          observer.observe(el);
        });

        /* Timeline items staggered reveal */
        setTimeout(function () {
          document.querySelectorAll('.el-timeline-item').forEach(function (el, idx) {
            setTimeout(function () {
              observer.observe(el);
            }, idx * 80);
          });
        }, 500);
      });
    }
  }
});
