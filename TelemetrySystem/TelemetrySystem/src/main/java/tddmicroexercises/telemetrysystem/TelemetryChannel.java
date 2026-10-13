package tddmicroexercises.telemetrysystem;

/**
 * Responsabilidad: enviar y recibir mensajes por el canal de telemetría.
 */
public interface TelemetryChannel
{
    void send(String message);

    String receive();
}
