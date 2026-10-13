# TelemetrySystem: refactorización SOLID y pruebas unitarias con Mockito

## 1. El enunciado

Somos desarrolladores en el equipo Ferrari de F1 y hay que escribir **pruebas unitarias con mocks** para la clase `TelemetryDiagnosticControls`.

Esta clase:
1. Se conecta al servidor de telemetría a través de `TelemetryClient` (reintenta hasta 3 veces).
2. Envía un mensaje de diagnóstico (`"AT#UD"`).
3. Recibe la respuesta y la guarda en `diagnosticInfo`.

`TelemetryClient` **simula** un cliente real que se conectaría por TCP/IP. Por dentro usa un `Random`, así que unas veces conecta y otras no.

El enunciado avisa de que **el código incumple uno o más principios SOLID**, y por eso es difícil de testear.

---

## 2. El problema: por qué no se podía testear

Código original:

```java
public class TelemetryDiagnosticControls {
    private final TelemetryClient telemetryClient;

    public TelemetryDiagnosticControls() {
        telemetryClient = new TelemetryClient();   // ← EL PROBLEMA
    }
    ...
}
```

La clase **crea ella misma** su dependencia con `new`. Esto tiene dos consecuencias:

- **No se puede sustituir por un mock.** El test no tiene manera de "meterle" un cliente falso.
- **Los tests no serían deterministas.** El cliente real usa un `Random` para decidir si conecta, así que el mismo test podría pasar unas veces y fallar otras.

Una prueba **unitaria** tiene que probar **solo** la lógica de `TelemetryDiagnosticControls`: los reintentos, la excepción, el envío y la recepción. No debe depender de la red ni del azar.

---

## 3. Los principios SOLID que se incumplían

| Letra | Principio | Qué significa | ¿Se incumplía? |
|---|---|---|---|
| **S** | Single Responsibility (Responsabilidad única) | Una clase debe tener un solo motivo para cambiar | ✅ Sí: `TelemetryClient` gestiona la **conexión** *y* el **envío/recepción de mensajes** |
| O | Open/Closed (Abierto/Cerrado) | Abierta a extensión, cerrada a modificación | Indirectamente: para cambiar el cliente había que tocar el código |
| L | Liskov Substitution (Sustitución de Liskov) | Un subtipo debe poder usarse donde se usa su tipo padre | No aplica aquí |
| **I** | Interface Segregation (Segregación de interfaces) | No obligar a depender de métodos que no usas | ✅ Sí: quien solo quiere enviar/recibir depende también de los métodos de conexión |
| **D** | Dependency Inversion (Inversión de dependencias) | Depender de **abstracciones** (interfaces), no de clases concretas, y no crearlas uno mismo | ✅ **Sí, el principal**: `new TelemetryClient()` dentro del constructor |

**El más importante es el DIP.** Es el que impedía usar mocks.

---

## 4. La solución: refactorización

### 4.1 Separar responsabilidades en dos interfaces (S + I)

```java
// Responsabilidad: gestionar la conexión
public interface TelemetryConnection {
    void connect(String telemetryServerConnectionString);
    void disconnect();
    boolean getOnlineStatus();
}

// Responsabilidad: enviar y recibir mensajes
public interface TelemetryChannel {
    void send(String message);
    String receive();
}
```

### 4.2 `TelemetryClient` implementa ambas

```java
public class TelemetryClient implements TelemetryConnection, TelemetryChannel {
    @Override public void connect(String s) { ... }   // el código de dentro NO cambia
    @Override public void disconnect() { ... }
    @Override public boolean getOnlineStatus() { ... }
    @Override public void send(String message) { ... }
    @Override public String receive() { ... }
}
```

Por dentro hace **exactamente lo mismo que antes**. Lo único que cambia es que ahora "cumple" las dos interfaces.

### 4.3 Inyección de dependencias por constructor (D)

```java
public class TelemetryDiagnosticControls {
    public static final String DIAGNOSTIC_CHANNEL_CONNECTION_STRING = "*111#";
    private static final int MAX_CONNECTION_RETRIES = 3;

    private final TelemetryConnection telemetryConnection;
    private final TelemetryChannel telemetryChannel;
    private String diagnosticInfo = "";

    // Constructor original: se mantiene para no romper el código existente
    public TelemetryDiagnosticControls() {
        this(new TelemetryClient());
    }

    private TelemetryDiagnosticControls(TelemetryClient client) {
        this(client, client);   // el mismo objeto hace de conexión y de canal
    }

    // NUEVO: recibe las dependencias desde fuera → aquí metemos los mocks
    public TelemetryDiagnosticControls(TelemetryConnection connection, TelemetryChannel channel) {
        this.telemetryConnection = connection;
        this.telemetryChannel = channel;
    }

    public void checkTransmission() throws Exception {
        diagnosticInfo = "";
        telemetryConnection.disconnect();

        int retryLeft = MAX_CONNECTION_RETRIES;
        while (!telemetryConnection.getOnlineStatus() && retryLeft > 0) {
            telemetryConnection.connect(DIAGNOSTIC_CHANNEL_CONNECTION_STRING);
            retryLeft -= 1;
        }

        if (!telemetryConnection.getOnlineStatus()) {
            throw new Exception("Unable to connect.");
        }

        telemetryChannel.send(TelemetryClient.DIAGNOSTIC_MESSAGE);
        diagnosticInfo = telemetryChannel.receive();
    }
}
```

Otras mejoras pequeñas de paso:
- `== false` cambiado por `!` (más limpio).
- Los "números mágicos" (`"*111#"` y `3`) ahora son constantes con nombre.

> **Idea clave:** antes la clase decidía qué cliente usar. Ahora **quien la crea** le pasa lo que necesita. En producción se le pasa el cliente real y en los tests un mock.

---

## 5. Mockito en 5 minutos

Un **mock** es un objeto falso que:
- **Devuelve lo que tú le digas** (stubbing).
- **Registra cómo se le ha llamado**, para que luego puedas comprobarlo (verificación).

| Herramienta | Para qué sirve | Ejemplo |
|---|---|---|
| `@ExtendWith(MockitoExtension.class)` | Activa Mockito en la clase de test (JUnit 5) | encima de la clase |
| `@Mock` | Crea un objeto falso de esa interfaz/clase | `@Mock TelemetryConnection connection;` |
| `when(...).thenReturn(...)` | Decide qué devuelve un método | `when(channel.receive()).thenReturn("OK");` |
| `thenReturn(a, b, c)` | Devuelve **un valor distinto en cada llamada** | `thenReturn(false, true)` → 1ª false, 2ª true… |
| `verify(mock).metodo(args)` | Comprueba que se llamó **una vez** con esos argumentos | `verify(channel).send("AT#UD");` |
| `verify(mock, times(n))` | Comprueba que se llamó exactamente `n` veces | `verify(connection, times(3)).connect(anyString());` |
| `verify(mock, never())` | Comprueba que **no** se llamó nunca | `verify(channel, never()).send(anyString());` |
| `anyString()` | "Cualquier String" (matcher) | `connect(anyString())` |
| `InOrder` | Comprueba el **orden** de las llamadas | `order.verify(connection).disconnect();` |

### El truco de los reintentos

`getOnlineStatus()` se llama varias veces dentro de `checkTransmission()`:

```
disconnect()
while (!getOnlineStatus() ...)   ← llamada 1
    connect()
while (!getOnlineStatus() ...)   ← llamada 2
    ...
if (!getOnlineStatus())          ← última llamada
```

Con `thenReturn(false, false, false, true)`:
- Llamadas 1, 2 y 3 → `false` → hace 3 `connect()`
- Llamada 4 (el `if`) → `true` → sigue y envía el mensaje

Así simulamos **"conecta al tercer intento"** sin depender del `Random`.

---

## 6. Los tests

Estructura **AAA** en cada test:
- **Arrange** (preparar): configurar los mocks.
- **Act** (actuar): llamar a `checkTransmission()`.
- **Assert** (comprobar): `assertEquals`, `assertThrows` y `verify`.

```java
@ExtendWith(MockitoExtension.class)
public class TelemetryDiagnosticControlsTest {

    private static final String DIAGNOSTIC_RESPONSE = "LAST TX rate................ 100 MBPS";

    @Mock private TelemetryConnection connection;
    @Mock private TelemetryChannel channel;
    private TelemetryDiagnosticControls controls;

    @BeforeEach
    void setUp() {
        controls = new TelemetryDiagnosticControls(connection, channel);  // ← inyectamos mocks
    }
    ...
}
```

| # | Test | Escenario | Qué comprueba |
|---|---|---|---|
| 1 | `CheckTransmission_should_send_a_diagnostic_message_and_receive_a_status_message_response` | Conecta a la primera | Envía `"AT#UD"` y `getDiagnosticInfo()` devuelve lo que dio `receive()` |
| 2 | `checkTransmission_connectsWithDiagnosticChannelConnectionString` | Conecta a la primera | `connect` recibe exactamente `"*111#"` |
| 3 | `checkTransmission_callsCollaboratorsInTheRightOrder` | Conecta a la primera | Orden: `disconnect → connect → send → receive` |
| 4 | `checkTransmission_retriesAndSucceedsOnThirdAttempt` | Falla 2 veces y conecta a la 3ª | `connect` se llama 3 veces y el diagnóstico se recibe |
| 5 | `checkTransmission_throwsAfterThreeFailedAttempts` | Nunca conecta | Lanza `Exception("Unable to connect.")`, 3 intentos y **nunca** `send`/`receive` |
| 6 | `checkTransmission_clearsPreviousDiagnosticInfoWhenConnectionFails` | Había diagnóstico previo y falla | `diagnosticInfo` queda vacío `""` |

### Ejemplo comentado: test 5 (el de la excepción)

```java
@Test
void checkTransmission_throwsAfterThreeFailedAttempts() {
    // Arrange: getOnlineStatus() SIEMPRE devuelve false
    when(connection.getOnlineStatus()).thenReturn(false);

    // Act + Assert: debe lanzar excepción
    Exception ex = assertThrows(Exception.class, () -> controls.checkTransmission());

    // Assert
    assertEquals("Unable to connect.", ex.getMessage());
    verify(connection, times(3)).connect(anyString());  // reintentó 3 veces
    verify(channel, never()).send(anyString());         // no envió nada
    verify(channel, never()).receive();                 // no recibió nada
}
```

---

## 7. Ejecución en IntelliJ

1. `File → Open` sobre la carpeta `TelemetrySystem` (la del `pom.xml`).
2. Maven descarga JUnit 5 y Mockito.
3. Clic derecho en `TelemetryDiagnosticControlsTest` → **Run**.

**Resultado: ✅ 6 tests passed. `Process finished with exit code 0`.**

### Sobre los WARNING que salen en la consola

**No son errores.** Son avisos:

- *"Mockito is currently self-attaching..."* y *"A Java agent has been loaded dynamically..."*: para crear mocks, Mockito carga un "agente" en la JVM mientras se ejecuta. Java 21 o superior avisa de que en el futuro esto no estará permitido por defecto. Hoy funciona bien.
- *"Sharing is only supported for boot loader classes..."*: es una optimización interna de la JVM que se desactiva porque IntelliJ añade sus librerías al arrancar. Da igual.

---

## 8. Estructura final del proyecto

```
TelemetrySystem/
├── pom.xml                                   (JUnit 5.11.4 + Mockito 5.15.2, Java 21)
└── src/
    ├── main/java/tddmicroexercises/telemetrysystem/
    │   ├── TelemetryConnection.java          ← NUEVA (interfaz)
    │   ├── TelemetryChannel.java             ← NUEVA (interfaz)
    │   ├── TelemetryClient.java              ← ahora implementa las 2 interfaces
    │   └── TelemetryDiagnosticControls.java  ← inyección de dependencias
    └── test/java/tddmicroexercises/telemetrysystem/
        └── TelemetryDiagnosticControlsTest.java  ← 6 tests con Mockito
```

---

## 9. Chuleta para explicarlo en clase

1. **¿Qué fallaba?** La clase hacía `new TelemetryClient()` dentro del constructor. Eso viola el **DIP**: depende de una clase concreta y la crea ella misma, así que no se puede sustituir por un mock. Además `TelemetryClient` tenía dos responsabilidades (**SRP**).
2. **¿Qué hice?** Extraje dos interfaces (`TelemetryConnection` y `TelemetryChannel`) y añadí un constructor que las recibe desde fuera (**inyección de dependencias**).
3. **¿Cómo lo probé?** Con Mockito creo mocks de las dos interfaces. Con `when/thenReturn` controlo si conecta o no, y con `verify` compruebo qué métodos se llamaron, cuántas veces y en qué orden.
4. **¿Por qué es mejor?** Los tests son **rápidos**, **deterministas** (no dependen del `Random` ni de la red) y prueban **solo** la lógica de `TelemetryDiagnosticControls`.

### Para practicar
- Borra el test 5 e intenta escribirlo tú desde cero.
- Añade un test nuevo: *"conecta al segundo intento → `connect` se llama 2 veces"*. Pista: `thenReturn(false, false, true)`.
- Pregunta para pensar: ¿por qué en el test 5 basta con `thenReturn(false)` y no hace falta poner `false` 4 veces? (Respuesta: cuando se acaban los valores, Mockito repite el último.)
