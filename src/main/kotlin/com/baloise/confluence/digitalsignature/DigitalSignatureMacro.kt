package com.baloise.confluence.digitalsignature

import com.atlassian.bandana.BandanaManager
import com.atlassian.confluence.api.model.Expansion
import com.atlassian.confluence.api.model.content.ContentType
import com.atlassian.confluence.api.model.content.id.ContentId
import com.atlassian.confluence.api.service.content.ContentService
import com.atlassian.confluence.content.render.xhtml.ConversionContext
import com.atlassian.confluence.core.ContentEntityObject
import com.atlassian.confluence.core.ContextPathHolder
import com.atlassian.confluence.core.DefaultSaveContext
import com.atlassian.confluence.macro.Macro
import com.atlassian.confluence.macro.Macro.OutputType
import com.atlassian.confluence.pages.Page
import com.atlassian.confluence.pages.PageManager
import com.atlassian.confluence.plugin.services.VelocityHelperService
import com.atlassian.confluence.renderer.radeox.macros.MacroUtils
import com.atlassian.confluence.security.ContentPermission
import com.atlassian.confluence.security.Permission
import com.atlassian.confluence.security.PermissionManager
import com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal
import com.atlassian.confluence.user.UserAccessor
import com.atlassian.plugins.osgi.javaconfig.OsgiServices.importOsgiService
import com.atlassian.sal.api.message.I18nResolver
import com.atlassian.sal.api.user.UserManager
import com.atlassian.sal.api.user.UserProfile
import com.atlassian.user.EntityException
import com.atlassian.user.GroupManager
import org.springframework.context.annotation.Bean
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.stream.Collectors

class DigitalSignatureMacro() : Macro {
    private val bandanaManager: BandanaManager
        @Bean get() = importOsgiService(BandanaManager::class.java)
    private val userManager: UserManager
        @Bean get() = importOsgiService(UserManager::class.java)
    private val contextPathHolder: ContextPathHolder
        @Bean get() = importOsgiService(ContextPathHolder::class.java)
    private val pageManager: PageManager
        @Bean get() = importOsgiService(PageManager::class.java)
    private val permissionManager: PermissionManager
        @Bean get() = importOsgiService(PermissionManager::class.java)
    private val groupManager: GroupManager
        @Bean get() = importOsgiService(GroupManager::class.java)
    private val i18nResolver: I18nResolver
        @Bean get() = importOsgiService(I18nResolver::class.java)
    private val velocityHelperService: VelocityHelperService
        @Bean get() = importOsgiService(VelocityHelperService::class.java)
    private val contentService: ContentService
        @Bean get() = importOsgiService(ContentService::class.java)
    private val userAccessor: UserAccessor
        @Bean get() = importOsgiService(UserAccessor::class.java)

    private val markdown = Markdown()
    private val contextHelper = ContextHelper()

    override fun execute(params: Map<String, String>, body: String?, conversionContext: ConversionContext): String {
        if (body == null || body.length <= 10) {
            return warning(i18nResolver.getText("com.baloise.confluence.digital-signature.signature.macro.warning.bodyToShort"))
        }

        val userGroups = getSet(params, "signerGroups")
        val petitionMode: Boolean = Signature2.isPetitionMode(userGroups)
        val signers = if (petitionMode) setOf("*") else contextHelper.union(
            getSet(params, "signers"), loadUserGroups(userGroups), loadInheritedSigners(
                InheritSigners.ofValue(
                    params["inheritSigners"] ?: ""
                ), conversionContext
            )
        )
        val entity = conversionContext.entity
        val signature = sync(
            Signature2(entity!!.latestVersionId, body, params["title"] ?: "").withNotified(getSet(params, "notified"))
                .withMaxSignatures(getLong(params, "maxSignatures"))
                .withVisibilityLimit(getLong(params, "visibilityLimit")), signers
        )

        val protectedContent = getBoolean(params, "protectedContent", false)
        if (protectedContent && isPage(conversionContext)) {
            try {
                ensureProtectedPage(entity as Page, signature)
            } catch (e: Exception) {
                return warning(
                    i18nResolver.getText(
                        "com.baloise.confluence.digital-signature.signature.macro.warning.editPermissionRequiredForProtectedContent",
                        "<a class=\"system-metadata-restrictions\">",
                        "</a>"
                    )
                )
            }
        }
        val velocityContext = buildContext(params, conversionContext, entity, signature, protectedContent)
        return velocityHelperService.getRenderedTemplateWithoutSwallowingErrors("templates/macro.vm", velocityContext)
    }

    private fun buildContext(
        params: Map<String, String>,
        conversionContext: ConversionContext,
        page: ContentEntityObject,
        signature: Signature2,
        protectedContent: Boolean
    ): Map<String, Any?> {
        val currentUser = AuthenticatedUserThreadLocal.get()
        val currentUserName = currentUser.name
        val protectedContentAccess = protectedContent && (permissionManager.hasPermission(
            currentUser,
            Permission.EDIT,
            page
        ) || signature.hasSigned(currentUserName))

        val context = MacroUtils.defaultVelocityContext()
        context["date"] = Date()
        context["markdown"] = markdown

        if (signature.isSignatureMissing(currentUserName)) {
            context["signAs"] = contextHelper.getProfileNotNull(userManager, currentUserName).fullName
            context["signAction"] = contextPathHolder.contextPath + REST_PATH + "/sign"
        }
        context["panel"] = getBoolean(params, "panel", true)
        context["protectedContent"] = protectedContentAccess
        if (protectedContentAccess && isPage(conversionContext)) {
            context["protectedContentURL"] =
                contextPathHolder.contextPath + DISPLAY_PATH + "/" + (page as Page).spaceKey + "/" + signature.protectedKey
        }

        val canExport = hideSignatures(params, signature, currentUserName)
        val signed = contextHelper.getProfiles(userManager, signature.signatures.keys)
        val missing = contextHelper.getProfiles(userManager, signature.missingSignatures)

        context["orderedSignatures"] = contextHelper.getOrderedSignatures(signature)
        context["orderedMissingSignatureProfiles"] =
            contextHelper.getOrderedProfiles(userManager, signature.missingSignatures)
        context["profiles"] = contextHelper.union(signed, missing)
        context["signature"] = signature
        context["visibilityLimit"] = signature.visibilityLimit
        context["mailtoSigned"] = getMailto(signed.values, signature.title, true, signature)
        context["mailtoMissing"] = getMailto(missing.values, signature.title, false, signature)
        context["UUID"] = UUID.randomUUID().toString().replace("-", "")
        context["downloadURL"] =
            if (canExport) contextPathHolder.contextPath + REST_PATH + "/export?key=" + signature.key else null
        return context
    }

    private fun ensureProtectedPage(page: Page, signature: Signature2) {
        val parentPage = contentService.find(Expansion("space")).withId(ContentId.of(page.id)).fetchOrNull()
        contentService.find(Expansion("id"))
            .withTitle(signature.protectedKey)
            .withSpace(parentPage.space)
            .withType(ContentType.PAGE)
            .fetchOrNull()
            ?: {

                val editors = page.getContentPermissionSet(ContentPermission.EDIT_PERMISSION)
                check(!editors.isEmpty) { "No editors found!" }
                val protectedPage = Page()
                protectedPage.space = page.space
                protectedPage.setParentPage(page)
                protectedPage.version = 1
                protectedPage.creator = page.creator
                for (editor in editors) {
                    protectedPage.addPermission(
                        ContentPermission.createUserPermission(
                            ContentPermission.EDIT_PERMISSION,
                            editor.userSubject
                        )
                    )
                    protectedPage.addPermission(
                        ContentPermission.createUserPermission(
                            ContentPermission.VIEW_PERMISSION,
                            editor.userSubject
                        )
                    )
                }
                for (signedUserName in signature.signatures.keys) {
                    protectedPage.addPermission(
                        ContentPermission.createUserPermission(
                            ContentPermission.VIEW_PERMISSION,
                            userAccessor.getUserByName(signedUserName)
                        )
                    )
                }
                protectedPage.title = signature.protectedKey
                pageManager.saveContentEntity(protectedPage, DefaultSaveContext.DEFAULT)
                page.addChild(protectedPage)
            }
    }

    private fun hideSignatures(params: Map<String, String>, signature: Signature2, currentUserName: String): Boolean {
        val pendingVisible = isVisible(signature, currentUserName, params["pendingVisible"] ?: "")
        val signaturesVisible = isVisible(signature, currentUserName, params["signaturesVisible"] ?: "")
        if (!pendingVisible) signature.missingSignatures = TreeSet()
        if (!signaturesVisible) signature.signatures = HashMap()
        return pendingVisible && signaturesVisible
    }

    private fun isVisible(signature: Signature2, currentUserName: String, signaturesVisibleParam: String): Boolean {
        return when (SignaturesVisible.ofValue(signaturesVisibleParam)) {
            SignaturesVisible.IF_SIGNATORY -> signature.hasSigned(currentUserName) || signature.isSignatory(
                currentUserName
            )

            SignaturesVisible.IF_SIGNED -> signature.hasSigned(currentUserName)
            SignaturesVisible.ALWAYS -> true
        }
    }

    private fun isPage(conversionContext: ConversionContext): Boolean {
        return conversionContext.entity is Page
    }

    private fun warning(message: String): String {
        return """
<div class="aui-message aui-message-warning">
    <p class="title">
        <strong>${i18nResolver.getText("com.baloise.confluence.digital-signature.signature.label")}</strong>
    </p>
    <p>$message</p>
</div>"""
    }

    private fun loadInheritedSigners(
        inheritSigners: InheritSigners,
        conversionContext: ConversionContext
    ): Set<String> {
        val users: MutableSet<String> = HashSet()
        when (inheritSigners) {
            InheritSigners.READERS_AND_WRITERS -> {
                users.addAll(loadUsers(conversionContext, ContentPermission.VIEW_PERMISSION))
                users.addAll(loadUsers(conversionContext, ContentPermission.EDIT_PERMISSION))
            }

            InheritSigners.READERS_ONLY -> {
                users.addAll(loadUsers(conversionContext, ContentPermission.VIEW_PERMISSION))
                users.removeAll(loadUsers(conversionContext, ContentPermission.EDIT_PERMISSION))
            }

            InheritSigners.WRITERS_ONLY -> users.addAll(loadUsers(conversionContext, ContentPermission.EDIT_PERMISSION))
            InheritSigners.NONE -> {}
        }
        return users
    }

    private fun loadUsers(conversionContext: ConversionContext, permission: String): Set<String> {
        val users: MutableSet<String> = HashSet()
        val contentPermissionSet = conversionContext.entity!!.getContentPermissionSet(permission)
        if (contentPermissionSet != null) {
            for (cp in contentPermissionSet) {
                if (cp.groupName != null) {
                    users.addAll(loadUserGroup(cp.groupName))
                }
                if (cp.userSubject != null) {
                    users.add(cp.userSubject.name)
                }
            }
        }
        return users
    }

    private fun loadUserGroups(groupNames: Iterable<String>): Set<String> {
        val ret: MutableSet<String> = HashSet()
        for (groupName in groupNames) {
            ret.addAll(loadUserGroup(groupName))
        }
        return ret
    }

    private fun loadUserGroup(groupName: String?): Set<String> {
        val ret: MutableSet<String> = HashSet()
        try {
            if (groupName == null) return ret
            val group = groupManager.getGroup(groupName.trim { it <= ' ' })
            if (group == null) return ret
            val pager = groupManager.getMemberNames(group)
            while (!pager.onLastPage()) {
                ret.addAll(pager.currentPage)
                pager.nextPage()
            }
            ret.addAll(pager.currentPage)
        } catch (e: EntityException) {
            e.printStackTrace()
        }
        return ret
    }

    private fun getBoolean(params: Map<String, String>, key: String, fallback: Boolean): Boolean {
        val value = params[key]
        return value?.toBoolean() ?: fallback
    }

    private fun getLong(params: Map<String, String>, key: String, fallback: Long = -1L): Long {
        val value = params[key]
        return value?.toLong() ?: fallback
    }

    private fun getSet(params: Map<String, String>, key: String): Set<String> {
        val value = params[key]
        return if (value == null || value.trim { it <= ' ' }.isEmpty()) TreeSet() else TreeSet(
            listOf(
                *value.split("[;, ]+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            )
        )
    }

    private fun sync(signature: Signature2, signers: Set<String>): Signature2 {
        val value: Any? = bandanaManager.getValue(ConfluenceBandanaContext.GLOBAL_CONTEXT, signature.key)
        val (sig, requiresUpdate) = Signature2.fromBandana(value)
        if (requiresUpdate) {
            Signature2.toBandana(bandanaManager, signature.key, sig!!)
        }
        sig?.also { loaded ->
            signature.signatures = loaded.signatures
            var save = false

            if (loaded.notify != signature.notify) {
                loaded.notify = signature.notify
                save = true
            }

            signature.missingSignatures = signers - loaded.signatures.keys
            if (loaded.missingSignatures != signature.missingSignatures) {
                loaded.missingSignatures = signature.missingSignatures
                save = true
            }

            if (loaded.maxSignatures != signature.maxSignatures) {
                loaded.maxSignatures = signature.maxSignatures
                save = true
            }

            if (loaded.visibilityLimit != signature.visibilityLimit) {
                loaded.visibilityLimit = signature.visibilityLimit
                save = true
            }

            if (save) {
                loaded.save(bandanaManager)
            }
        } ?: signature.apply { missingSignatures = signers }.save(bandanaManager)
        return signature
    }

    override fun getBodyType(): Macro.BodyType {
        return Macro.BodyType.PLAIN_TEXT
    }

    override fun getOutputType(): OutputType {
        return OutputType.BLOCK
    }

    fun getMailto(
        profiles: Collection<UserProfile>?,
        subject: String,
        signed: Boolean,
        signature: Signature2?,
        contextPathFixed: String? = null // for testing only as we're missing the osgi wiring
    ): String? {
        if (profiles.isNullOrEmpty()) return null
        val profilesWithMail: List<UserProfile> =
            profiles.stream().filter { profile: UserProfile -> contextHelper.hasEmail(profile) }.collect(
                Collectors.toList()
            )
        val ret = StringBuilder("mailto:")
        for (profile in profilesWithMail) {
            if (ret.length > 7) ret.append(',')
            ret.append(contextHelper.mailTo(profile))
        }
        ret.append("?Subject=").append(urlEncode(subject))
        if (ret.length > MAX_MAILTO_CHARACTER_COUNT) {
            ret.setLength(0)
            ret.append("mailto:")
            for (profile in profilesWithMail) {
                if (ret.length > 7) ret.append(',')
                ret.append(profile.email.trim { it <= ' ' })
            }
            ret.append("?Subject=").append(urlEncode(subject))
        }
        if (ret.length > MAX_MAILTO_CHARACTER_COUNT) {
            val ctxPath = contextPathFixed ?: contextPathHolder.contextPath
            return ctxPath + REST_PATH + "/emails?key=" + signature?.key + "&signed=" + signed
        }
        return ret.toString()
    }

    private fun urlEncode(string: String): String {
        try {
            return URLEncoder.encode(string, StandardCharsets.UTF_8.name())
        } catch (e: UnsupportedEncodingException) {
            throw IllegalStateException(e)
        }
    }

    companion object {
        private const val MAX_MAILTO_CHARACTER_COUNT = 500
        private const val REST_PATH = "/rest/signature/1.0"
        private const val DISPLAY_PATH = "/display"
    }
}
