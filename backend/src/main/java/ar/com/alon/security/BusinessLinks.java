package ar.com.alon.security;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Enlaces locales estables por instalación, sin depender de DNS ni dominios contratados. */
public final class BusinessLinks {
    private static final Set<String> RESERVED_HOSTS =
            Set.of("alon", "www", "mail", "negocios", "ftp", "smtp", "imap", "autodiscover");

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

    public static String management(long id, String slug, String domain) {
        if (domain.isBlank()) return management(id, slug);
        String label = publicLabel(slug);
        String hostname = reservedHost(label) ? legacyHost(id, slug, domain) : label + "." + domain;
        return "https://" + hostname + "/";
    }

    public static String shop(long id, String slug, String domain) {
        return domain.isBlank() ? shop(id, slug) : management(id, slug, domain) + "tienda/" + slug;
    }

    static String publicLabel(String slug) {
        return slug.substring(0, Math.min(slug.length(), 63)).replaceAll("-$", "");
    }

    static boolean reservedHost(String label) {
        return RESERVED_HOSTS.contains(label);
    }

    static String legacyHost(long id, String slug, String domain) {
        // Deja espacio para el puerto y su guion en la etiqueta DNS de 63 caracteres.
        return slug.substring(0, Math.min(slug.length(), 57)).replaceAll("-$", "")
                + "-"
                + BusinessProvisioning.portFor(id)
                + ".negocios."
                + domain;
    }

    public static String shop(long id, String slug) {
        return "http://127.0.0.1:" + BusinessProvisioning.portFor(id) + "/tienda/" + slug;
    }
}
