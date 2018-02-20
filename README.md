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
  tag using `git describe --dirty` with the letter `g` prepended (for git).
- An additional tag for the image is added corresponding to the git commit id
  of the folder containing the Dockerfile. This is also prepended with `g` (for
  git).
- Finally a tag named `latest` is also added.

When exporting Docker images to file, an image tag file will be generated for
each Docker image. This file will contain the full tag of the image (based off
`git describe`). E.g.

- `brightsparklabs/alpha:g1.0.0`

Each image tag file will be named using the format:

- `VERSION.DOCKER_IMAGE.<image_name>`
    - Where:
        - `image_name` is a filename friendly version of the `image name`
          provided in the plugin's configuration block.

## Usage

```groovy
// file: build.gradle

plugins {
    id 'com.brightsparklabs.gradle.docker-image'
}
```

## Configuration

Use the following configuration block to configure the plugin:

```groovy
// file: build.gradle

dockerImagePluginConfig {
    dockerFileDefinitions = [
        [
            'dockerfile' : file('src/alpha/Dockerfile'),
            'repository' : 'brightsparklabs/alpha',
            'tags'       : ['awesome-ant', 'testing']
        ],
        [
            'dockerfile' : file('src/bravo/Dockerfile'),
            'repository' : 'brightsparklabs/bravo',
        ],
    ]
    imageTagDir = new File('build/dist/imageTags/')
    continueOnFailure = true
}
```

Where:

- `dockerFileDefinitions`: [`Map[]`] each map has the following keys:
    - `dockerfile`: [`File`] dockerfile to build
    - `repository`: [`String`] repository name for the built docker image
    - `tags`: [String[]`] custom tags for the built docker image (optional)
- `imageTagDir`: [`File`] the directory in which to store image tag files
  (optional, default is `build/imageTags`)
- `continueOnFailure`: [`Boolean`] set to true if the build should continue
  even if a docker image build fails (optional, default is `false`)

# Tasks

The tasks added by the plugin can be found by running:

```shell
./gradlew tasks
```

## Example

With the above `build.gradle` file and a repository structure as follows:

```
my-project/ (git tag: 1.2.0)
- src/
  - alpha/ (latest commit id: 62d1a77)
    - Dockerfile
  - bravo/ (latest commit id: e8b158f)
    - Dockerfile
```

Running `gradle saveDockerImages` will:

- Build the following docker images:
    - brightsparklabs/alpha:latest
    - brightsparklabs/alpha:g1.2.0
    - brightsparklabs/alpha:g62d1a77
    - brightsparklabs/alpha:awesome-ant
    - brightsparklabs/alpha:testing
    - brightsparklabs/bravo:latest
    - brightsparklabs/bravo:g1.2.0
    - brightsparklabs/bravo:ge8b158f
- Create the following docker image files:
    - build/images/docker-image-brightsparklabs-alpha:g1.2.0
    - build/images/docker-image-brightsparklabs-bravo:g1.2.0
- Save the image tags to the following files:
    - build/dist/imageTags/VERSION.DOCKER-IMAGE.brightsparklabs-alpha
    - build/dist/imageTags/VERSION.DOCKER-IMAGE.brightsparklabs-bravo

# Licenses

Refer to the `LICENSE` file for details.

