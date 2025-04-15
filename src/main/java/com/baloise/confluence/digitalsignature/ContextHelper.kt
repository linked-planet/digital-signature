package com.baloise.confluence.digitalsignature

import com.atlassian.sal.api.user.UserManager
import com.atlassian.sal.api.user.UserProfile
import com.baloise.confluence.digitalsignature.sal.DummyProfile
import java.util.*
import java.util.function.Function

class ContextHelper {
    fun getOrderedSignatures(signature: Signature2): Any {
        val ret: SortedSet<Map.Entry<String, Date>> = signature.signatures.entries.toSortedSet(
            Comparator<Map.Entry<String, Date>> { o1, o2 -> o1.value.compareTo(o2.value) }
                .thenComparing { o1, o2 -> o1.key.compareTo(o2.key) }
        )
        return ret
    }

    @SafeVarargs
    fun <K, V> union(vararg maps: Map<K, V>): Map<K, V> {
        val union: MutableMap<K, V> = HashMap()
        for (map in maps) {
            union.putAll(map)
        }
        return union
    }

    @SafeVarargs
    fun <K> union(vararg sets: Set<K>): Set<K> {
        val union: MutableSet<K> = HashSet()
        for (set in sets) {
            union.addAll(set)
        }
        return union
    }

    fun getProfiles(userManager: UserManager, userNames: Set<String>): Map<String, UserProfile> {
        val ret: MutableMap<String, UserProfile> = HashMap()
        if (Signature2.Companion.isPetitionMode(userNames)) return ret
        for (userName in userNames) {
            ret[userName] = getProfileNotNull(userManager, userName)
        }
        return ret
    }

    fun getProfileNotNull(userManager: UserManager, userName: String): UserProfile {
        val profile = userManager.getUserProfile(userName)
        return profile ?: DummyProfile(userName)
    }

    fun getOrderedProfiles(userManager: UserManager, userNames: Set<String>): SortedSet<UserProfile> {
        val ret: SortedSet<UserProfile> = TreeSet(UserProfileByName())
        if (Signature2.Companion.isPetitionMode(userNames)) return ret
        for (userName in userNames) {
            ret.add(getProfileNotNull(userManager, userName))
        }
        return ret
    }

    fun mailTo(profile: UserProfile): String {
        return String.format("%s<%s>", profile.fullName.trim { it <= ' ' }, profile.email.trim { it <= ' ' })
    }

    fun hasEmail(profile: UserProfile?): Boolean {
        return profile != null && profile.email != null && !profile.email.trim { it <= ' ' }.isEmpty()
    }
}
