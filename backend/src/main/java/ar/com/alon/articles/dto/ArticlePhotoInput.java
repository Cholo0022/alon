package ar.com.alon.articles.dto;

import jakarta.validation.constraints.Size;

/** Retain a current key, or upload raster bytes. Never accepts filesystem paths. */
public record ArticlePhotoInput(
        @Size(max = 80) String key, @Size(max = 7000000) String data, boolean illustrative) {}
