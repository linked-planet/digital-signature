package com.baloise.confluence.digitalsignature.api

import org.springframework.beans.factory.InitializingBean

interface PluginComponent : InitializingBean {
    companion object {
        const val name: String = "digital-signature"
    }
}