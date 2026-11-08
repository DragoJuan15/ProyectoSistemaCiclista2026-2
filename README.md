
# Sistema de Monitoreo, Vigilancia y Seguridad para Ciclistas en Zona Urbana y Metropolitana

## 1. Descripción del proyecto

Este proyecto desarrolla un sistema de monitoreo y seguridad para ciclistas en zonas urbanas y metropolitanas mediante visión artificial, procesamiento de imágenes, sensores del dispositivo móvil y generación de alertas.

El sistema utiliza un modelo de detección de objetos basado en YOLO, integrado en una aplicación Android mediante ONNX Runtime. Su propósito es identificar elementos que puedan representar un riesgo para el ciclista y proporcionar avisos visuales y auditivos.

El desarrollo contempla las siguientes herramientas:

- Python para preparación de datos y entrenamiento.
- Ultralytics YOLO para entrenamiento y exportación de modelos.
- CVAT para anotación y administración de videos.
- TransT y Nuclio para pruebas de seguimiento de objetos dentro del entorno CVAT.
- OpenCV y NumPy para procesamiento de imágenes.
- Android Studio y Kotlin para la aplicación móvil.
- ONNX Runtime Android para ejecutar el modelo entrenado en el teléfono.

## 2. Arquitectura general

El desarrollo se divide en cuatro etapas:

1. Preparación y anotación de videos.
2. Construcción y revisión del conjunto de datos.
3. Entrenamiento y exportación del modelo YOLO.
4. Integración del modelo en la aplicación Android.

### Clases del modelo

El modelo final trabaja con tres clases:

| ID | Clase |
|---|---|
| 0 | vehiculo |
| 1 | peaton |
| 2 | objetoSolido |

Los estados `reposo` y `movimiento` se manejan mediante seguimiento y análisis temporal, no como clases adicionales del modelo final.

La lógica de riesgo también contempla situaciones como `casiAccidente` y `peligroReal`. Estos eventos se analizan mediante la lógica del sistema y no deben confundirse con las tres clases de detección del modelo final.

---

# 3. Requisitos previos

## 3.1 Hardware recomendado

Para el entorno de desarrollo se recomienda:

- Computadora con Windows 10 u 11.
- Procesador multinúcleo.
- 16 GB de RAM como mínimo recomendado.
- Almacenamiento disponible para videos, imágenes y modelos.
- GPU NVIDIA compatible con CUDA, si se desea acelerar el entrenamiento.

La GPU no es obligatoria para preparar el dataset, instalar las dependencias o ejecutar todas las herramientas. Sin embargo, puede reducir considerablemente el tiempo de entrenamiento.

## 3.2 Software necesario

Instalar los siguientes programas:

- Python.
- Git.
- Docker Desktop.
- Windows Subsystem for Linux 2 (WSL2).
- Ubuntu para WSL2.
- Android Studio, para compilar la aplicación móvil.

Descargas oficiales:

- Python: https://www.python.org/downloads/
- Git: https://git-scm.com/downloads
- Docker Desktop: https://www.docker.com/products/docker-desktop/
- WSL: https://learn.microsoft.com/windows/wsl/install
- Ubuntu: https://apps.microsoft.com/
- Android Studio: https://developer.android.com/studio

Se recomienda reiniciar la computadora después de instalar o actualizar WSL2, Docker Desktop o los controladores gráficos.

## 3.3 Comprobar las instalaciones

Abrir PowerShell y ejecutar:

```powershell
python --version
git --version
docker --version
wsl --version
```

Si alguno de los comandos no se reconoce, comprobar que el programa correspondiente esté instalado y agregado al PATH.

---

# 4. Descargar el proyecto

Abrir PowerShell y ubicarse en el directorio donde se desea almacenar el proyecto.

Ejemplo:

```powershell
cd C:\
```

Clonar el repositorio:

```powershell
git clone URL_DEL_REPOSITORIO
```

Entrar en la carpeta:

```powershell
cd NOMBRE_DEL_REPOSITORIO
```

Para actualizar los archivos desde GitHub:

```powershell
git pull
```

**Nota:** sustituir `URL_DEL_REPOSITORIO` y `NOMBRE_DEL_REPOSITORIO` por los datos reales del repositorio.

---

# 5. Preparación del entorno virtual de Python

Se recomienda utilizar un entorno virtual para mantener las dependencias del proyecto separadas de las demás instalaciones de Python.

## 5.1 Abrir PowerShell en la carpeta del proyecto

Ejemplo:

```powershell
cd C:\SistemaCiclista2
```

Comprobar Python:

```powershell
python --version
```

Crear el entorno virtual:

```powershell
python -m venv .venv
```

Este comando genera una carpeta llamada `.venv`, donde se almacenarán las bibliotecas instaladas para el proyecto.

## 5.2 Activar el entorno virtual

Ejecutar:

```powershell
.\.venv\Scripts\Activate.ps1
```

Cuando la activación sea correcta, PowerShell mostrará el nombre del entorno al inicio de la línea:

```text
(.venv) PS C:\SistemaCiclista2>
```

## 5.3 Solucionar el bloqueo de ejecución de PowerShell

En algunas instalaciones de Windows, PowerShell impide ejecutar scripts por su política de seguridad.

Si aparece un mensaje indicando que la ejecución de scripts está deshabilitada, ejecutar:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

Después, volver a ejecutar:

```powershell
.\.venv\Scripts\Activate.ps1
```

Esta configuración solamente afecta a la sesión actual de PowerShell. Al cerrar la terminal, deja de aplicarse.

Como alternativa, se puede activar el entorno desde CMD:

```cmd
.venv\Scripts\activate.bat
```

## 5.4 Instalar dependencias

Actualizar pip:

```powershell
python -m pip install --upgrade pip
```

Instalar las bibliotecas principales:

```powershell
python -m pip install numpy pyyaml ultralytics
```

Estas bibliotecas cumplen las siguientes funciones:

| Biblioteca | Función |
|---|---|
| NumPy | Operaciones con matrices y datos numéricos. |
| PyYAML | Lectura y escritura de archivos YAML. |
| Ultralytics | Entrenamiento, validación y exportación de YOLO. |
| PyTorch | Ejecución y entrenamiento del modelo. |

Ultralytics instala las dependencias necesarias de PyTorch según la configuración disponible. Para aprovechar CUDA se debe instalar una versión de PyTorch compatible con el controlador NVIDIA y con la versión de CUDA correspondiente.

## 5.5 Comprobar las dependencias

Ejecutar:

```powershell
python -c "import torch, ultralytics, numpy, yaml; print('PyTorch:', torch.__version__); print('Ultralytics:', ultralytics.__version__); print('CUDA disponible:', torch.cuda.is_available()); print('GPU:', torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'No disponible')"
```

Si CUDA aparece como `False`, el entrenamiento utilizará CPU cuando no se especifique otro dispositivo compatible.

**Importante:** instalar CUDA o tener una tarjeta NVIDIA no garantiza que PyTorch utilice automáticamente la GPU. Se necesita una compilación de PyTorch compatible con CUDA.

## 5.6 Desactivar el entorno virtual

Cuando se termine de trabajar:

```powershell
deactivate
```

Para continuar otro día, abrir PowerShell en la carpeta del proyecto y volver a activar el entorno:

```powershell
.\.venv\Scripts\Activate.ps1
```

---

# 6. Instalación y ejecución de CVAT

CVAT permite administrar videos, crear tareas de anotación y revisar las etiquetas asociadas a los cuadros de cada grabación.

La instalación de CVAT se realiza mediante Docker.

## 6.1 Preparar WSL2

Abrir PowerShell como administrador:

```powershell
wsl --install
```

Si WSL2 ya está instalado, comprobar su estado:

```powershell
wsl --status
```

Consultar las distribuciones instaladas:

```powershell
wsl -l -v
```

Se recomienda utilizar Ubuntu con WSL2.

## 6.2 Iniciar Docker Desktop

Abrir Docker Desktop y comprobar que el motor de Docker esté iniciado.

En PowerShell:

```powershell
docker version
```

También se puede comprobar:

```powershell
docker compose version
```

Si Docker no responde, revisar que Docker Desktop esté iniciado y que la integración con WSL2 esté habilitada.

## 6.3 Obtener CVAT

El repositorio oficial de CVAT es:

https://github.com/cvat-ai/cvat

Si se va a realizar una instalación nueva, desde WSL2:

```bash
git clone https://github.com/cvat-ai/cvat
cd cvat
```

Si CVAT ya está instalado, no es necesario volver a descargarlo. Entrar a la carpeta existente.

## 6.4 Iniciar CVAT

Desde la carpeta de CVAT ejecutar:

```bash
docker compose up -d
```

Este comando inicia los servicios definidos en el archivo Docker Compose en segundo plano.

Comprobar los contenedores:

```bash
docker compose ps
```

Para consultar los registros:

```bash
docker compose logs -f
```

Acceder desde el navegador:

http://localhost:8080

## 6.5 Crear un usuario administrador

En la terminal de la carpeta de CVAT:

```bash
docker exec -it cvat_server bash -ic 'python3 ~/manage.py createsuperuser'
```

Seguir las instrucciones para crear el usuario.

## 6.6 Detener y volver a iniciar CVAT

Detener los servicios:

```bash
docker compose stop
```

Volver a iniciarlos:

```bash
docker compose start
```

Detener y eliminar los contenedores:

```bash
docker compose down
```

**Importante:** `docker compose down` no equivale a borrar los datos persistentes. No utilizar opciones de eliminación de volúmenes si se desea conservar las anotaciones y tareas.

---

# 7. Preparación de videos y anotaciones

Antes de entrenar un modelo, es necesario disponer de imágenes y etiquetas organizadas.

## 7.1 Crear una tarea en CVAT

1. Abrir CVAT en el navegador.
2. Iniciar sesión.
3. Crear una nueva tarea.
4. Asignar un nombre identificable al video.
5. Configurar las etiquetas correspondientes.
6. Subir el video.
7. Iniciar la anotación.

Se recomienda utilizar nombres que permitan identificar el origen y las condiciones de grabación.

Por ejemplo:

```text
video_urbano_dia_01
video_urbano_noche_01
video_peaton_01
video_vehiculo_01
video_objeto_01
```

## 7.2 Etiquetas originales

Durante las etapas iniciales se utilizaron las siguientes etiquetas:

| ID original | Etiqueta |
|---|---|
| 0 | vehiculo |
| 1 | peaton |
| 2 | bache |
| 3 | objetoSolido |
| 4 | casiAccidente |
| 5 | peligroReal |

Las clases `bache`, `casiAccidente` y `peligroReal` se utilizaron en etapas anteriores de preparación y análisis.

El entrenamiento final V10 utiliza únicamente:

- `vehiculo`
- `peaton`
- `objetoSolido`

La selección de estas clases debe respetarse al preparar las etiquetas para el modelo final.

## 7.3 Seguimiento y estados

Los estados `reposo` y `movimiento` representan características temporales de los objetos detectados.

No deben agregarse como clases adicionales al archivo de configuración YOLO.

El seguimiento permite analizar cambios de posición y tamaño de los objetos entre cuadros consecutivos.

## 7.4 Exportar anotaciones

Una vez terminada la anotación:

1. Abrir la tarea.
2. Seleccionar la opción de exportación.
3. Elegir el formato compatible con YOLO utilizado por el proyecto.
4. Descargar el archivo.
5. Revisar las imágenes y etiquetas exportadas.

Se debe verificar que las coordenadas de las cajas y los identificadores de clase sean válidos antes de continuar.

---

# 8. TransT y Nuclio

TransT se utilizó como herramienta de seguimiento dentro del entorno de anotación.

Nuclio permite desplegar funciones que pueden ser utilizadas por CVAT para ejecutar modelos externos.

El despliegue de TransT se realizó mediante el entorno de CVAT y el script de despliegue para CPU.

## 8.1 Ubicarse en la carpeta de CVAT

Desde WSL2:

```bash
cd ~/cvat
```

Si la carpeta se encuentra en una ruta de Windows montada en WSL, utilizar la ruta correspondiente.

Ejemplo:

```bash
cd /mnt/c/Users/drago/cvat
```

## 8.2 Desplegar TransT para CPU

Ejecutar:

```bash
./serverless/deploy_cpu.sh serverless/pytorch/dschoerk/transt/nuclio
```

Este comando utiliza el script de despliegue para instalar la función TransT en el entorno Nuclio.

## 8.3 Comprobar el despliegue

Revisar los contenedores:

```bash
docker ps
```

Consultar los registros de los servicios:

```bash
docker compose logs -f
```

Si la función no aparece disponible, revisar los mensajes de error del despliegue y las dependencias del modelo.

**Nota:** este procedimiento corresponde al despliegue CPU utilizado en el entorno de desarrollo. No garantiza compatibilidad automática con cualquier versión de CVAT, Nuclio o sus dependencias. Para una instalación nueva, consultar también la documentación oficial de CVAT:

https://docs.cvat.ai/

---

# 9. Preparación del dataset YOLO

El dataset debe organizarse en carpetas de imágenes y etiquetas.

Una estructura habitual es:

```text
dataset_yolo/
│
├── images/
│   ├── train/
│   └── val/
│
├── labels/
│   ├── train/
│   └── val/
│
└── data.yaml
```

Cada imagen debe tener su archivo `.txt` correspondiente con las anotaciones YOLO.

## 9.1 Configuración del dataset

Ejemplo del archivo `data.yaml`:

```yaml
path: C:/SistemaCiclista2/dataset_yolo

train: images/train
val: images/val

names:
  0: vehiculo
  1: peaton
  2: objetoSolido
```

El archivo indica:

- La ubicación principal del dataset.
- La carpeta de imágenes de entrenamiento.
- La carpeta de validación.
- Los nombres de las clases.

## 9.2 Formato de las etiquetas

Cada archivo `.txt` utiliza el formato:

```text
class_id x_center y_center width height
```

Las coordenadas se encuentran normalizadas entre 0 y 1.

Ejemplo:

```text
0 0.512 0.438 0.240 0.315
```

Esto representa una detección de la clase `vehiculo` con su caja delimitadora.

## 9.3 Verificaciones antes del entrenamiento

Comprobar:

- Que cada imagen tenga su archivo de etiquetas.
- Que los identificadores de clase estén entre 0 y 2.
- Que las coordenadas estén normalizadas.
- Que no existan etiquetas vacías incorrectas.
- Que las carpetas de entrenamiento y validación estén correctamente organizadas.
- Que las imágenes puedan abrirse.

---

# 10. Entrenamiento del modelo V10

El entrenamiento final V10 utiliza YOLO11n y tres clases:

- `vehiculo`
- `peaton`
- `objetoSolido`

El script principal es:

```text
entrenamiento_V10.py
```

Se recomienda ejecutar los comandos desde la carpeta que contiene el script, con el entorno virtual activado.

## 10.1 Revisar la división del dataset

Ejecutar:

```powershell
python entrenamiento_V10.py --solo-revisar
```

Este modo permite comprobar la organización y la división de los datos antes de realizar el entrenamiento.

## 10.2 Preparar el dataset

Ejecutar:

```powershell
python entrenamiento_V10.py --preparar-only
```

Genera los archivos de configuración y preparación del dataset sin iniciar el entrenamiento.

## 10.3 Entrenamiento con división cola

Ejecutar:

```powershell
python entrenamiento_V10.py
```

Este modo utiliza la división predeterminada `cola`.

Permite entrenar el modelo y obtener métricas de validación utilizando la división temporal correspondiente.

## 10.4 Entrenamiento con división por bloques

Ejecutar:

```powershell
python entrenamiento_V10.py --split bloques
```

Este modo utiliza una división temporal por bloques.

Su objetivo es evitar que cuadros muy cercanos de una misma grabación aparezcan repartidos entre entrenamiento y validación, lo que podría producir una evaluación demasiado optimista.

## 10.5 Entrenamiento final

Ejecutar:

```powershell
python entrenamiento_V10.py --final
```

Este modo utiliza todos los datos disponibles para producir el modelo final.

**Advertencia:** las métricas de esta ejecución no deben interpretarse como una evaluación independiente de generalización si los datos de validación también se utilizan durante el entrenamiento.

Por esa razón, deben conservarse los resultados de las divisiones `cola` y `bloques`.

## 10.6 Parámetros de entrenamiento

La configuración V10 utilizada contempla:

| Parámetro | Valor |
|---|---|
| Modelo | YOLO11n |
| Épocas | 100 |
| Tamaño de imagen | 960 |
| Batch | 4 |
| Paciencia | 30 |
| Workers | 4 |
| Dispositivo | 0, cuando CUDA está disponible |

Los valores reales utilizados deben verificarse en el script y en los registros de cada ejecución.

## 10.7 Exportar a ONNX

El modelo se exporta para permitir su integración en Android.

La configuración de exportación utilizada contempla:

- Formato ONNX.
- Opset 12.
- Simplificación habilitada.
- Dimensiones dinámicas deshabilitadas.
- Precisión FP32.
- NMS externo al modelo.

El script V10 contempla la exportación ONNX dentro de su flujo de entrenamiento final.

El archivo generado debe localizarse en la carpeta de resultados de la ejecución correspondiente.

---

# 11. Interpretación de métricas

Durante el entrenamiento se generan métricas para analizar el comportamiento del modelo.

| Métrica | Significado |
|---|---|
| Precision | Proporción de detecciones positivas que son correctas. |
| Recall | Proporción de objetos reales que fueron detectados. |
| mAP50 | Media de precisión promedio con IoU de 0.50. |
| mAP50-95 | Media de precisión promedio con distintos umbrales IoU. |

Es importante distinguir las métricas de entrenamiento y validación de los resultados obtenidos durante las pruebas reales.

Una métrica elevada no garantiza que el sistema funcione correctamente en todas las condiciones de iluminación, distancia, movimiento o calidad de cámara.

---

# 12. Aplicación Android

La aplicación móvil se desarrolla en Android Studio utilizando Kotlin.

Su función es ejecutar el modelo ONNX, obtener imágenes de las cámaras, analizar detecciones y generar alertas.

## 12.1 Tecnologías utilizadas

- Kotlin.
- Android Studio.
- Jetpack Compose.
- CameraX.
- ONNX Runtime Android.
- AndroidX.
- Gradle.

## 12.2 Modelo ONNX

El archivo ONNX generado durante el entrenamiento debe copiarse a:

```text
app/src/main/assets/
```

El nombre del modelo debe coincidir con el utilizado en el código de la aplicación.

No se debe asumir que un modelo anterior, como NEW2 o NEW4, corresponde al entrenamiento V10.

## 12.3 Componentes principales

La aplicación se organiza mediante módulos con responsabilidades específicas:

| Archivo | Función |
|---|---|
| MainActivity.kt | Actividad principal y coordinación de la interfaz. |
| CameraManager.kt | Administración de la cámara integrada. |
| YoloDetector.kt | Ejecución del modelo ONNX. |
| Detection.kt | Estructura de las detecciones. |
| DetectionOverlay.kt | Dibujo de cuadros y etiquetas. |
| RiskAnalyzer.kt | Análisis de riesgo. |
| DetectionLogger.kt | Registro de detecciones en CSV. |
| AccidentSensorMonitor.kt | Lectura de acelerómetro y giroscopio. |
| UsbCameraController.kt | Administración de cámaras USB. |
| CameraFrameProcessor.kt | Preparación y procesamiento de cuadros. |
| AudioManager.kt | Emisión de alertas auditivas. |

## 12.4 Compilar y ejecutar

1. Abrir Android Studio.
2. Seleccionar Open.
3. Abrir la carpeta del proyecto Android.
4. Esperar a que Gradle sincronice las dependencias.
5. Conectar el teléfono mediante USB.
6. Activar las opciones de desarrollador.
7. Activar la depuración USB.
8. Seleccionar el dispositivo.
9. Ejecutar la aplicación.

Para compilar desde la terminal de Windows:

```powershell
.\gradlew.bat assembleDebug
```

El APK de depuración normalmente se genera en:

```text
app\build\outputs\apk\debug\app-debug.apk
```

La ubicación debe comprobarse después de compilar.

## 12.5 Pruebas de funcionamiento

Durante las pruebas se revisan:

- Inicio y detención de cámaras.
- Detección de vehículos.
- Detección de peatones.
- Detección de objetos sólidos.
- Visualización de cuadros.
- Análisis de riesgo.
- Alertas auditivas.
- Lectura de sensores.
- Generación de registros CSV.

Los resultados deben analizarse considerando las condiciones de iluminación, movimiento y calidad de imagen.

---

# 13. Solución de problemas frecuentes

## 13.1 PowerShell bloquea la activación del entorno

Error:

```text
running scripts is disabled on this system
```

Solución:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\.venv\Scripts\Activate.ps1
```

## 13.2 Python no reconoce una biblioteca

Ejemplo:

```text
ModuleNotFoundError
```

Comprobar que el entorno virtual esté activo:

```powershell
python -c "import sys; print(sys.executable)"
```

Instalar la dependencia faltante:

```powershell
python -m pip install NOMBRE_DE_LA_BIBLIOTECA
```

## 13.3 PyTorch no reconoce CUDA

Ejecutar:

```powershell
python -c "import torch; print(torch.cuda.is_available())"
```

Si devuelve `False`, comprobar:

- Controlador NVIDIA instalado.
- Versión de PyTorch.
- Compatibilidad de CUDA.
- Entorno virtual activo.

No es suficiente instalar el CUDA Toolkit si la versión instalada de PyTorch no tiene soporte CUDA.

## 13.4 Docker no inicia

Comprobar:

```powershell
docker version
wsl --status
```

Revisar que Docker Desktop esté abierto y que WSL2 esté habilitado.

## 13.5 CVAT no abre

Comprobar los contenedores:

```bash
docker compose ps
```

Consultar los registros:

```bash
docker compose logs -f
```

Comprobar que el puerto 8080 no esté siendo utilizado por otro servicio.

## 13.6 El modelo ONNX no aparece en Android

Comprobar que el archivo esté en:

```text
app/src/main/assets/
```

Verificar que el nombre del archivo coincida exactamente con el utilizado por el código Kotlin.

## 13.7 La aplicación no tiene acceso a la cámara

Comprobar:

- Permisos de cámara en AndroidManifest.xml.
- Permisos concedidos desde los ajustes del teléfono.
- Configuración de CameraX.
- Compatibilidad del dispositivo.

---

# 14. Estructura general del repositorio

La estructura puede variar dependiendo de la versión del proyecto. Una organización recomendada es:

```text
SistemaCiclista/
│
├── README.md
│
├── entrenamiento_V10.py
│
├── dataset_yolo/
│   ├── images/
│   ├── labels/
│   └── data.yaml
│
├── resultados/
│
├── modelos/
│   └── modelo_V10.onnx
│
├── videos/
│
├── anotaciones/
│
└── apps/
    └── SistemaCiclistaAndroid/
        ├── app/
        ├── gradle/
        ├── build.gradle.kts
        └── settings.gradle.kts
```

Los videos originales, modelos pesados y datasets completos pueden mantenerse fuera de GitHub si superan los límites de almacenamiento del repositorio.

---

# 15. Recomendaciones para trabajar con Git

Consultar los cambios:

```powershell
git status
```

Agregar los archivos modificados:

```powershell
git add .
```

Crear un commit:

```powershell
git commit -m "Actualizacion del sistema ciclista"
```

Subir los cambios:

```powershell
git push
```

Se recomienda no subir:

- Entornos virtuales `.venv`.
- Cachés de Python.
- Carpetas temporales.
- Videos privados.
- Archivos de entrenamiento demasiado grandes.
- Credenciales o contraseñas.

Un archivo `.gitignore` básico puede contener:

```gitignore
.venv/
__pycache__/
*.pyc
runs/
*.cache
.idea/
.gradle/
local.properties
build/
```

---

# 16. Referencias oficiales

- Ultralytics: https://docs.ultralytics.com/
- PyTorch: https://pytorch.org/
- CVAT: https://www.cvat.ai/
- CVAT Documentation: https://docs.cvat.ai/
- Docker: https://docs.docker.com/
- ONNX: https://onnx.ai/
- ONNX Runtime: https://onnxruntime.ai/
- Android Developers: https://developer.android.com/
- Git: https://git-scm.com/doc

---

# 17. Consideraciones finales

El sistema se encuentra organizado en etapas de desarrollo que permiten preparar los datos, entrenar modelos de detección y posteriormente integrarlos en una aplicación Android.

La reproducibilidad del proyecto depende de mantener organizados los videos, las anotaciones, los archivos de configuración, los modelos y las versiones de las dependencias.

Para obtener resultados comparables, se recomienda conservar los registros de cada entrenamiento y documentar las condiciones en las que se realizaron las pruebas.
