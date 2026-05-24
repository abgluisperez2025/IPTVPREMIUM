# Setup tu Repositorio IPTV en GitHub

## Paso 1: Crear el repo en GitHub

1. Ve a https://github.com/new
2. Nombre: `cloudstream-extensions-iptv` (o el que prefieras)
3. Descripción: "IPTVPlayer Extension for CloudStream - Enhanced Version"
4. **Marca: Public** (para que CloudStream pueda acceder)
5. Agrega README y .gitignore (Python/Java)
6. Click: "Create repository"

## Paso 2: Clonar y añadir contenido

```bash
# En tu máquina local
git clone https://github.com/TU_USUARIO/cloudstream-extensions-iptv.git
cd cloudstream-extensions-iptv

# Copiar estructura
cp -r /home/claude/mi-iptv-repo/* .

# Agregar JAR compilado
cp /mnt/user-data/uploads/IPTVPlayer.jar ./builds/

# Commit inicial
git add .
git commit -m "Initial: IPTVPlayer extension structure"
git push origin main
```

## Paso 3: CloudStream cargará tu repo así

En CloudStream:
```
Settings → Extensions → Add Repository

URL: https://raw.githubusercontent.com/TU_USUARIO/cloudstream-extensions-iptv/main/repo.json

O shortcode (si lo registras):
tuusuario-iptv
```

---

## Archivos clave que necesitas:

✅ `repo.json` - Metadata del repositorio
✅ `IPTVPlayer/build.gradle` - Configuración Gradle
✅ `builds/IPTVPlayer.jar` - El ejecutable
✅ `builds/IPTVPlayer.cs3` - Metadata del plugin
✅ `.github/workflows/` - CI/CD (opcional pero recomendado)
