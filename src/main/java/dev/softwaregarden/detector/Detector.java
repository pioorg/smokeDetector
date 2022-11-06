/*
 *  Copyright (C) 2022 Piotr Przybył
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package dev.softwaregarden.detector;

import com.sun.net.httpserver.HttpServer;
import static java.time.Duration.ofSeconds;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Detector {

    private final BigDecimal alarmLevel = new BigDecimal("0.0004");
    private final AtomicLong coParticlesDetected = new AtomicLong();
    private final AtomicLong otherParticlesDetected = new AtomicLong();
    private final HttpServer server;

    public static void main(String[] args) throws IOException {
        System.out.println("starting=" + Instant.now());
        int port = Integer.parseInt(System.getenv("detector.port"));

        var detector = new Detector(port);
        detector.startOperating();
        System.out.println("started=" + Instant.now());
    }

    public Detector(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 1_000_000);
        server.createContext("/", exchange -> {
            var response = "";
            switch (exchange.getRequestMethod()) {
                case "POST" -> captureParticle(new String(exchange.getRequestBody().readAllBytes()));
                case "GET" -> response = prepareReport();
                case default, null -> {
                    exchange.sendResponseHeaders(405, 0);
                    exchange.close();
                    return;
                }
            }

            var responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    private void startOperating() {
        var buzzerExecutor = Executors.newScheduledThreadPool(5);
        buzzerExecutor.scheduleAtFixedRate(this::performCheck, 1, 1, TimeUnit.SECONDS);
        try {
            Thread.sleep(ofSeconds(3));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        server.start();
    }

    private void performCheck() {
        System.out.println("checking=" + Instant.now());
        // BAD CODE AHEAD! DO NOT DO YOUR PROD SYSTEM LIKE THIS! DEMO ONLY!
        var co = BigDecimal.valueOf(coParticlesDetected.get());
        var all = co.add(BigDecimal.valueOf(otherParticlesDetected.get()));
        if (co.divide(all, RoundingMode.HALF_EVEN).compareTo(alarmLevel) > 0) {
            System.out.println("ALARM");
        }
    }

    private String prepareReport() {
        // BAD CODE AHEAD! DO NOT DO YOUR PROD SYSTEM LIKE THIS! DEMO ONLY!
        return """
            {
              "CO" : %d,
              "other" : %d
            }""".formatted(coParticlesDetected.get(), otherParticlesDetected.get());
    }

    private void captureParticle(String particle) {
        if ("CO".equalsIgnoreCase(particle)) {
            coParticlesDetected.addAndGet(1);
        } else {
            otherParticlesDetected.addAndGet(1);
        }
    }
}
