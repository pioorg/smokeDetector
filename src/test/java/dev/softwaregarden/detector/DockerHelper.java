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

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.PushResponseItem;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;


public interface DockerHelper {
    static void tagImage(GenericContainer<?> container, DockerImageName dockerImageName) {
        container.getDockerClient()
            .commitCmd(container.getContainerId())
            .withRepository(dockerImageName.getRepository())
            .withTag(dockerImageName.getVersionPart())
            .exec();
    }

    static void pushImage(GenericContainer<?> container, DockerImageName dockerImageName) {
        try {
            container.getDockerClient()
                .pushImageCmd(dockerImageName.getRepository() + ":" + dockerImageName.getVersionPart())
                .exec(new ResultCallback.Adapter<>() {
                    @Override
                    public void onNext(PushResponseItem pri) {
                        if (pri.isErrorIndicated()) {
                            throw new RuntimeException("" + pri.getErrorDetail());
                        }
                    }
                }).awaitCompletion(180, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
