package com.desktoppet;

import com.desktoppet.backend.BackendHttpServer;
import com.desktoppet.backend.BackendServices;
import com.desktoppet.config.AppConfig;

public final class BackendApplication {
    private BackendApplication() {
    }

    public static void main(String[] args) throws Exception {
        BackendServices services = BackendServices.create();
        AppConfig config = services.config();
        String host = config.serverHost();
        int port = config.serverPort();
        BackendHttpServer server = new BackendHttpServer(services, host, port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            services.close();
        }, "desktop-pet-backend-shutdown"));
        server.start();
        System.out.println("Desktop Pet backend listening on http://" + host + ":" + port);
    }
}
