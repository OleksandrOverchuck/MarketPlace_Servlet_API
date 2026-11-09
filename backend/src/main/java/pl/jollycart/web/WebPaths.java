package pl.jollycart.web;

import jakarta.servlet.http.HttpServletRequest;

public final class WebPaths {

    private WebPaths() {
    }

    /** Sciezka zadania bez context path (np. /api/auth/login). */
    public static String of(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();
        return pathInfo == null ? servletPath : servletPath + pathInfo;
    }
}
