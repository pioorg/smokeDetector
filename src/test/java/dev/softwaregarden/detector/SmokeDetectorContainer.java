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

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class SmokeDetectorContainer extends GenericContainer<SmokeDetectorContainer> {

    private static final int DETECTOR_PORT = 7890;

    public SmokeDetectorContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        withExposedPorts(DETECTOR_PORT);
        withEnv("detector.port", "" + DETECTOR_PORT);
        withCommand("/tmp/detector");
        waitingFor(Wait.forLogMessage("^started=.*", 1));
    }
}
