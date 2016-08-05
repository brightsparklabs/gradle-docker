# gradle-docker

Gradle plugins for working with Docker.

# Build

```shell
./gradlew build

# publish
./gradlew publishPlugins
```

# Docker Image Plugin

This plugin simplifies working with Docker images. It is used to build and
export images so that they can be packaged into releases.

The tag for each image built is dynamically generated using the following
rules:
- The `repository` field of the image tag will be set to the `image name`
  provided in the plugin's configuration block.
- The `tag` (version) field of the image tag will be generated from the git
  tags in the repository. Ideally it will look for the first tag which contains
  the version of the `Dockerfile`. Failing that, it will use `git describe` to
  uniquely identify the version of the file.

When exporting Docker images to file, a image tag file will be generated for
each Docker image. This file will contain the full tag of the image. E.g.

- `brightsparklabs/alpha:1.0.0`

Each image tag file will be named using the format:

- `.VERSION.DOCKER_IMAGE.<image_name>`
    - Where:
        - `image_name` is a filename friendly version of the `image name`
          provided in the plugin's configuration block.

## Usage

```groovy
// file: build.gradle

plugins {
    id 'com.brightsparklabs.gradle.docker.docker-image'
}
```

## Configuration

Use the following configuration block to configure the plugin:

```groovy
// file: build.gradle

dockerImagePluginConfig {
    dockerFiles = [
        'brightsparklabs/alpha': file('src/docker/alpha/Dockerfile'),
        'brightsparklabs/bravo': new File(buildscript.sourceFile.parentFile, 'src/docker/bravo/docker-file'),
    ]
    imageTagDir = new File('src/dist/')
}
```

Where:

- `dockerFiles` is a map of `image_name` to `dockerfile`.
    - An image will be built for each `image_name` specified using the
      associated `dockerfile`.
- `imageTagDir` is the directory in which to store image tag files.

# Tasks

The tasks added by the plugin can be found by running:

```shell
./gradlew tasks
```

# Licenses

Refer to the `LICENSE` file for details.

