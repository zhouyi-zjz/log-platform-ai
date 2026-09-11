package com.zhouyi.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @author zjz
 * @version 1.0
 * @description:
 * @date 2026/9/11 12:30
 */
@Controller
public class LoginController {

    /**
     * 显示登录页面
     */
    @GetMapping("/login")
    public String loginPage() {
        return "login"; // 对应 src/main/resources/templates/login.html
    }

    /**
     * 根路径重定向到 /monitor
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/monitor/";
    }
}

