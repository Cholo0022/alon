CREATE TABLE usuarios (
  id BIGINT NOT NULL AUTO_INCREMENT,
  comercio_id BIGINT NOT NULL,
  usuario VARCHAR(80) NOT NULL,
  nombre VARCHAR(160) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  rol VARCHAR(20) NOT NULL,
  permisos TEXT NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_usuarios_comercio_usuario (comercio_id, usuario),
  CONSTRAINT fk_usuarios_comercio FOREIGN KEY (comercio_id) REFERENCES comercios(id),
  CONSTRAINT chk_usuarios_rol CHECK (rol IN ('DUENO', 'VENDEDOR', 'PERSONALIZADO'))
);
