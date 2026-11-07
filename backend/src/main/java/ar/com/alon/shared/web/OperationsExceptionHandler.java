// Convierte los errores del servidor en mensajes que la interfaz puede mostrar.
package ar.com.alon.shared.web;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OperationsExceptionHandler {
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> uploadTooLarge(Exception exception) {
        return ResponseEntity.status(413)
                .body(Map.of("error", "El archivo de backup supera el límite de 512 MB."));
    }

    /**
     * Convierte una validación de negocio fallida en una respuesta 400 con el mensaje de error para
     * el usuario.
     */
    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<Map<String, Object>> fieldValidation(FieldValidationException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", exception.getMessage(), "fields", exception.fields()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidInput(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    /**
     * Devuelve un conflicto 409 ante duplicados o referencias de datos incompatibles sin revelar
     * detalles SQL.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> invalidReference(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        Map.of(
                                "error",
                                "Ya existe un registro con esos datos o una referencia no es válida."));
    }

    /** Traduce la ausencia de un registro consultado a una respuesta 404 legible. */
    @ExceptionHandler(EmptyResultDataAccessException.class)
    public ResponseEntity<Map<String, String>> notFound(EmptyResultDataAccessException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "No se encontró el registro solicitado."));
    }

    /** Conserva el código HTTP de una excepción controlada y devuelve su motivo como error JSON. */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(
            org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(
                        Map.of(
                                "error",
                                exception.getReason() == null
                                        ? "No se pudo completar la solicitud."
                                        : exception.getReason()));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalidFields(
            org.springframework.web.bind.MethodArgumentNotValidException exception) {
        var fields = new java.util.LinkedHashMap<String, String>();
        for (var field : exception.getBindingResult().getFieldErrors())
            fields.putIfAbsent(
                    field.getField(),
                    switch (field.getCode() == null ? "" : field.getCode()) {
                        case "NotBlank", "NotNull", "NotEmpty" -> "Completá este campo.";
                        case "Min", "Positive", "PositiveOrZero", "DecimalMin", "Max" ->
                                "Revisá la cantidad y los límites permitidos.";
                        case "Size" -> "Revisá la longitud permitida para este campo.";
                        case "Email" -> "Ingresá un email válido.";
                        default -> "Revisá el formato de este campo.";
                    });
        return ResponseEntity.badRequest()
                .body(
                        Map.of(
                                "error",
                                "Revisá los campos señalados: "
                                        + fields.keySet().stream()
                                                .map(this::fieldLabel)
                                                .collect(java.util.stream.Collectors.joining(", "))
                                        + ".",
                                "fields",
                                fields));
    }

    /** Conserva el campo que falló al convertir una cantidad o un formato JSON. */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(
            org.springframework.http.converter.HttpMessageNotReadableException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof tools.jackson.core.JacksonException mapping
                    && !mapping.getPath().isEmpty()) {
                var path = new StringBuilder();
                for (var reference : mapping.getPath()) {
                    if (reference.getPropertyName() != null) {
                        if (!path.isEmpty()) path.append('.');
                        path.append(reference.getPropertyName());
                    } else if (reference.getIndex() >= 0)
                        path.append('[').append(reference.getIndex()).append(']');
                }
                if (!path.isEmpty()) {
                    String message = "Revisá el formato de este campo.";
                    if (cause
                                    instanceof
                                    tools.jackson.databind.exc.MismatchedInputException mismatch
                            && (mismatch.getTargetType() == int.class
                                    || mismatch.getTargetType() == long.class))
                        message = "Ingresá un número entero dentro del límite permitido.";
                    return ResponseEntity.badRequest()
                            .body(
                                    Map.of(
                                            "error",
                                            "Revisá el campo señalado: "
                                                    + fieldLabel(path.toString())
                                                    + ".",
                                            "fields",
                                            Map.of(path.toString(), message)));
                }
            }
            cause = cause.getCause();
        }
        return ResponseEntity.badRequest()
                .body(
                        Map.of(
                                "error",
                                "La solicitud tiene un formato inválido. Revisá los datos y volvé a guardar."));
    }

    private String fieldLabel(String path) {
        String key = path.startsWith("product.") ? path.substring(8) : path;
        var variant = java.util.regex.Pattern.compile("variants\\[(\\d+)\\]\\.(\\w+)").matcher(key);
        if (variant.matches()) {
            String label =
                    switch (variant.group(2)) {
                        case "size" -> "talle";
                        case "color" -> "color";
                        case "code" -> "código";
                        case "quantity" -> "cantidad";
                        default -> "dato";
                    };
            return label + " del renglón " + (Integer.parseInt(variant.group(1)) + 1);
        }
        return switch (key) {
            case "buyer.firstName" -> "nombre";
            case "buyer.lastName" -> "apellido";
            case "buyer.phone" -> "celular de diez dígitos";
            case "buyer.dni" -> "DNI";
            case "buyer.province" -> "provincia";
            case "buyer.city" -> "ciudad";
            case "buyer" -> "datos de contacto";
            case "name" -> "nombre";
            case "code" -> "código";
            case "cost" -> "costo";
            case "initialStock", "minimumStock" -> "stock";
            case "freightPercent" -> "flete";
            case "marginPercent" -> "ganancia";
            case "apparel.model" -> "modelo";
            case "apparel.brand" -> "marca";
            case "apparel.size" -> "talle";
            case "apparel.color" -> "color";
            case "apparel.material" -> "material";
            case "apparel.description" -> "descripción";
            case "apparel.sizeSystem" -> "sistema de talles";
            case "apparel.audience" -> "público";
            case "apparel.season" -> "temporada";
            default -> key;
        };
    }

    /**
     * Responde 400 cuando la estructura, los campos o los formatos de la solicitud no superan la
     * validación.
     */
    @ExceptionHandler({
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        jakarta.validation.ConstraintViolationException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class
    })
    public ResponseEntity<Map<String, String>> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(
                        Map.of(
                                "error",
                                "Revisá los campos obligatorios, formatos, límites y cantidades de la solicitud."));
    }

    /**
     * Devuelve un conflicto 409 cuando otra operación impide completar la modificación concurrente.
     */
    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> concurrentChange(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Otra operación modificó estos datos. Volvé a intentar."));
    }

    /**
     * Conserva el código de error del protocolo HTTP y presenta un mensaje sobre ruta, método o
     * formato de la solicitud.
     */
    @ExceptionHandler({
        org.springframework.web.HttpRequestMethodNotSupportedException.class,
        org.springframework.web.HttpMediaTypeNotSupportedException.class,
        org.springframework.web.HttpMediaTypeNotAcceptableException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.servlet.resource.NoResourceFoundException.class
    })
    public ResponseEntity<Map<String, String>> protocolError(Exception exception) {
        var error = (org.springframework.web.ErrorResponse) exception;
        return ResponseEntity.status(error.getStatusCode())
                .body(
                        Map.of(
                                "error",
                                "Revisá la dirección, el método y el formato de la solicitud."));
    }

    /**
     * Registra el fallo técnico en el servidor y responde 500 con un mensaje que no expone datos
     * internos.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception exception) {
        org.slf4j.LoggerFactory.getLogger(getClass())
                .error("Fallo inesperado al procesar la solicitud", exception);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "No se pudo completar la operación. Intentá nuevamente."));
    }
}
