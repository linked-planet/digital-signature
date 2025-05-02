package com.baloise.confluence.digitalsignature

import com.atlassian.sal.api.user.UserKey
import com.atlassian.sal.api.user.UserProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.*

internal class UserProfileByNameTest {
    @Test
    fun testCompare() {
        val profile1 = buildUserProfile("Heinz Meier", "heinz.meier@meier.com")

        val profile2 = buildUserProfile("Abraham Aebischer", "Abraham Aebischer@meier.com")
        val profiles: SortedSet<UserProfile> = TreeSet(UserProfileByName())
        profiles.add(profile1)
        profiles.add(profile2)
        profiles.add(profile1)

        Assertions.assertEquals("[Abraham Aebischer, Heinz Meier]", profiles.toString())
    }

    fun buildUserProfile(userName: String, userEmail: String)= object : UserProfile {
        override fun toString(): String = userName
        override fun getFullName(): String = userName
        override fun getUsername(): String = userName
        override fun getEmail(): String = userEmail

        override fun getUserKey(): UserKey {
            TODO("Not yet implemented")
        }
        override fun getProfilePictureUri(p0: Int, p1: Int): URI {
            TODO("Not yet implemented")
        }
        override fun getProfilePictureUri(): URI {
            TODO("Not yet implemented")
        }
        override fun getProfilePageUri(): URI {
            TODO("Not yet implemented")
        }
    }
}
