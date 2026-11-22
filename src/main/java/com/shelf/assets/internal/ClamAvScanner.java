package com.shelf.assets.internal;

import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
class ClamAvScanner implements MalwareScanner {
    private final AssetProperties properties;

    ClamAvScanner(AssetProperties properties) { this.properties = properties; }

    @Override
    public boolean clean(Path file) throws IOException {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(properties.clamavHost(), properties.clamavPort()), 3000);
            socket.setSoTimeout(20000);
            var output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            try (var fileStream = Files.newInputStream(file)) {
                var chunk = new byte[8192];
                for (int size; (size = fileStream.read(chunk)) != -1;) {
                    output.writeInt(size);
                    output.write(chunk, 0, size);
                }
            }
            output.writeInt(0);
            output.flush();
            var input = socket.getInputStream();
            var result = new java.io.ByteArrayOutputStream();
            for (int current; (current = input.read()) > 0 && current != '\n';) {
                if (result.size() == 1024) throw new IOException("Invalid scanner reply");
                result.write(current);
            }
            var status = result.toString(StandardCharsets.US_ASCII);
            if (status.equals("stream: OK")) return true;
            if (status.endsWith(" FOUND")) return false;
            throw new IOException("Malware scan did not complete");
        }
    }
}
