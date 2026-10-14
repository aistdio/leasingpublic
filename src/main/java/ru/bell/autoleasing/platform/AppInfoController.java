package ru.bell.autoleasing.platform;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class AppInfoController {
    private final AppInfo info;

    public AppInfoController(@Value("${autolease.app-env}") String appEnv, ObjectProvider<BuildProperties> build) {
        BuildProperties properties = build.getIfAvailable();
        this.info = new AppInfo(appEnv, properties != null ? properties.getVersion() : "dev");
    }

    @GetMapping("/info")
    public AppInfo info() {
        return info;
    }

    public record AppInfo(String appEnv, String version) {
    }
}
