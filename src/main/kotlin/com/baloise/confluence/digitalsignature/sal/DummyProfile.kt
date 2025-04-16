package com.baloise.confluence.digitalsignature.sal

import com.atlassian.sal.api.user.UserKey
import com.atlassian.sal.api.user.UserProfile
import java.net.URI

class DummyProfile(private val userKey: String) : UserProfile {
    override fun getUserKey(): UserKey {
        return UserKey(userKey)
    }

    override fun getUsername(): String {
        return userKey
    }

    override fun getFullName(): String {
        return userKey
    }

    override fun getEmail(): String {
        return ""
    }

    override fun getProfilePictureUri(width: Int, height: Int): URI? {
        return null
    }

    override fun getProfilePictureUri(): URI? {
        return null
    }

    override fun getProfilePageUri(): URI? {
        return null
    }
}
