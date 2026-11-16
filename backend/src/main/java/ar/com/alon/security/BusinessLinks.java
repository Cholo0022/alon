package ar.com.alon.security;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Enlaces locales estables por instalación, sin depender de DNS ni dominios contratados. */
public final class BusinessLinks {
    private BusinessLinks() {}

    public static String slug(String name) {
        String slug =
                Normalizer.normalize(name, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}+", "")
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("^-|-$", "");
        if (slug.isBlank()) slug = "negocio";
        slug = slug.substring(0, Math.min(slug.length(), 70)).replaceAll("-$", "");
        return Set.of("tienda", "api", "assets", "actuator", "error").contains(slug)
                ? "negocio-" + slug
                : slug;
    }

    public static String management(long id, String slug) {
        return "http://127.0.0.1:" + BusinessProvisioning.portFor(id) + "/" + slug;
    }

    public static String shop(long id, String slug) {
        return "http://127.0.0.1:" + BusinessProvisioning.portFor(id) + "/tienda/" + slug;
    }
}
