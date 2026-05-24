#!/usr/bin/env python3
"""
Setup automatizado para subir IPTVPlayer a GitHub
Uso: python3 setup_github.py
"""

import subprocess
import os
import sys
import json
from pathlib import Path

class Colors:
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    RED = '\033[91m'
    BLUE = '\033[94m'
    RESET = '\033[0m'

def print_step(msg):
    print(f"{Colors.BLUE}[*]{Colors.RESET} {msg}")

def print_success(msg):
    print(f"{Colors.GREEN}[✓]{Colors.RESET} {msg}")

def print_error(msg):
    print(f"{Colors.RED}[✗]{Colors.RESET} {msg}")

def print_warning(msg):
    print(f"{Colors.YELLOW}[!]{Colors.RESET} {msg}")

def run_cmd(cmd, check=True):
    """Ejecuta comando y retorna output"""
    try:
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        if check and result.returncode != 0:
            print_error(f"Error: {result.stderr}")
            return None
        return result.stdout.strip()
    except Exception as e:
        print_error(f"Error ejecutando comando: {e}")
        return None

def main():
    print(f"\n{Colors.BLUE}╔═══════════════════════════════════════════════════════╗{Colors.RESET}")
    print(f"{Colors.BLUE}║     Setup GitHub para IPTVPlayer CloudStream          ║{Colors.RESET}")
    print(f"{Colors.BLUE}╚═══════════════════════════════════════════════════════╝{Colors.RESET}\n")
    
    # Paso 1: Verificar git
    print_step("Verificando Git...")
    if not run_cmd("git --version"):
        print_error("Git no instalado. Instala: sudo apt-get install git")
        sys.exit(1)
    print_success("Git disponible")
    
    # Paso 2: Recopilar info del usuario
    print_step("\nConfiguración requerida:")
    
    username = input(f"{Colors.YELLOW}GitHub usuario:{Colors.RESET} ").strip()
    if not username:
        print_error("Usuario requerido")
        sys.exit(1)
    
    repo_name = input(f"{Colors.YELLOW}Nombre del repo [{Colors.BLUE}cloudstream-extensions-iptv{Colors.YELLOW}]:{Colors.RESET} ").strip() or "cloudstream-extensions-iptv"
    token = input(f"{Colors.YELLOW}GitHub Personal Access Token:{Colors.RESET} ").strip()
    
    if not token:
        print_warning("Token recomendado para push automatizado")
    
    # Paso 3: Actualizar repo.json
    print_step(f"\nActualizando config para @{username}...")
    
    repo_path = Path("repo.json")
    if repo_path.exists():
        with open(repo_path, 'r') as f:
            config = json.load(f)
        
        # Actualizar URLs
        for plugin in config.get("plugins", []):
            plugin["url"] = f"https://raw.githubusercontent.com/{username}/{repo_name}/main/builds/IPTVPlayer.cs3"
            plugin["jarUrl"] = f"https://raw.githubusercontent.com/{username}/{repo_name}/main/builds/IPTVPlayer.jar"
            plugin["iconUrl"] = f"https://raw.githubusercontent.com/{username}/{repo_name}/main/IPTVPlayer/res/icon.png"
            plugin["repositoryUrl"] = f"https://github.com/{username}/{repo_name}"
            plugin["authors"].append(username)
        
        config["repositoryUrl"] = f"https://github.com/{username}/{repo_name}"
        
        with open(repo_path, 'w') as f:
            json.dump(config, f, indent=2)
        
        print_success("repo.json actualizado")
    
    # Paso 4: Git init y primeros commits
    print_step("\nInicializando Git...")
    
    if not Path(".git").exists():
        run_cmd("git init")
        print_success("Git repositorio inicializado")
    
    run_cmd('git config user.email "noreply@example.com"', check=False)
    run_cmd('git config user.name "CloudStream"', check=False)
    
    # Paso 5: Agregar archivos
    print_step("Agregando archivos...")
    run_cmd("git add -A")
    print_success("Archivos staged")
    
    # Paso 6: Commit
    print_step("Creando primer commit...")
    run_cmd('git commit -m "Initial: IPTVPlayer repository setup"', check=False)
    print_success("Commit creado")
    
    # Paso 7: Remote setup
    print_step(f"\nConfigurando remote para {username}/{repo_name}...")
    
    if token:
        remote_url = f"https://{username}:{token}@github.com/{username}/{repo_name}.git"
    else:
        remote_url = f"https://github.com/{username}/{repo_name}.git"
    
    run_cmd(f'git remote remove origin', check=False)
    run_cmd(f'git remote add origin "{remote_url}"')
    print_success("Remote configurado")
    
    # Paso 8: Push
    print_step("Haciendo push a GitHub (puede tomar un momento)...")
    if run_cmd("git push -u origin main", check=False):
        print_success("¡Push completado!")
    else:
        print_warning("Push falló - asegúrate que el repo existe en GitHub")
        print(f"Crea manualmente: https://github.com/new?name={repo_name}")
    
    # Resumen
    print(f"\n{Colors.GREEN}╔════════════════════════════════════════════════════════╗{Colors.RESET}")
    print(f"{Colors.GREEN}║                   ¡LISTO!                            ║{Colors.RESET}")
    print(f"{Colors.GREEN}╚════════════════════════════════════════════════════════╝{Colors.RESET}")
    
    print(f"\n{Colors.BLUE}Tu repo está en:{Colors.RESET}")
    print(f"  https://github.com/{username}/{repo_name}\n")
    
    print(f"{Colors.BLUE}URL para CloudStream:{Colors.RESET}")
    print(f"  https://raw.githubusercontent.com/{username}/{repo_name}/main/repo.json\n")
    
    print(f"{Colors.BLUE}En CloudStream:{Colors.RESET}")
    print(f"  Settings → Extensions → Add Repository")
    print(f"  → Pega la URL de arriba\n")
    
    print(f"{Colors.YELLOW}Próximos pasos:{Colors.RESET}")
    print(f"  1. Clona el repo localmente para editar")
    print(f"  2. Mejora el código en src/main/kotlin/")
    print(f"  3. Compila con: gradle build")
    print(f"  4. Actualiza versión en repo.json")
    print(f"  5. git push para publicar cambios\n")

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print_error("\nCancelado por usuario")
        sys.exit(0)
    except Exception as e:
        print_error(f"Error: {e}")
        sys.exit(1)
