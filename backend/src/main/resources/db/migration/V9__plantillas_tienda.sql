ALTER TABLE comercios
  ADD COLUMN plantilla_tienda VARCHAR(30) NOT NULL DEFAULT 'clasica',
  ADD COLUMN titulo_tienda VARCHAR(120) NOT NULL DEFAULT 'Todo lo que necesitás, a un carrito de distancia.',
  ADD COLUMN descripcion_tienda VARCHAR(240) NOT NULL DEFAULT 'Elegí tus productos. Nosotros preparamos tu pedido.';
