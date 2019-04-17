/*
 * Created by brightSPARK Labs
 * www.brightsparklabs.com
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
     *    ]
     */
    List dockerFileDefinitions = []

    /** Directory to store images in */
    File imagesDir

    /** Directory to store image tags in */
    File imageTagDir

    /** Whether to continue the build if a Dockerfile has an error */
    Boolean continueOnFailure = false
}

