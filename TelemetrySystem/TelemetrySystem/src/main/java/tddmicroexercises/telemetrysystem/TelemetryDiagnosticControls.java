package tddmicroexercises.telemetrysystem;

public class TelemetryDiagnosticControls
{
    public static final String DIAGNOSTIC_CHANNEL_CONNECTION_STRING = "*111#";
    private static final int MAX_CONNECTION_RETRIES = 3;

    private final TelemetryConnection telemetryConnection;
    private final TelemetryChannel telemetryChannel;
    private String diagnosticInfo = "";

    /**
     * Constructor original: se mantiene para no romper el código que ya lo usa.
     * Usa un único TelemetryClient real como conexión y como canal.
     */
    public TelemetryDiagnosticControls()
    {
        this(new TelemetryClient());
    }

    private TelemetryDiagnosticControls(TelemetryClient client)
    {
        this(client, client);
    }

    /**
     * Constructor con inyección de dependencias (DIP): recibe abstracciones,
     * lo que permite sustituirlas por mocks en las pruebas.
     */
    public TelemetryDiagnosticControls(TelemetryConnection telemetryConnection, TelemetryChannel telemetryChannel)
    {
        this.telemetryConnection = telemetryConnection;
        this.telemetryChannel = telemetryChannel;
    }

    public String getDiagnosticInfo()
    {
        return diagnosticInfo;
    }

    public void setDiagnosticInfo(String diagnosticInfo)
    {
        this.diagnosticInfo = diagnosticInfo;
    }

    public void checkTransmission() throws Exception
    {
        diagnosticInfo = "";

        telemetryConnection.disconnect();

        int retryLeft = MAX_CONNECTION_RETRIES;
        while (!telemetryConnection.getOnlineStatus() && retryLeft > 0)
        {
            telemetryConnection.connect(DIAGNOSTIC_CHANNEL_CONNECTION_STRING);
            retryLeft -= 1;
        }

        if (!telemetryConnection.getOnlineStatus())
        {
            throw new Exception("Unable to connect.");
        }

        telemetryChannel.send(TelemetryClient.DIAGNOSTIC_MESSAGE);
        diagnosticInfo = telemetryChannel.receive();
    }
}
