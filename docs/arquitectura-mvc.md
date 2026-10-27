# Arquitectura MVC de Alon

Alon conserva su interfaz web en React y su API REST en Spring Boot. Las responsabilidades se organizan en tres partes:

## Modelo

El paquete `backend/src/main/java/ar/com/alon/model/` contiene los modelos que concentran las reglas del negocio y el acceso a los datos:

- `OperationsModel`: ventas, compras, artículos, proveedores, caja y movimientos.
- `AuthModel`: inicio de sesión, contraseñas, usuarios y permisos.
- `DemoDataModel`: listados, consultas e informes.

## Vista

La vista web está en `frontend/src/views/AppView.tsx`. `frontend/src/main.tsx` inicia la aplicación y los archivos CSS definen su presentación. La vista consulta la API mediante solicitudes HTTP.

## Controlador

Los controladores de `backend/src/main/java/ar/com/alon/api/` reciben las solicitudes HTTP, validan sus parámetros y delegan el trabajo al modelo:

- `operations/OperationsController`
- `auth/AuthController`
- `demo/DemoDataController`
- `health/HealthController`

El recorrido de una operación es:

```text
Vista React → Controlador Spring MVC → Modelo → MySQL
```

La interfaz es una aplicación de página única, por eso la vista se ejecuta en el navegador y Spring entrega los datos mediante una API REST; no utiliza plantillas HTML del servidor.
