package com.example.localPicmaService.Controller.module.HomePage;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BlogController {

    @GetMapping("/games")
    public List<Map> getGames() { /* 查询游戏列表 */
        return null;
    }

    @GetMapping("/photos")
    public List<Map> getPhotos() { /* 返回 { featured: [...], recent: [...] } */

        return null;
    }

    @GetMapping("/life")
    public List<Map> getLife() { /* 查询生活动态 */
        return null;
    }

    @GetMapping("/work")
    public List<Map> getWork() { /* 查询工作项目 */
        return null;
    }
}
