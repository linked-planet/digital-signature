package com.baloise.confluence.digitalsignature

import com.atlassian.sal.api.user.UserProfile

class UserProfileByName : Comparator<UserProfile> {
    override fun compare(u1: UserProfile, u2: UserProfile): Int {
        var ret = nn(u1.fullName).compareTo(nn(u2.fullName))
        if (ret != 0) return ret

        ret = nn(u1.email).compareTo(nn(u2.email))
        if (ret != 0) return ret

        ret = nn(u1.username).compareTo(nn(u2.username))
        if (ret != 0) return ret

        return u1.hashCode().compareTo(u2.hashCode())
    }

    private fun nn(string: String?): String {
        return string ?: ""
    }
}
