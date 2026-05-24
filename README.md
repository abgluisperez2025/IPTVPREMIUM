# CloudStream IPTV Repository

Repositorio de extensiones CloudStream con **IPTVPlayer** mejorado.

## 📺 IPTVPlayer

Reproductor IPTV para listas M3U/M3U8 dentro de CloudStream.

**Características:**
- ✅ Parseo robusto de listas M3U
- ✅ Caché inteligente de playlists
- ✅ Manejo de errores y reconexión
- ✅ Soporte para grupos y categorías
- ✅ UI integrada en CloudStream

## 🚀 Instalación

### En CloudStream:

1. Ve a **Settings → Extensions**
2. Toca **Add Repository**
3. Pega la URL:
   ```
   https://raw.githubusercontent.com/TU_USUARIO/cloudstream-extensions-iptv/main/repo.json
   ```
4. Busca **IPTVPlayer** e instala

### Primera vez:

1. Abre IPTVPlayer en CloudStream
2. Añade tu lista M3U (URL o archivo local)
3. Disfruta

## 📝 Uso

### Agregar lista M3U:

```
URL directo: https://ejemplo.com/playlist.m3u
O archivo local: /sdcard/Downloads/canales.m3u
```

### Ejemplos de listas:

- [iptv-org](https://iptv-org.github.io/) - Base de datos IPTV global
- Tus propias listas personalizadas

## 🔧 Mejoras Implementadas

Basado en IPTVPlayer v6 de Phisher98/Adippe con:

- [ ] Parsing M3U más tolerante (maneja formatos rotos)
- [ ] Caché persistente en BD local
- [ ] Reintentos automáticos en streams muertos
- [ ] Búsqueda y filtros mejorados
- [ ] Soporte EPG (Electronic Program Guide)
- [ ] Sincronización de listas en background

## 📁 Estructura

```
.
├── repo.json                    # Manifest del repositorio
├── builds/
│   ├── IPTVPlayer.jar          # Extensión compilada
│   └── IPTVPlayer.cs3          # Metadata
├── IPTVPlayer/
│   ├── build.gradle            # Configuración Gradle
│   ├── src/main/kotlin/        # Código fuente (futuro)
│   └── src/main/res/           # Recursos (iconos, etc)
└── README.md                    # Este archivo
```

## 🛠️ Desarrollo Local

### Requisitos:
- Java 11+
- Gradle 7+
- Kotlin 1.9+

### Compilar:

```bash
cd IPTVPlayer
gradle build
```

### Generar JAR:

```bash
gradle jar
```

El JAR estará en: `build/libs/IPTVPlayer.jar`

## 📄 Créditos

**Versión Original:**
- Phisher98
- Adippe

**Mejoras y Mantenimiento:**
- TU_USUARIO

## ⚖️ Licencia

MIT License - Fork original de phisher98/cloudstream-extensions-phisher

## 🐛 Reporte de Bugs

Si encuentras problemas:

1. Abre un **Issue** en GitHub
2. Describe qué pasa (screenshots si es posible)
3. Tu lista M3U funciona en otro reproductor?

## 🚀 Roadmap

- [ ] v8: Caché mejorado + BD local
- [ ] v9: EPG integrado
- [ ] v10: Sincronización en nube

---

**Última actualización:** Mayo 2026
