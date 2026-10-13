package tddmicroexercises.telemetrysystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TelemetryDiagnosticControlsTest
{
    private static final String DIAGNOSTIC_RESPONSE = "LAST TX rate................ 100 MBPS";

    @Mock
    private TelemetryConnection connection;

    @Mock
    private TelemetryChannel channel;

    private TelemetryDiagnosticControls controls;

    @BeforeEach
    void setUp()
    {
        controls = new TelemetryDiagnosticControls(connection, channel);
    }

    @Test
    @DisplayName("Envía el mensaje de diagnóstico y guarda la respuesta recibida")
    public void CheckTransmission_should_send_a_diagnostic_message_and_receive_a_status_message_response() throws Exception
    {
        // Arrange: offline tras el disconnect, online tras el primer connect
        when(connection.getOnlineStatus()).thenReturn(false, true);
        when(channel.receive()).thenReturn(DIAGNOSTIC_RESPONSE);

        // Act
        controls.checkTransmission();

        // Assert
        verify(channel).send(TelemetryClient.DIAGNOSTIC_MESSAGE);
        assertEquals(DIAGNOSTIC_RESPONSE, controls.getDiagnosticInfo());
    }

    @Test
    @DisplayName("Conecta usando la cadena de conexión del canal de diagnóstico")
    void checkTransmission_connectsWithDiagnosticChannelConnectionString() throws Exception
    {
        when(connection.getOnlineStatus()).thenReturn(false, true);

        controls.checkTransmission();

        verify(connection).connect("*111#");
    }

    @Test
    @DisplayName("Desconecta, conecta, envía y recibe en ese orden")
    void checkTransmission_callsCollaboratorsInTheRightOrder() throws Exception
    {
        when(connection.getOnlineStatus()).thenReturn(false, true);

        controls.checkTransmission();

        InOrder order = inOrder(connection, channel);
        order.verify(connection).disconnect();
        order.verify(connection).connect(anyString());
        order.verify(channel).send(TelemetryClient.DIAGNOSTIC_MESSAGE);
        order.verify(channel).receive();
    }

    @Test
    @DisplayName("Reintenta la conexión y funciona si conecta al tercer intento")
    void checkTransmission_retriesAndSucceedsOnThirdAttempt() throws Exception
    {
        // 3 comprobaciones del bucle en false, la última en true
        when(connection.getOnlineStatus()).thenReturn(false, false, false, true);
        when(channel.receive()).thenReturn(DIAGNOSTIC_RESPONSE);

        controls.checkTransmission();

        verify(connection, times(3)).connect(TelemetryDiagnosticControls.DIAGNOSTIC_CHANNEL_CONNECTION_STRING);
        assertEquals(DIAGNOSTIC_RESPONSE, controls.getDiagnosticInfo());
    }

    @Test
    @DisplayName("Lanza excepción tras 3 intentos fallidos y no envía nada")
    void checkTransmission_throwsAfterThreeFailedAttempts()
    {
        when(connection.getOnlineStatus()).thenReturn(false);

        Exception ex = assertThrows(Exception.class, () -> controls.checkTransmission());

        assertEquals("Unable to connect.", ex.getMessage());
        verify(connection, times(3)).connect(anyString());
        verify(channel, never()).send(anyString());
        verify(channel, never()).receive();
    }

    @Test
    @DisplayName("Borra el diagnóstico anterior aunque falle la conexión")
    void checkTransmission_clearsPreviousDiagnosticInfoWhenConnectionFails()
    {
        controls.setDiagnosticInfo("diagnóstico antiguo");
        when(connection.getOnlineStatus()).thenReturn(false);

        assertThrows(Exception.class, () -> controls.checkTransmission());

        assertEquals("", controls.getDiagnosticInfo());
    }
}
