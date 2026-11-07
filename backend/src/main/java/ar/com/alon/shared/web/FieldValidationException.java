package ar.com.alon.shared.web;

import java.util.Map;

/** Error de negocio vinculado a controles concretos del formulario. */
public class FieldValidationException extends IllegalArgumentException {
    private final Map<String, String> fields;

    public FieldValidationException(String message, Map<String, String> fields) {
        super(message);
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}
