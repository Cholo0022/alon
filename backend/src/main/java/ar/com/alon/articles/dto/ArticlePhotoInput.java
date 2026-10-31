// Define y valida los datos necesarios para conservar o subir una foto de artículo.
package ar.com.alon.articles.dto;

import jakarta.validation.constraints.Size;

/** Permite conservar una foto existente o subir una imagen nueva; no recibe rutas del equipo. */
public record ArticlePhotoInput(
        @Size(max = 80) String key, @Size(max = 7000000) String data, boolean illustrative) {}
