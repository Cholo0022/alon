# Alon Web

Interfaz React y TypeScript con persistencia mediante la API Spring. Los listados vacíos permanecen vacíos; los errores de red y sesión se muestran al usuario. Los datos ficticios se cargan únicamente mediante el script SQL opcional de demostración.

Requisitos: Node.js 24 y API local iniciada. Desde esta carpeta:

```sh
npm ci
npm run dev
```

La web usa http://127.0.0.1:5173; Vite reenvía /api a http://127.0.0.1:8080. El navegador y la API deben compartir origen mediante ese proxy durante desarrollo y mediante el servidor web al desplegar.

```sh
npm run build
npm run lint
npm test
npm audit
```

El análisis rechaza advertencias. Las pruebas verifican importes, fechas, listas vacías, búsqueda y paginación, sesión e identificadores de operaciones reintentadas.

Código organizado en pages/, components/, hooks/ y lib/. Los importes definitivos los calcula la API con decimales; la interfaz muestra centavos y sus totales previos son orientativos hasta recibir la confirmación del servidor.
