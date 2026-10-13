package tddmicroexercises.telemetrysystem;

/**
 * Responsabilidad: gestionar la conexión con el servidor de telemetría.
 */
public interface TelemetryConnection
{
    void connect(String telemetryServerConnectionString);

    void disconnect();

    boolean getOnlineStatus();
}
