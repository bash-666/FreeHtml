# Project Context: Open Source HTML & File Viewer/Editor

## 📌 Overview
Estamos construyendo una aplicación nativa para Android usando **Kotlin y Jetpack Compose**. 
El objetivo es crear un visor y editor de archivos de código y documentos, completamente **Open Source, gratuito y sin anuncios**. La interfaz debe ser moderna, usando **Material 3** y con **Modo Oscuro** por defecto.

## 🛠️ Stack Tecnológico y Librerías
- **Lenguaje:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Arquitectura:** MVVM (Model-View-ViewModel) con Coroutines y StateFlow.
- **Editor de Código:** `compose-highlight` (dev.hossain:compose-highlight) para resaltado de sintaxis.
- **Visor de PDF:** `androidx.pdf:pdf-compose` (solo lectura).
- **Navegación:** `androidx.navigation:navigation-compose`.
- **Manejo de Archivos:** Storage Access Framework (SAF) con `ActivityResultContracts.OpenDocument`.

## 📱 Funcionalidades Requeridas
1. **Soporte de Archivos:**
   - **Editable:** HTML, TXT, JSON, CSS, JS.
   - **Solo Lectura (Visor):** PDF, Word (.docx).
2. **Pantalla Principal (Home):** 
   - Botón para cargar archivos locales.
   - Lista de archivos recientes (usando DataStore o Room).
   - Botón para crear un archivo nuevo.
3. **Pantalla de Edición/Visor:**
   - Barra superior con el nombre del archivo.
   - Pestañas (Tabs): **View** (código resaltado), **Edit** (editor de código), **Preview** (renderizado).
   - Para PDF y Word, solo se mostrará la vista de lectura.
4. **Pantalla de Ajustes:**
   - Selección de tema (Claro/Oscuro/Sistema).
   - Sección "About" con enlaces al repositorio de GitHub (Open Source) y opción de donaciones.
   - Sin rastro de "Premium" o compras dentro de la app.

## 🎨 Guías de Diseño (UI/UX)
- Usa colores oscuros profundos (ej. `#121212` para fondo, `#1E1E1E` para tarjetas).
- Acentos en un color verde azulado (teal) o dinámicos usando Material You.
- Tipografía clara, preferiblemente sans-serif para la UI y monoespaciada para el código.
- Botones flotantes inferiores redondeados para acciones rápidas en el editor (Guardar, Compartir, Copiar).
