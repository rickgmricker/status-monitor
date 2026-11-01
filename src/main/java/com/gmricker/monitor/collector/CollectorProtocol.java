package com.gmricker.monitor.collector;

import static com.gmricker.monitor.i18n.I18n.tr;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class CollectorProtocol {
    private CollectorProtocol() {}
    static byte[] captureRequest(long pid) {
        if (pid <= 0 || pid > 0xffff_ffffL) throw new IllegalArgumentException(tr("PID inválido."));
        return ("CAPTURE 1 " + pid + "\n").getBytes(StandardCharsets.US_ASCII);
    }
    static void expectReady(InputStream input) throws IOException {
        var response = new StringBuilder();
        for (int index = 0; index < 512; index++) {
            int character = input.read();
            if (character < 0) throw new IOException(tr("O serviço encerrou a conexão antes de iniciar a coleta."));
            if (character == '\n') {
                if (response.toString().equals("OK 1")) return;
                throw new IOException(response.toString().startsWith("ERR ") ? tr(response.substring(4))
                        : tr("Protocolo incompatível com o serviço coletor."));
            }
            response.append((char) character);
        }
        throw new IOException(tr("Resposta inválida do serviço coletor."));
    }
}
