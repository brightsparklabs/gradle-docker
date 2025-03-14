# gradle-docker

![Build Status](https://github.com/brightsparklabs/gradle-docker/actions/workflows/gradle-plugins.yml/badge.svg)
[![Gradle Plugin](https://img.shields.io/gradle-plugin-portal/v/com.brightsparklabs.gradle.docker-image)](https://plugins.gradle.org/plugin/com.brightsparklabs.gradle.docker-image)

Gradle plugins for working with Docker.

## Compatibility

| Plugin Version | Gradle Version | Java Version |
| -------------- | -------------- | ------------ |
| 3.0.0          | 7.x.y          | 11           |
| 4.0.0          | 7.x.y          | 17           |
| 5.0.0          | 8.x.y          | 21           |

## Build

```shell
./gradlew build

# publish
./gradlew publishPlugins
```

## Docker Image Plugin

This plugin simplifies working with Docker images. It is used to build and
export images so that they can be packaged into releases.

### Usage

```groovy
// file: build.gradle

plugins {
    id 'com.brightsparklabs.gradle.docker-image'
}
```

### Tasks

The plugin adds the following gradle tasks:

#### buildDockerImages

Builds and tags the images as specified in the configuration block.

The following command line arguments can be specified when running this task:

- `includeImages`: Includes only the specified images in the build. E.g.

        gradle buildDockerImages --includeImages brightsparklabs/alpha,brightsparklabs/bravo

- `excludeImages`: Excludes the specified images from the build. E.g.

        gradle buildDockerImages --excludeImages brightsparklabs/alpha,brightsparklabs/bravo

The following tags are automatically added in addition to any specified custom
tags:

- `latest`.
- The version property from the gradle build script.
- The value from running `git describe --dirty` or `0.0.0-UNKNOWN` if there are
  no git tags.
- The latest git commit id of the folder containing the Dockerfile or
  `UNKNOWN-COMMIT` if there is not commit id on the folder (i.e. folder is not
  checked into git).
- The UTC timestamp of the build in ISO8601 format (without colons).

The following `build-args` are automatically passed to the `docker build`
command:

- `APP_VERSION`: the gradle property `project.version`.
- `BUILD_DATE`: the UTC timestamp of the build in ISO8601 format.
- `VCS_REF`: the latest git commit id of the folder containing the Dockerfile.

These build-args can be utilised within the `Dockerfile` for labels,
environment variables, etc:

    ARG APP_VERSION
    ARG BUILD_DATE
    ARG VCS_REF
    LABEL org.label-schema.name="docker-gradle" \
          org.label-schema.description="Image used to run gradle-docker" \
          org.label-schema.vendor="brightSPARK Labs" \
          org.label-schema.schema-version="1.0.0-rc1" \
          org.label-schema.vcs-url="https://github.com/brightsparklabs/gradle-docker/" \
          org.label-schema.vcs-ref=${VCS_REF} \
          org.label-schema.build-date=${BUILD_DATE} \
          org.label-schema.version=${APP_VERSION}
    ENV META_BUILD_DATE=${BUILD_DATE}
    ENV META_VCS_REF=${VCS_REF}
    ENV APP_VERSION=${APP_VERSION}

An image tag file will be generated for each Docker image. This file will
contain the full tag of the image (based off the version property from the
gradle build script). E.g.

- `brightsparklabs/alpha:1.0.0`

Each image tag file will be named using the format:

- `VERSION.DOCKER_IMAGE.<repositoryName>`
  - Where:
    - `repositoryName` is a filename friendly version of the `name`
      provided in the plugin's configuration block.

#### saveDockerImages

Saves each image to a file which can be loaded by `docker load`.

#### publishDockerImages

Publishes each image to the relevant Docker Registry. The credentials for
logging into the Registry must have already been configured via `docker login`.

### Configuration

Use the following configuration block to configure the plugin:

```groovy
// file: build.gradle

project.version = 'v1.2.0-RC'

dockerImagePluginConfig {
    // dockerfiles to operate on
    dockerFileDefinitions = [
        [
            'dockerfile' : file('src/alpha/Dockerfile'),
            'name'       : 'brightsparklabs/alpha',
            'tags'       : ['awesome-ant', 'testing']
        ],
        [
            'dockerfile' : file('src/bravo/bravo.Dockerfile'),
            'repository' : 'brightsparklabs/bravo',
            'target'     : 'dev-mode',
            'buildArgs'  : ['--compress', '--quiet'],
            'contextDir' : file('./src')
        ],
    ]
    imagesDir = new File('build/images/')
    imageTagDir = new File('build/imageTags/')
    continueOnFailure = true
    deleteOlderImages = true
    removeDanglingImages = true
    privateDockerServer = 'docker.brightsparklabs.com'
    privateDockerUsername =  project.hasProperty('dockerUsername') ? dockerUsername : System.env.DOCKER_USERNAME
    privateDockerPassword =  project.hasProperty('dockerPassword') ? dockerPassword : System.env.DOCKER_PASSWORD
}
```

Where:

- `dockerFileDefinitions`: [`Map[]`] each map has the following keys:
  - `dockerfile`: [`File`] dockerfile to build
  - `name`: [`String`] repository name for the built docker image
  - `repository`: [`String`] repository name for the built docker image
    [**DEPRECATED** use `name` instead]
  - `tags`: [String[]`] custom tags for the built docker image [optional]
  - `buildArgs`: [String[]`] additional arguments to the `docker build`
    command [optional]
  - `target`: [`String`] target to build (only applies to multi-stage builds)
    [optional]
- `imageTagDir`: [`File`] the directory in which to store images
  [optional, default: `build/images`]
- `imageTagDir`: [`File`] the directory in which to store image tag files
  [optional, default: `build/imageTags`]
- `continueOnFailure`: [`Boolean`] set to true if the build should continue
  even if a docker image build fails [optional, default: `false`]
- `deleteOlderImages`: [`Boolean`] set to true if older images of the same name
  should be deleted after each new image is built [optional, default: `false`]
- `removeDanglingImages`: [`Boolean`] set to true if the build should remove
  dangling images after all images are built [optional, default: `false`]
- `privateDockerServer`: [`String`] the private Docker server to log into when
  building images that use private images as a base [optional]
- `privateDockerUsername`: [`String`] the private Docker server username to use
  when logging into the specified private Docker server [optional]
- `privateDockerPassword`: [`String`] the private Docker server password to use
  when logging into the specified private Docker server [optional]

### Example

With the above `build.gradle` file and a repository structure as follows:

```
my-project/ (git tag: 1.2.0)
- src/
  - alpha/ (latest commit id: 62d1a77)
    - Dockerfile
  - bravo/ (latest commit id: e8b158f)
    - bravo.Dockerfile
```

Running `gradle saveDockerImages` will:

- Build the following docker images:
  - brightsparklabs/alpha:latest
  - brightsparklabs/alpha:v1.2.0-RC (from project.version)
  - brightsparklabs/alpha:1.2.0 (from git repo tag)
  - brightsparklabs/alpha:g62d1a77 (from folder)
  - brightsparklabs/alpha:awesome-ant (custom tag)
  - brightsparklabs/alpha:testing (custom tag)
  - brightsparklabs/bravo:latest
  - brightsparklabs/alpha:v1.2.0-RC (from project.version)
  - brightsparklabs/bravo:1.2.0 (from git repo tag)
  - brightsparklabs/bravo:ge8b158f (from folder)
- Save the image tags to the following files:
  - build/imageTags/VERSION.DOCKER-IMAGE.brightsparklabs-alpha
  - build/imageTags/VERSION.DOCKER-IMAGE.brightsparklabs-bravo
- Create the following docker image files:
  - build/images/docker-image-brightsparklabs-alpha:v1.2.0-RC
  - build/images/docker-image-brightsparklabs-bravo:v1.2.0-RC

## Licenses

Refer to the `LICENSE` file for details.
