package fr.annuaire.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Adresses courtes vers l'interface d'administration (Angular) et la page de démonstration. */
@Configuration
public class PagesStatiques implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registre) {
        registre.addRedirectViewController("/", "/admin/");
        registre.addRedirectViewController("/admin", "/admin/");
        registre.addViewController("/admin/").setViewName("forward:/admin/index.html");
    }
}
