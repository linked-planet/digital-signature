package com.baloise.confluence.digitalsignature.ao;

import net.java.ao.Entity;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Unique;

/**
 * Active Objects row for a digital signature. The Gson JSON of {@code Signature2} lives in
 * {@code payload} so signer maps do not need child tables.
 */
public interface SignatureEntity extends Entity {

    /**
     * Bandana / {@code Signature2} key, e.g. {@code signature.<sha256>}.
     */
    @NotNull
    @Unique
    String getSignatureKey();

    void setSignatureKey(String signatureKey);

    long getPageId();

    void setPageId(long pageId);

    /**
     * {@code Signature2.serialize()} JSON. Unlimited length because the macro body is included.
     */
    @NotNull
    @StringLength(StringLength.UNLIMITED)
    String getPayload();

    void setPayload(String payload);
}
