package com.lestere.opensource.arc

enum class AutoReleaseCyberTag {
    /**
     * Super force task, will finish this task when server down but this task still not be run.
     */
    FORCE,

    /**
     * Not the super task, will not run when server down, means this task will cancel when server down but not run.
     */
    REST;
}