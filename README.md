# Ampliación de Entornos de Desarrollo · Testing (2º DAM)

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat&logo=openjdk&logoColor=white)
![JUnit5](https://img.shields.io/badge/JUnit_5-25A162?style=flat&logo=junit5&logoColor=white)
[![Tests](https://github.com/MarioCarcalete/Ampliacion-EntornosIDE-Testing/actions/workflows/tests.yml/badge.svg)](https://github.com/MarioCarcalete/Ampliacion-EntornosIDE-Testing/actions/workflows/tests.yml)
![Maven](https://img.shields.io/badge/Maven-C71A36?style=flat&logo=apachemaven&logoColor=white)

Prácticas de **pruebas unitarias**, **mocks** y **principios SOLID** de la asignatura de Ampliación de Entornos de Desarrollo del 2º curso de DAM.

## 🧪 Proyectos

### [TelemetrySystem](TelemetrySystem): refactorización SOLID y tests con Mockito

La clase `TelemetryDiagnosticControls` se conecta a un servidor de telemetría (de un equipo de F1), envía un mensaje de diagnóstico y guarda la respuesta. El código original creaba su dependencia con `new`, así que era imposible de testear sin depender de la red o del azar.

Lo que hice:

- **Detectar qué principios SOLID se incumplían** (responsabilidad única, segregación de interfaces e inversión de dependencias).
- **Refactorizar**: separar el cliente en dos interfaces e inyectar la dependencia por constructor.
- **Escribir 6 tests unitarios con JUnit 5 y Mockito** (se ejecutan solos en GitHub Actions en cada push) que comprueban los reintentos de conexión, la excepción cuando no conecta y el envío y la recepción del mensaje.

📄 La explicación completa, paso a paso, está en [TelemetrySystem-explicacion.md](TelemetrySystem/TelemetrySystem-explicacion.md).

## ▶️ Cómo ejecutar los tests

```bash
git clone https://github.com/MarioCarcalete/Ampliacion-EntornosIDE-Testing.git
cd Ampliacion-EntornosIDE-Testing/TelemetrySystem/TelemetrySystem
mvn test
```

También se puede abrir la carpeta `TelemetrySystem/TelemetrySystem` en IntelliJ IDEA o Eclipse como proyecto Maven y lanzar los tests desde el IDE.

---

Hecho por [Mario Carcalete](https://github.com/MarioCarcalete) durante el 2º curso de DAM.
