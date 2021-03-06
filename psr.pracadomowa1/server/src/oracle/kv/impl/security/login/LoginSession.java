/*-
 * Copyright (C) 2011, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This file was distributed by Oracle as part of a version of Oracle NoSQL
 * Database made available at:
 *
 * http://www.oracle.com/technetwork/database/database-technologies/nosqldb/downloads/index.html
 *
 * Please see the LICENSE file included in the top-level directory of the
 * appropriate version of Oracle NoSQL Database for a copy of the license and
 * additional information.
 */
package oracle.kv.impl.security.login;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.security.auth.Subject;

/**
 * LoginSession captures the critical information about a login session.
 */
public class LoginSession {

    /* The key for this login session */
    private final Id id;

    /* The Subject that describes the logged-in entity */
    private volatile Subject subject;

    /* The client host from which the login originated */
    private final String clientHost;

    /* Indicates whether this is a persistent session */
    private final boolean isPersistentSession;

    /*
     * The time at which the login session will time out, expressed in the
     * same units and time reference as System.currentTimeMillis(). If the
     * value is 0L, the session will not expire.
     */
    private volatile long expireTime;

    public static final class Id {
        private final byte[] idValue;

        public Id(byte[] value) {
            idValue = value;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(idValue);
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if (other == null) {
                return false;
            }
            if (other.getClass() != getClass()) {
                return false;
            }
            final Id otherId = (Id) other;
            return Arrays.equals(idValue, otherId.idValue);
        }

        public byte[] getValue() {
            return Arrays.copyOf(idValue, idValue.length);
        }

        public boolean beginsWith(byte[] prefix) {
            if (idValue.length < prefix.length) {
                return false;
            }

            for (int i = 0; i < prefix.length; i++) {
                if (idValue[i] != prefix[i]) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Produce a compact identifier for the input idValue that is securely
         * generated.  This allows session id values to be represented in log
         * output without the risk of someone using the underying id.  The
         * generated output must not be relied upon as a truly unique
         * identifier for the idValue.
         */
        public static int hashId(byte[] idValue) {

            final String hashAlgo = "SHA-256";
            try {
                final MessageDigest md = MessageDigest.getInstance(hashAlgo);
                md.update(idValue, 0, idValue.length);
                final byte[] mdbytes = md.digest();

                /* Use the first 31 bits of the secure hash as the id value */
                return (mdbytes[0] & 0xff) |
                    ((mdbytes[1] & 0xff) << 8) |
                    ((mdbytes[2] & 0xff) << 16) |
                    ((mdbytes[3] & 0x7f) << 24);
            } catch (NoSuchAlgorithmException nsae) {

                /*
                 * This is unlikely to occur, since this is available in the
                 * versions of the JDK that we support, but the output of this
                 * is not critical, so just report it in a form that would allow
                 * us to recognize that a problem is occurring.
                 */
                return -1;
            }
        }

        /**
         * Computes a securely hashed identifier for the session id. The hash
         * values for two distinct ids are not guaranteed to be unique.
         */
        public int hashId() {
            return hashId(idValue);
        }

    }

    /**
     * Create a login session object.
     */
    public LoginSession(Id id,
                        Subject subject,
                        String clientHost,
                        boolean persistent) {
        if (id == null) {
            throw new IllegalArgumentException("id may mnot be null");
        }
        this.id = id;
        this.subject = subject;
        this.clientHost = clientHost;
        this.expireTime = 0;
        this.isPersistentSession = persistent;
    }

    public Id getId() {
        return id;
    }

    public Subject getSubject() {
        return subject;
    }

    public String getClientHost() {
        return clientHost;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    public boolean isExpired() {
        return expireTime != 0 && System.currentTimeMillis() > expireTime;
    }

    public boolean isPersistent() {
        return isPersistentSession;
    }

    @Override
    public LoginSession clone() {
        final LoginSession session =
            new LoginSession(getId(), getSubject(), getClientHost(),
                             isPersistentSession);
        session.setExpireTime(getExpireTime());
        return session;
    }
}
