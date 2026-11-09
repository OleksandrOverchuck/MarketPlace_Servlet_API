package pl.jollycart.web;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

import jakarta.servlet.Filter;

import pl.jollycart.config.AppConfig;
import pl.jollycart.user.UserDao;
import pl.jollycart.web.filter.AuthorizationFilter;
import pl.jollycart.web.filter.CorsFilter;
import pl.jollycart.web.filter.UserStatusFilter;

/** Uruchamia wbudowany Tomcat, rejestruje filtry (w kolejnosci wykonania) i ApiServlet. */
public final class EmbeddedServer {

    private EmbeddedServer() {
    }

    public static Tomcat start(AppConfig config, Router router, UserDao userDao) throws Exception {
        Path baseDir = Files.createDirectories(Path.of("target", "tomcat")).toAbsolutePath();
        Path docBase = Files.createDirectories(baseDir.resolve("webapp"));

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(config.serverPort());
        tomcat.getConnector();

        Context context = tomcat.addContext("", docBase.toString());

        // Kolejnosc filtrow: CORS -> sesja i status uzytkownika -> reguly dostepu
        addFilter(context, "cors", new CorsFilter(config.corsAllowedOrigins()));
        addFilter(context, "userStatus", new UserStatusFilter(userDao));
        addFilter(context, "authorization", new AuthorizationFilter());

        Tomcat.addServlet(context, "api", new ApiServlet(router));
        context.addServletMappingDecoded("/", "api");

        tomcat.start();
        return tomcat;
    }

    private static void addFilter(Context context, String name, Filter filter) {
        FilterDef definition = new FilterDef();
        definition.setFilterName(name);
        definition.setFilter(filter);
        context.addFilterDef(definition);

        FilterMap mapping = new FilterMap();
        mapping.setFilterName(name);
        mapping.addURLPattern("/*");
        context.addFilterMap(mapping);
    }
}
