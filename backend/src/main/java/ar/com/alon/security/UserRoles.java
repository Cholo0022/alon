package ar.com.alon.security;

/** Centraliza los roles administrativos; DUENO es el identificador histórico de Gerente. */
public final class UserRoles {
    /** Evita instanciar esta colección de comprobaciones de roles. */
    private UserRoles() {}

    /** Identifica las cuentas habilitadas para administrar el negocio o su seguridad. */
    public static boolean administrator(Object role) {
        return "SUPER".equals(role) || "DUENO".equals(role);
    }
}
