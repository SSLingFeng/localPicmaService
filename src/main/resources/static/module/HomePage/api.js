/* =============================================
   API LAYER
   =============================================
   切换真实后端时：把 BASE_URL 改为你的 Spring Boot 接口地址，
   删除各 fetch 方法里的 getMock* 降级逻辑即可。
   ============================================= */

var BlogAPI = (function () {

  var BASE_URL = '/api'; // Spring Boot 后端地址

  /* ---------- 通用请求 ---------- */
  function get(endpoint) {
    return axios.get(BASE_URL + endpoint)
      .then(function (res) { return res.data; })
      .catch(function (err) {
        console.warn('[BlogAPI] 请求失败 ' + endpoint, err);
        return null;
      });
  }

  /* ---------- 游戏 ---------- */
  function fetchGames() {
    return get('/games').then(function (data) {
      return data || getMockGames();
    });
  }
  function getMockGames() {
    return [
      {
        id: 1,
        title: '艾尔登法环',
        cover: 'https://picsum.photos/seed/eldenring/480/280',
        platforms: ['PS5', 'PC'],
        rating: 4.5,
        playtime: '128h',
        description: '交界地的探索令人沉醉，每一处废墟都藏着惊喜。法术与刀剑的交响，是今年最难忘的冒险。',
        tags: ['开放世界', '动作RPG', '魂系']
      },
      {
        id: 2,
        title: '塞尔达传说：王国之泪',
        cover: 'https://picsum.photos/seed/zeldatotk/480/280',
        platforms: ['Switch'],
        rating: 5,
        playtime: '95h',
        description: '究极手和余料建造让创造力没有天花板。海拉鲁大陆的天空与地底，任天堂再次定义了"好玩"。',
        tags: ['冒险', '开放世界', '解谜']
      },
      {
        id: 3,
        title: '博德之门3',
        cover: 'https://picsum.photos/seed/baldursgate3/480/280',
        platforms: ['PC'],
        rating: 4.5,
        playtime: '72h',
        description: 'CRPG 的文艺复兴，每一个选择都影响深远。队友们的故事比主线更让人欲罢不能。',
        tags: ['CRPG', '回合制', '剧情']
      }
    ];
  }

  /* ---------- 摄影 ---------- */
  function fetchPhotos() {
    return get('/photos').then(function (data) {
      return data || getMockPhotos();
    });
  }
  function getMockPhotos() {
    return {
      featured: [
        { id: 1, url: 'https://picsum.photos/seed/mountain1/960/540', title: '贡嘎银河',       location: '四川·贡嘎' },
        { id: 2, url: 'https://picsum.photos/seed/citynight/960/540',  title: '陆家嘴星轨',     location: '上海·浦东' },
        { id: 3, url: 'https://picsum.photos/seed/autumnlake/960/540', title: '喀纳斯之秋',     location: '新疆·喀纳斯' }
      ],
      recent: [
        { id: 4, url: 'https://picsum.photos/seed/street1/400/400',    location: '成都·太古里' },
        { id: 5, url: 'https://picsum.photos/seed/flower1/400/400',    location: '无锡·鼋头渚' },
        { id: 6, url: 'https://picsum.photos/seed/sea1/400/400',       location: '厦门·曾厝垵' },
        { id: 7, url: 'https://picsum.photos/seed/temple1/400/400',    location: '京都·伏见稻荷' },
        { id: 8, url: 'https://picsum.photos/seed/forest1/400/400',    location: '雨崩·神瀑' },
        { id: 9, url: 'https://picsum.photos/seed/sunset2/400/400',    location: '大理·洱海' }
      ]
    };
  }

  /* ---------- 生活 ---------- */
  function fetchLife() {
    return get('/life').then(function (data) {
      return data || getMockLife();
    });
  }
  function getMockLife() {
    return [
      {
        id: 1, date: '2025-05-10', title: '周末烘焙实验',
        category: '日常', tagType: '', color: '#c8956c',
        content: '第一次尝试做可颂，虽然层次还不够分明，但黄油香气弥漫整个厨房的幸福感是真实的。下次试试72小时冷藏发酵。',
        images: [
          'https://picsum.photos/seed/bread1/200/200',
          'https://picsum.photos/seed/bread2/200/200'
        ],
        important: false
      },
      {
        id: 2, date: '2025-04-28', title: '读完《置身事内》',
        category: '阅读', tagType: 'success', color: '#6b9e8a',
        content: '兰小欢老师把中国政府与经济发展讲得深入浅出。理解了城投、土地财政和产业政策背后的逻辑，对宏观经济有了更立体的认识。',
        important: true
      },
      {
        id: 3, date: '2025-04-15', title: '武功山日出',
        category: '旅行', tagType: 'warning', color: '#e6a23c',
        content: '凌晨三点出发，在绝望坡上风大到站不稳。但当第一缕光穿透云海、金色铺满草甸的那一刻，一切都值了。',
        images: [
          'https://picsum.photos/seed/wugong1/200/200',
          'https://picsum.photos/seed/wugong2/200/200',
          'https://picsum.photos/seed/wugong3/200/200'
        ],
        important: false
      },
      {
        id: 4, date: '2025-03-20', title: '入坑手冲咖啡',
        category: '日常', tagType: '', color: '#c8956c',
        content: '买了 Hario V60 和 Timemore C2 手摇磨。从最初的过萃苦涩到现在能喝出耶加雪菲的柑橘花香，过程本身就很有乐趣。',
        important: false
      }
    ];
  }

  /* ---------- 工作 ---------- */
  function fetchWork() {
    return get('/work').then(function (data) {
      return data || getMockWork();
    });
  }
  function getMockWork() {
    return [
      {
        id: 1,
        name: '智能运维平台 v2.0',
        status: '进行中', statusType: 'warning',
        description: '基于 Spring Boot + Vue 的企业级 AIOps 平台，集成日志分析、异常检测与自动告警。后端使用 Elasticsearch 做日志检索，Flink 做实时流处理。',
        progress: 68,
        progressColor: '#c8956c',
        techStack: ['Spring Boot', 'Vue 3', 'Elasticsearch', 'Flink', 'Docker'],
        dateRange: '2024.11 — 至今',
        link: '#'
      },
      {
        id: 2,
        name: '内部工具集 CLI',
        status: '已完成', statusType: 'success',
        description: '用 Go 编写的命令行工具集，包含数据库迁移、配置生成、代码审查辅助等功能。大幅提升了团队日常开发效率。',
        progress: 100,
        progressColor: '#6b9e8a',
        techStack: ['Go', 'Cobra', 'Docker'],
        dateRange: '2024.08 — 2024.10',
        link: '#'
      },
      {
        id: 3,
        name: '移动端性能监控 SDK',
        status: '规划中', statusType: 'info',
        description: '轻量级 Android/iOS 性能采集 SDK，支持帧率、启动耗时、内存等指标上报。计划用 Rust 编写核心采集模块以降低性能开销。',
        progress: 15,
        progressColor: '#909399',
        techStack: ['Rust', 'Kotlin', 'Swift', 'gRPC'],
        dateRange: '2025.Q3 规划',
        link: ''
      }
    ];
  }

  /* ---------- Public ---------- */
  return {
    fetchGames:  fetchGames,
    fetchPhotos: fetchPhotos,
    fetchLife:   fetchLife,
    fetchWork:   fetchWork
  };

})();
