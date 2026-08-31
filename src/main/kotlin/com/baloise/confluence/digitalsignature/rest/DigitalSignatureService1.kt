package com.baloise.confluence.digitalsignature.rest

import com.atlassian.confluence.api.model.Expansion
import com.atlassian.confluence.api.model.content.id.ContentId
import com.atlassian.confluence.api.model.pagination.PageResponse
import com.atlassian.confluence.api.model.pagination.PageResponseImpl
import com.atlassian.confluence.api.model.people.Subject
import com.atlassian.confluence.api.model.people.SubjectType
import com.atlassian.confluence.api.model.people.User
import com.atlassian.confluence.api.model.permissions.ContentRestriction
import com.atlassian.confluence.api.model.permissions.OperationKey
import com.atlassian.confluence.api.service.content.ContentService
import com.atlassian.confluence.api.service.permissions.ContentRestrictionService
import com.atlassian.confluence.pages.PageManager
import com.atlassian.confluence.plugin.services.VelocityHelperService
import com.atlassian.confluence.renderer.radeox.macros.MacroUtils
import com.atlassian.confluence.setup.settings.GlobalSettingsManager
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal
import com.atlassian.confluence.user.ConfluenceUser
import com.atlassian.mail.Email
import com.atlassian.mail.MailException
import com.atlassian.mail.server.MailServerManager
import com.atlassian.mywork.model.NotificationBuilder
import com.atlassian.mywork.service.LocalNotificationService
import com.atlassian.plugin.spring.scanner.annotation.component.ConfluenceComponent
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport
import com.atlassian.sal.api.message.I18nResolver
import com.atlassian.sal.api.user.UserManager
import com.atlassian.sal.api.user.UserProfile
import com.atlassian.velocity.htmlsafe.HtmlSafe
import com.baloise.confluence.digitalsignature.ContextHelper
import com.baloise.confluence.digitalsignature.Markdown
import com.baloise.confluence.digitalsignature.Signature2
import com.baloise.confluence.digitalsignature.ao.SignatureStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.text.MessageFormat
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.stream.Collectors
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo

@Path("/")
@Consumes(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON)
@ConfluenceComponent
class DigitalSignatureService(
    private val signatureStore: SignatureStore,
    @param:ComponentImport private val settingsManager: GlobalSettingsManager,
    @param:ComponentImport private val userManager: UserManager,
    @param:ComponentImport private val notificationService: LocalNotificationService,
    @param:ComponentImport private val mailServerManager: MailServerManager,
    @param:ComponentImport private val pageManager: PageManager,
    @param:ComponentImport private val i18nResolver: I18nResolver,
    @param:ComponentImport private val velocityHelperService: VelocityHelperService,
    @param:ComponentImport private val contentService: ContentService,
    @param:ComponentImport private val contentRestrictionService: ContentRestrictionService,
) {

    private val contextHelper = ContextHelper()

    @Transient
    private val markdown = Markdown()

    @GET
    @Path("sign")
    fun sign(
        @QueryParam("key") key: String?
    ): Response {
        val confluenceUser = AuthenticatedUserThreadLocal.get()
    		?: return Response.status(Response.Status.UNAUTHORIZED).build()
        
		val userName = confluenceUser.name
		if (userName.isNullOrBlank()) {
			log.error("A user name is required to call this method.")
			return Response.noContent().build()
		}

        val signature = getSignature(key) ?: run {
			log.error("A signature is required to call this method.")
			return Response.noContent().build()
		}

        if (signature == null || userName == null || userName.trim { it <= ' ' }.isEmpty()) {
            log.error(
                "Both, a signature and a user name are required to call this method.",
                NullPointerException(if (signature == null) "signature" else "userName")
            )
            return Response.noContent().build()
        }

        if (!signature.sign(userName)) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(
                    i18nResolver.getText(
                        "com.baloise.confluence.digital-signature.signature.service.error.badUser",
                        userName,
                        key
                    )
                )
                .type(MediaType.TEXT_PLAIN)
                .build()
        }

        signatureStore.put(signature.key, signature)
        val baseUrl = settingsManager.globalSettings.baseUrl
        for (notifiedUser in signature.notify) {
            notify(notifiedUser, confluenceUser, signature, baseUrl)
        }
        contentService.find(Expansion("space")).withId(ContentId.of(signature.pageId)).fetchOrNull()
            ?.let { parentPage ->
                contentService.find().withSpace(parentPage.space).withTitle(signature.protectedKey)
                    .withContainer(parentPage).fetchOrNull()
            }
            ?.let { protectedPage ->
                val restrictions: MutableMap<SubjectType, PageResponse<Subject>> = mutableMapOf()
                val subject = User.fromUserkey(confluenceUser.key)
                val pageResponse = PageResponseImpl.builder<Subject>().add(subject).build()
                restrictions[SubjectType.USER] = pageResponse

                val restriction =
                    ContentRestriction.builder().operation(OperationKey.READ).restrictions(restrictions).build()

                contentRestrictionService.updateRestrictions(
                    protectedPage.id,
                    listOf(restriction),
                    Expansion("read.restrictions.user")
                )
            }

        val pageUri =
            URI.create(settingsManager.globalSettings.baseUrl + "/pages/viewpage.action?pageId=" + signature.pageId)
        return Response.temporaryRedirect(pageUri).build()
    }

    private fun notify(notifiedUser: String, signedUser: ConfluenceUser, signature: Signature2, baseUrl: String) {
        try {
            val notifiedUserProfile = contextHelper.getProfileNotNull(userManager, notifiedUser)

            val user = String.format(
                "<a href='%s/display/~%s'>%s</a>",
                baseUrl,
                signedUser.name,
                signedUser.fullName
            )
            val document = String.format(
                "<a href='%s/pages/viewpage.action?pageId=%d'>%s</a>",
                baseUrl,
                signature.pageId,
                signature.title
            )
            var html = i18nResolver.getText(
                "com.baloise.confluence.digital-signature.signature.service.message.hasSignedShort",
                user,
                document
            )
            if (signature.isMaxSignaturesReached) {
                html += "<br/>" + i18nResolver.getText(
                    "com.baloise.confluence.digital-signature.signature.service.warning.maxSignaturesReached",
                    signature.maxSignatures
                )
            }
            val titleText = i18nResolver.getText(
                "com.baloise.confluence.digital-signature.signature.service.message.hasSignedShort",
                signedUser.fullName,
                signature.title
            )

            notificationService.createOrUpdate(
                notifiedUser,
                NotificationBuilder()
                    .application(PLUGIN_KEY) // a unique key that identifies your plugin
                    .title(titleText)
                    .itemTitle(titleText)
                    .description(html)
                    .groupingId("$PLUGIN_KEY-signature") // a key to aggregate notifications
                    .createNotification()
            ).get()

            val mailServer = mailServerManager.defaultSMTPMailServer

            if (mailServer == null) {
                log.warn("No default SMTP server found -> no signature notification sent.")
            } else if (!contextHelper.hasEmail(notifiedUserProfile)) {
                log.warn("$notifiedUser is to be notified but has no email address. Skipping email notification")
            } else {
                mailServer.send(
                    Email(notifiedUserProfile.email)
                        .setSubject(titleText)
                        .setBody(html)
                        .setMimeType("text/html")
                )
            }
        } catch (e: IllegalArgumentException) {
            log.error("Could not send notification to $notifiedUser", e)
        } catch (e: InterruptedException) {
            log.error("Could not send notification to $notifiedUser", e)
        } catch (e: MailException) {
            log.error("Could not send notification to $notifiedUser", e)
        } catch (e: ExecutionException) {
            log.error("Could not send notification to $notifiedUser", e)
        }
    }

    @GET
    @Path("export")
    @Produces("text/html; charset=UTF-8")
    @HtmlSafe
    fun export(@QueryParam("key") key: String?): String {
        val signature: Signature2 = getSignature(key) ?: log.error(
            "A signature is required to call this method.",
            NullPointerException("signature")
        ).run { return "ERROR: A signature is required to call this method." }

        val signed = contextHelper.getProfiles(userManager, signature.signatures.keys)
        val missing = contextHelper.getProfiles(userManager, signature.missingSignatures)

        val context = MacroUtils.defaultVelocityContext()
        context["markdown"] = markdown
        context["orderedSignatures"] = contextHelper.getOrderedSignatures(signature)
        context["orderedMissingSignatureProfiles"] =
            contextHelper.getOrderedProfiles(userManager, signature.missingSignatures)
        context["profiles"] = contextHelper.union(signed, missing)
        context["signature"] = signature
        context["currentDate"] = Date()
        context["date"] = Date()

        return velocityHelperService.getRenderedTemplate("templates/export.vm", context)
    }

    @GET
    @Path("emails")
    @Produces("text/html; charset=UTF-8")
    fun emails(
        @QueryParam("key") key: String?,
        @QueryParam("signed") signed: Boolean,
        @QueryParam("emailOnly") emailOnly: Boolean,
        @Context uriInfo: UriInfo
    ): Response {
        val signature: Signature2 = getSignature(key) ?: log.error(
            "A signature is required to call this method.",
            NullPointerException("signature")
        ).run {
            return Response.noContent().build()
        }

        val profiles = contextHelper.getProfiles(
            userManager, if (signed)
                signature.signatures.keys
            else
                signature.missingSignatures
        )

        val context = MacroUtils.defaultVelocityContext()
        context["signature"] = signature
        val signatureText = String.format("<i>%s</i> ( %s )", signature.title, signature.hash)
        val rawTemplate =
            if (signed) i18nResolver.getRawText("com.baloise.confluence.digital-signature.signature.service.message.signedUsersEmails") else i18nResolver.getRawText(
                "com.baloise.confluence.digital-signature.signature.service.message.unsignedUsersEmails"
            )
        context["signedOrNotWithHtml"] = MessageFormat.format(rawTemplate, "<b>", "</b>", signatureText)
        context["withNamesChecked"] = if (emailOnly) "" else "checked"
        context["signedChecked"] = if (signed) "checked" else ""
        context["toggleWithNamesURL"] = uriInfo.requestUriBuilder.replaceQueryParam("emailOnly", !emailOnly).build()
        context["toggleSignedURL"] = uriInfo.requestUriBuilder.replaceQueryParam("signed", !signed).build()
        val mapping = { p: UserProfile -> (if (emailOnly) p.email else contextHelper.mailTo(p)).trim { it <= ' ' } }
        context["emails"] = profiles.values.stream()
            .filter { profile: UserProfile? -> contextHelper.hasEmail(profile) }
            .map(mapping).collect(Collectors.toList())

        context["currentDate"] = Date()
        context["date"] = Date()
        return Response.ok(velocityHelperService.getRenderedTemplate("templates/email.vm", context)).build()
    }

    /**
     * @param key signature key
     * @return stored signature, or `null` if the key is null or not found
     */
    private fun getSignature(key: String?): Signature2? = key?.let { signatureStore.get(it) }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(DigitalSignatureService::class.java)
        const val PLUGIN_KEY: String = "com.baloise.confluence:digital-signature"
    }
}
