package ru.bell.autoleasing.auth.login;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(Model model, HttpServletRequest request) {
        String error = request.getParameter("error");
        String logout = request.getParameter("logout");
        if (error != null) {
            model.addAttribute("loginError", "Возникла ошибка при попытке входа.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "Выход выполнен успешно.");
        }
        return "login";
    }

    @GetMapping("/home")
    public String home() {
        return "home";
    }
}
