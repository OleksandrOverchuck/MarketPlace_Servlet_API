package pl.jollycart.web;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

import jakarta.servlet.Filter;
import jakarta.servlet.MultipartConfigElement;

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

        Wrapper servlet = Tomcat.addServlet(context, "api", new ApiServlet(router));
        context.addServletMappingDecoded("/", "api");

        // Przesylanie plikow (multipart/form-data): limit 10 MB na plik i na zadanie,
        // tak jak spring.servlet.multipart.max-file-size / max-request-size w wersji Spring.
        Path multipartTmp = Files.createDirectories(baseDir.resolve("multipart-tmp"));
        long tenMb = 10L * 1024 * 1024;
        servlet.setMultipartConfigElement(
                new MultipartConfigElement(multipartTmp.toString(), tenMb, tenMb, 0));

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
