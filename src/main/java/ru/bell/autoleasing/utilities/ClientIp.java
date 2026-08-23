package ru.bell.autoleasing.utilities;

import jakarta.servlet.http.HttpServletRequest;

public class ClientIp {
    public static String getClientIP(HttpServletRequest request) {
        String[] proxyHeaders = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED"
        };

        for (String header : proxyHeaders) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !ip.equalsIgnoreCase("unknown")) {
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }
}
