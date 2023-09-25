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

import static dev.softwaregarden.detector.SneakyRunner.throwingRun;
import static java.net.http.HttpRequest.BodyPublishers.ofString;
import static java.time.Duration.ofSeconds;
import static java.time.Instant.parse;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.shaded.org.awaitility.core.ThrowingRunnable;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class DetectorTest {

    private final static MountableFile jar = MountableFile.forHostPath(Paths.get("target/detector-1.0-SNAPSHOT.jar"));

    private GenericContainer<?> createContainer() {
        //noinspection resource
        return new GenericContainer<>("openjdk:21-slim")
            .withCopyFileToContainer(jar, "/tmp/detector.jar")
            .withExposedPorts(7890)
            .withEnv("detector.port", "7890")
            .withCommand("java", "-jar", "/tmp/detector.jar")
            .waitingFor(Wait.forLogMessage("^started=.*", 1));
    }

    @Test
//    @Disabled
    void shouldStartInLessThan4Seconds() {

        try (var container = createContainer()) {

            container.start();

            var events = container.getLogs().lines()
                .filter(s -> s.startsWith("start"))
                .collect(toMap(s -> s.split("=")[0], s -> parse(s.split("=")[1])));

            assertThat(events).containsKeys("starting", "started");
            assertThat(container.getLogs()).contains("checking=");
            var startedDeadline = events.get("starting").plusSeconds(4);
            assertThat(events.get("started")).isBeforeOrEqualTo(startedDeadline);
        }
    }

    @Test
//    @Disabled
    void shouldCheckAtLeastOncePerSecond() {
        try (var container = createContainer()) {

            container.start();

            var checkEvents = container.getLogs().lines()
                .filter(s -> s.startsWith("checking="))
                .toList();

            Awaitility.given()
                .between(ofSeconds(3), ofSeconds(4))
                .pollDelay(ofSeconds(3))
                .until(
                    () -> container.getLogs().lines().filter(s -> s.startsWith("checking=")).toList(),
                    before -> before.size() - checkEvents.size() >= 3
                );
        }
    }


    @Test
//    @Disabled
    void shouldAlarmAbove400PPM() throws InterruptedException {

        try (var container = createContainer()) {

            container.start();

            var client = HttpClient.newHttpClient();
            var uri = URI.create("http://%s:%d/".formatted(container.getHost(), container.getFirstMappedPort()));
            var o2Req = HttpRequest.newBuilder(uri).POST(ofString("O2")).build();
            var coReq = HttpRequest.newBuilder(uri).POST(ofString("CO")).build();

            try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < 1_000; i++) {
                    exec.execute(throwingRun(() -> client.send(o2Req, BodyHandlers.discarding())));
                }

                exec.execute(throwingRun(() -> client.send(coReq, BodyHandlers.discarding())));

                exec.shutdown();
                assertThat(exec.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }

            Awaitility.given()
                .atMost(ofSeconds(1))
                .until(
                    container::getLogs,
                    logs -> logs.contains("ALARM")
                );
//            var checkRequest = HttpRequest.newBuilder(uri).GET().build();
//            var response = client.send(checkRequest, BodyHandlers.ofString());
//            System.out.println(response.body());
        }
    }

}

interface SneakyRunner {
    static Runnable throwingRun(ThrowingRunnable tr) {
        return () -> {
            try {
                tr.run();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };
    }
}
