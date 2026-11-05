-- Contacto opcional, separado de los teléfonos de clientes y proveedores.
ALTER TABLE comercios ADD COLUMN telefono_tienda VARCHAR(30) NOT NULL DEFAULT '';
