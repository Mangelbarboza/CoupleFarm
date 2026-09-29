# Personalizar música y razas

## Agregar música propia

Android no admite subcarpetas dentro de `res/raw`, por eso la carpeta válida es exactamente:

`app/src/main/res/raw/`

1. Copia ahí archivos `.ogg`, `.mp3` o `.wav`.
2. El nombre tiene que comenzar con `music_`, estar en minúsculas y usar solo letras, números y guion bajo. Ejemplos: `music_lluvia.ogg`, `music_granja_02.mp3`.
3. Compila de nuevo el APK. El juego descubre automáticamente todos los recursos `music_*` y los reproduce consecutivamente en orden alfabético; al terminar vuelve a empezar.

No hace falta registrar cada canción en Kotlin. Los efectos `sfx_*` no entran en la lista musical. Para un APK liviano se recomienda OGG, estéreo a 96–160 kbps. Usa únicamente música propia o que tengas permiso de distribuir.

## Agregar una raza de perro o gato

El comportamiento no se duplica: todas las razas de una misma especie comparten movimiento, seguimiento, órdenes y crecimiento. Solo cambian nombre, precio y sprites.

Cada raza usa 12 PNG transparentes, sin margen ni partes de otras celdas. Tamaño recomendado: `160 × 160 px` por archivo. El pivote debe ser consistente: patas centradas abajo y el animal con una escala parecida a las razas existentes.

Convención para un perro con prefijo `puppy_husky` (en `app/src/main/res/drawable-nodpi/`):

- `puppy_husky_sit_down_v7.png`
- `puppy_husky_sit_right_v7.png`
- `puppy_husky_sit_up_v7.png`
- `puppy_husky_sit_left_v7.png`
- `puppy_husky_walk_a_down_v7.png`, `...right...`, `...up...`, `...left...`
- `puppy_husky_walk_b_down_v7.png`, `...right...`, `...up...`, `...left...`

Para un gato usa, por ejemplo, `kitten_siamese` y los mismos sufijos. El sufijo `_v7` forma parte de la convención actual aunque el dibujo sea nuevo. Evita espacios, mayúsculas, tildes y guiones en nombres de recursos Android.

Luego abre `app/src/main/java/com/example/couplefarm/ui/PetBreedCatalog.kt` y agrega una sola `PetBreedDefinition` a `dogs` o `cats`, con un `breedIndex` nuevo que no se repita dentro de esa especie. Usa el sprite sentado hacia abajo como `previewResource`.

Ejemplo dentro de `dogs`:

```kotlin
PetBreedDefinition(
    DomesticAnimalType.DOG,
    3,
    "puppy_husky",
    "Husky",
    420,
    R.drawable.puppy_husky_sit_down_v7,
),
```

El precio de cobro actualmente es común por especie (`dogPurchasePrice` o `catPurchasePrice` en `FarmWorldConfig`), así que conviene mostrar ese mismo valor en el catálogo.

Lista de control antes de compilar:

- Los 12 PNG tienen fondo alfa real, no blanco ni tablero de transparencia dibujado.
- Ningún dibujo toca el borde del archivo.
- Las cuatro direcciones miran realmente hacia abajo, derecha, arriba e izquierda.
- Los cuadros A/B cambian las patas, pero conservan tamaño y pivote.
- El `spritePrefix` del catálogo coincide exactamente con los nombres de archivo.
