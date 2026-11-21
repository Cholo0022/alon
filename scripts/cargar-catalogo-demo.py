"""Carga un catálogo ficticio en Mi comercio desde la carpeta Compose del VPS."""

import argparse
import hashlib
import io
import json
import subprocess
import tarfile
import uuid
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import urlopen

from PIL import Image, ImageDraw


COMPOSE = ["docker", "compose", "-f", "compose.hostinger.yaml", "-f", "compose.https.yaml"]
GROUPS = {
    "ARTICULO": {
        "home-decoration": "Decoración",
        "kitchen-accessories": "Bazar y cocina",
        "furniture": "Muebles",
        "sports-accessories": "Accesorios deportivos",
        "mobile-accessories": "Accesorios para celulares",
        "laptops": "Computación",
        "mens-watches": "Relojes",
        "womens-watches": "Relojes",
        "womens-bags": "Bolsos",
        "groceries": "Almacén",
        "beauty": "Belleza",
        "fragrances": "Perfumería",
    },
    "INDUMENTARIA": {"mens-shirts": "Camisas y remeras", "womens-dresses": "Vestidos"},
    "CALZADO": {"mens-shoes": "Calzado masculino", "womens-shoes": "Calzado femenino"},
}


def mysql(sql):
    result = subprocess.run(
        COMPOSE + ["exec", "-T", "mysql", "sh", "-ec",
                   'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql --default-character-set=utf8mb4 -u "$MYSQL_USER" "$MYSQL_DATABASE" -BN'],
        input=sql.encode(), stdout=subprocess.PIPE, check=True,
    )
    return result.stdout.decode().strip()


def literal(value):
    return "CONVERT(X'" + value.encode().hex() + "' USING utf8mb4)"


def fetch(url, allowed_host):
    if urlsplit(url).scheme != "https" or urlsplit(url).hostname != allowed_host:
        raise ValueError("Origen de imagen inesperado: " + url)
    with urlopen(url, timeout=30) as response:
        if urlsplit(response.geturl()).hostname != allowed_host:
            raise ValueError("El servidor redirigió fuera del origen esperado.")
        data = response.read(5 * 1024 * 1024 + 1)
    if len(data) > 5 * 1024 * 1024:
        raise ValueError("Archivo demasiado grande.")
    return data


def illustration(kind, product_id):
    colors = ("#2563eb", "#0f766e", "#b45309", "#9333ea", "#be123c")
    color = colors[product_id % len(colors)]
    image = Image.new("RGB", (480, 480), "#f1f5f9")
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((28, 28, 452, 452), radius=28, fill="white")
    draw.ellipse((105, 320, 375, 358), fill="#e2e8f0")
    if kind == "INDUMENTARIA":
        draw.polygon(((97, 168), (174, 110), (207, 101), (240, 134),
                      (273, 101), (306, 110), (383, 168), (340, 221),
                      (303, 199), (303, 326), (177, 326), (177, 199),
                      (140, 221)), fill=color)
    elif kind == "CALZADO":
        draw.polygon(((99, 298), (106, 233), (155, 244), (180, 169),
                      (226, 173), (276, 234), (367, 261), (389, 294)),
                     fill=color)
        draw.rounded_rectangle((93, 288, 393, 316), radius=12, fill="#334155")
        for offset in (0, 18, 36):
            draw.line((198 + offset, 209 + offset // 2,
                       222 + offset, 196 + offset // 2), fill="white", width=5)
    else:
        draw.polygon(((117, 176), (238, 121), (362, 176),
                      (362, 302), (238, 349), (117, 297)), fill=color)
        draw.line(((117, 176), (238, 223), (362, 176)), fill="white", width=4)
        draw.line((238, 223, 238, 349), fill="white", width=4)
    draw.text((240, 399), f"DEMO  /  {kind}  /  {product_id:03}", fill="#334155", anchor="mm")
    return image


def publish_demo(store):
    demo = """a.comercio_id={store} AND a.estado='ACTIVO'
        AND ((a.tipo_producto='ARTICULO' AND a.codigo BETWEEN 'DEMO26-A001' AND 'DEMO26-A100')
          OR (a.tipo_producto='INDUMENTARIA' AND a.codigo BETWEEN 'DEMO26-I001' AND 'DEMO26-I100')
          OR (a.tipo_producto='CALZADO' AND a.codigo BETWEEN 'DEMO26-C001' AND 'DEMO26-C100'))
        AND a.codigo REGEXP '^DEMO26-[AIC][0-9]{{3}}$'""".format(store=store)
    counts = mysql(f"SELECT tipo_producto,COUNT(*) FROM articulos a WHERE {demo} GROUP BY tipo_producto;")
    if set(counts.splitlines()) != {f"{kind}\t100" for kind in GROUPS}:
        raise SystemExit("No se encontraron 100 artículos demo activos de cada tipo. Publicación cancelada.")

    # La tienda omite productos sin stock. Solo se inicializan artículos sin movimientos.
    mysql(f"""START TRANSACTION;
        SELECT a.id FROM articulos a WHERE {demo} FOR UPDATE;
        INSERT INTO movimientos_stock
            (comercio_id,articulo_id,tipo,cantidad,referencia,descripcion,fecha_hora)
        SELECT a.comercio_id,a.id,'ALTA',5,CONCAT('ALTA-',a.codigo),
               'Stock inicial para tienda demo',NOW()
        FROM articulos a
        WHERE {demo} AND a.stock=0
          AND NOT EXISTS (SELECT 1 FROM movimientos_stock m
                          WHERE m.comercio_id=a.comercio_id AND m.articulo_id=a.id);
        UPDATE articulos a
        JOIN movimientos_stock m ON m.comercio_id=a.comercio_id AND m.articulo_id=a.id
             AND m.tipo='ALTA' AND m.referencia=CONCAT('ALTA-',a.codigo)
             AND m.descripcion='Stock inicial para tienda demo'
        SET a.stock=m.cantidad
        WHERE {demo} AND a.stock=0
          AND NOT EXISTS (SELECT 1 FROM movimientos_stock otro
                          WHERE otro.comercio_id=a.comercio_id AND otro.articulo_id=a.id AND otro.id<>m.id);
        UPDATE articulos a SET a.cargar_web=TRUE WHERE {demo};
        COMMIT;""")
    print("Tipo / publicados / con stock")
    print(mysql(f"""SELECT tipo_producto,COUNT(*),SUM(a.stock>0)
        FROM articulos a WHERE {demo} AND a.cargar_web=TRUE GROUP BY tipo_producto;"""))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--publicar-demo", action="store_true")
    args = parser.parse_args()
    store = mysql("SELECT id FROM comercios WHERE codigo='ALON' AND nombre='Mi comercio';")
    if not store.isdecimal():
        raise SystemExit("No se encontró el comercio ALON / Mi comercio. Carga cancelada.")
    if args.publicar_demo:
        publish_demo(store)
        return

    print("Descargando los modelos del catálogo de prueba...", flush=True)
    source = json.loads(fetch(
        "https://raw.githubusercontent.com/Ovi/DummyJSON/master/database/products.json",
        "raw.githubusercontent.com",
    ))
    products = source["products"] if isinstance(source, dict) else source
    articles = []
    photos = {}
    categories = {}
    cdn_blocked = False
    illustration_count = 0
    for kind, mapping in GROUPS.items():
        candidates = sorted((p for p in products if p["category"] in mapping), key=lambda p: p["id"])
        if not candidates:
            raise SystemExit("Faltan productos en el origen para " + kind)
        sizes = {
            "ARTICULO": ("",),
            "INDUMENTARIA": ("XS", "S", "M", "L", "XL", "XXL", "XXXL", "4XL", "5XL", "6XL", "XXS", "3XS"),
            "CALZADO": tuple(map(str, range(32, 49))),
        }[kind]
        if kind != "ARTICULO" and len(candidates) * len(sizes) < 100:
            raise SystemExit("El origen tiene pocos modelos para " + kind)
        for index in range(100):
            product = candidates[index % len(candidates)]
            edition = index // len(candidates)
            url = product["thumbnail"]
            if url not in photos:
                original = None
                if not cdn_blocked:
                    try:
                        original = fetch(url, "cdn.dummyjson.com")
                    except HTTPError as error:
                        cdn_blocked = error.code in (401, 403, 429)
                        if cdn_blocked:
                            print("El CDN rechazó el acceso. Se usarán ilustraciones de prueba.", flush=True)
                    except (URLError, TimeoutError):
                        cdn_blocked = True
                encoded = io.BytesIO()
                if original is None:
                    illustration(kind, product["id"]).save(encoded, format="PNG")
                    illustration_count += 1
                else:
                    with Image.open(io.BytesIO(original)) as image:
                        if image.width * image.height > 16_000_000:
                            raise ValueError("La imagen supera 16 megapíxeles: " + url)
                        image.thumbnail((600, 600))
                        image.convert("RGBA").save(encoded, format="PNG")
                data = encoded.getvalue()
                key = str(uuid.UUID(bytes=hashlib.sha256(data).digest()[:16])) + ".png"
                photos[url] = key, data
            category = mapping[product["category"]]
            categories.setdefault(category, "DEMO26-R" + hashlib.sha256(category.encode()).hexdigest()[:12])
            code = f"DEMO26-{kind[0]}{index+1:03}"
            title = product["title"][:70]
            size = sizes[edition] if kind != "ARTICULO" else ""
            suffix = f" · {size} · Único" if size else (f" · Presentación {edition+1}" if edition else "")
            cost = max(1000, round(product["price"] * 500))
            articles.append((code, title + suffix, category, kind, title if size else "", size, cost, photos[url][0]))

    print(f"Preparados {len(articles)} registros y {len(photos)} imágenes de modelos "
          f"({illustration_count} ilustraciones).", flush=True)
    archive = io.BytesIO()
    with tarfile.open(fileobj=archive, mode="w") as output:
        for key, data in dict(photos.values()).items():
            entry = tarfile.TarInfo(key)
            entry.size = len(data)
            entry.mode = 0o644
            output.addfile(entry, io.BytesIO(data))
    subprocess.run(
        COMPOSE + ["exec", "-T", "api", "sh", "-ec",
                   "mkdir -p /app/data/article-images && tar --skip-old-files -xf - -C /app/data/article-images"],
        input=archive.getvalue(), check=True,
    )

    sql = ["START TRANSACTION;", f"SELECT id FROM comercios WHERE id={store} FOR UPDATE;"]
    for name, code in categories.items():
        sql.append(f"""INSERT INTO rubros (comercio_id,codigo,nombre)
            SELECT {store},{literal(code)},{literal(name)}
            WHERE NOT EXISTS (SELECT 1 FROM rubros WHERE comercio_id={store} AND nombre={literal(name)});""")
    for code, title, category, kind, model, size, cost, key in articles:
        system = {"ARTICULO": "", "INDUMENTARIA": "LETRAS", "CALZADO": "AR"}[kind]
        specs = [kind, model, "Demo" if size else "", size, system,
                 "Único" if size else "", "Varios" if size else "",
                 "UNISEX" if size else "", "TODO EL AÑO" if size else "", "Artículo de demostración", key]
        sql.append(f"""INSERT INTO articulos
            (comercio_id,codigo,nombre,rubro_id,unidad,costo,precio_venta,margen_porcentaje,
             tipo_producto,modelo,marca,talle,sistema_talle,color,material,publico,temporada,descripcion,
             imagen_clave,imagen_ilustrativa,cargar_web)
            SELECT {store},{literal(code)},{literal(title)},r.id,'UNIDAD',{cost},{cost * 1.5:.2f},50,
                   {','.join(map(literal, specs))},TRUE,FALSE
            FROM rubros r WHERE r.comercio_id={store} AND r.nombre={literal(category)}
              AND NOT EXISTS (SELECT 1 FROM articulos WHERE comercio_id={store} AND codigo={literal(code)})
            ORDER BY r.id LIMIT 1;""")
        sql.append(f"""INSERT INTO articulo_imagenes (comercio_id,articulo_id,posicion,clave,ilustrativa)
            SELECT a.comercio_id,a.id,1,a.imagen_clave,TRUE FROM articulos a
            WHERE a.comercio_id={store} AND a.codigo={literal(code)} AND a.imagen_clave={literal(key)}
              AND NOT EXISTS (SELECT 1 FROM articulo_imagenes i WHERE i.comercio_id=a.comercio_id
                              AND i.articulo_id=a.id AND i.posicion=1);""")
    sql.append("COMMIT;")
    mysql("\n".join(sql))
    counts = mysql(f"""SELECT a.tipo_producto,COUNT(*),COUNT(i.clave) FROM articulos a
        LEFT JOIN articulo_imagenes i ON i.comercio_id=a.comercio_id AND i.articulo_id=a.id AND i.posicion=1
        WHERE a.comercio_id={store} AND a.codigo REGEXP '^DEMO26-[AIC][0-9]{{3}}$'
        GROUP BY a.tipo_producto ORDER BY a.tipo_producto;""")
    print("Tipo / artículos / imágenes asociadas")
    print(counts)
    if set(counts.splitlines()) != {f"{kind}\t100\t100" for kind in GROUPS}:
        raise SystemExit("Los totales no coinciden con la carga solicitada.")


if __name__ == "__main__":
    main()
