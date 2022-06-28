/*
 * Maintained by brightSPARK Labs.
 * www.brightsparklabs.com
 *
 * Refer to LICENSE at repository root for license details.
 */

package com.brightsparklabs.gradle.docker

/**
 * Configuration object for the DockerImage plugin.
 */
class DockerImagePluginExtension {
    /**
     * Definitions of the Dockerfiles to build. This is an array of Maps. Each
     * Map has the following format:
     *    [
     *        // dockerfile to build
     *        'dockerfile' : file('src/acme/Dockerfile'),
     *        // repository name for the built docker image
     *        'name'       : 'brightsparklabs/acme',
     *        // OPTIONAL: custom tags for the built docker image
     *        'tags'       : ['v1.0.1', 'awesome-ant']
     *        // OPTIONAL: additional arguments to the `docker build` command
     *        'buildArgs'  : ['--compress', '--quiet']
     *        // OPTIONAL: target to build (only applies to multi-stage builds)
     *        'target'     : 'dev'
     *        // OPTIONAL:  the Docker build context path (defaults to parent folder)
     *        'contextDir' : file('./')
     *    ]
     */
    List dockerFileDefinitions = []

    /** Directory to store images in */
    File imagesDir

    /** Directory to store image tags in */
    File imageTagDir

    /** Whether to continue the build if a Dockerfile has an error */
    Boolean continueOnFailure = false

    /** Whether to delete older Docker images after building each image */
    Boolean deleteOlderImages = false

    /** Whether to remove dangling Docker images after building all images*/
    Boolean removeDanglingImages = false

    /** The private Docker server */
    String privateDockerServer

    /** The private Docker server login username */
    String privateDockerUsername

    /** The private Docker server login password */
    String privateDockerPassword
}

