package com.baloise.confluence.digitalsignature

import com.atlassian.sal.api.user.UserKey
import com.atlassian.sal.api.user.UserProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.net.URI
import java.util.*

internal class UserProfileByNameTest {
    @Test
    fun testCompare() {
        val profile1 = object : UserProfile {
            override fun toString(): String = "Heinz Meier"
            override fun getUsername(): String = "Heinz Meier"
            override fun getFullName(): String = "Heinz Meier"
            override fun getEmail(): String = "heinz.meier@meier.com"
            override fun getUserKey(): UserKey { TODO("Not yet implemented") }

            override fun getProfilePictureUri(p0: Int, p1: Int): URI { TODO("Not yet implemented") }
            override fun getProfilePictureUri(): URI { TODO("Not yet implemented") }
            override fun getProfilePageUri(): URI { TODO("Not yet implemented") }
        }

        val profile2 = object : UserProfile {
            override fun toString(): String = "Abraham Aebischer"
            override fun getFullName(): String = "Abraham Aebischer"
            override fun getEmail(): String = "Abraham Aebischer@meier.com"
            override fun getUsername(): String = "Abraham Aebischer"
            override fun getUserKey(): UserKey { TODO("Not yet implemented") }

            override fun getProfilePictureUri(p0: Int, p1: Int): URI { TODO("Not yet implemented") }
            override fun getProfilePictureUri(): URI { TODO("Not yet implemented") }
            override fun getProfilePageUri(): URI { TODO("Not yet implemented") }
        }
        val profiles: SortedSet<UserProfile> = TreeSet(UserProfileByName())
        profiles.add(profile1)
        profiles.add(profile2)
        profiles.add(profile1)

        Assertions.assertEquals("[Abraham Aebischer, Heinz Meier]", profiles.toString())
    }
}
