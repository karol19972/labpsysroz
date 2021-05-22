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
package oracle.kv.util.migrator.impl.source.ondb;

import static oracle.nosql.common.migrator.util.Constants.ONDB_TYPE;
import static oracle.nosql.common.migrator.util.JsonUtils.*;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import oracle.kv.Consistency;
import oracle.kv.util.migrator.impl.util.OndbConfig;
import oracle.nosql.common.migrator.DataSourceConfig;
import oracle.nosql.common.migrator.MigratorCommandParser;
import oracle.nosql.common.migrator.MigratorCommandParser.CommandParserHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The NoSQL database source configuration.
 * <pre>{@code
 * "source" : {
 *    "type": "nosqldb",
 *    "helperHosts" : [<host:port>, <host:port> ...],    --required
 *    "storeName" : "<storeName>",                       --required
 *    "username" : <user-name>,                          --optional
 *    "security" : <security-file-path>,                 --optional
 *    "tables" : [<table>, <table> ...],                 --optional
 *    "namespaces" : [<namespace>, <namespace> ...],     --optional
 *    "requestTimeoutMs": <ms>                           --optional
 *    "consistency": {                                   --optional,hidden
 *        "type": "NONE"|"ABSOLUTE"|"TIME",
 *        "permissibleLagMs":<ms>,
 *        "timeoutMs":<ms>,
 *     }
 * }
 * }</pre>
 */
public class OndbSourceConfig extends OndbConfig
    implements DataSourceConfig {

    public final static String PROP_CONSISTENCY = "consistency";
    public final static String PROP_CONSISTENCY_TYPE = "type";
    public final static String PROP_CONSISTENCY_PERMISSIBLE_LAG_MS =
        "permissibleLagMs";
    public final static String PROP_CONSISTENCY_TIMEOUT_MS = "timeoutMs";

    static String COMMAND_ARGS =
        OndbConfig.COMMAND_ARGS_REQ + "\n\t" +
        OndbConfig.COMMAND_ARGS_OPT;

    private ConsistencyType consistency;

    OndbSourceConfig() {
        super(ONDB_TYPE);
    }

    public OndbSourceConfig(InputStream in, int configVersion) {
        this();
        parseJson(in, configVersion);
    }

    public OndbSourceConfig(MigratorCommandParser parser) {
        this();
        parseArgs(parser);
    }

    /**
     * Used by unit test
     */
    public OndbSourceConfig(String[] helperHosts,
                            String storeName,
                            String userName,
                            String securityFile,
                            String[] namespaces,
                            String[] tables,
                            int requestTimeoutMs,
                            Consistency consistency) {

        super(ONDB_TYPE, helperHosts, storeName, userName, securityFile,
              namespaces, tables, requestTimeoutMs);
        this.consistency = (consistency == null ? null :
                            ConsistencyType.fromConsistency(consistency));
        validate(false);
    }

    @Override
    public void parseJsonNode(JsonNode node, int configVersion) {
        super.parseJsonNode(node, configVersion);

        JsonNode consNode = node.get(PROP_CONSISTENCY);
        if (consNode != null) {
            if (consNode.isObject()) {
                ConsistencyType.Type type =
                     readEnum(consNode, PROP_CONSISTENCY_TYPE,
                              ConsistencyType.Type.class,
                              ConsistencyType.Type.TIME);
                if (type != ConsistencyType.Type.TIME) {
                    throw new InvalidConfigException(
                        "Invalid consistency configuration: " +
                         consNode.toString(),
                         this);
                }
                long permissibleLagMs = readLong(consNode,
                        PROP_CONSISTENCY_PERMISSIBLE_LAG_MS);
                long timeoutMs = readLong(consNode, PROP_CONSISTENCY_TIMEOUT_MS);
                consistency = new ConsistencyType(permissibleLagMs, timeoutMs);
            } else if (consNode.isTextual()) {
                String type = consNode.textValue();
                ConsistencyType.Type ctype;
                try {
                    ctype = ConsistencyType.Type.valueOf(type.toUpperCase());
                } catch (IllegalArgumentException iae) {
                    throw new InvalidConfigException(
                        "Invalid consistency configuration: " + type, this);
                }
                consistency = new ConsistencyType(ctype);
            }
        }
    }

    @Override
    public void writeJsonNode(ObjectNode node, int configVersion) {
        super.writeJsonNode(node, configVersion);
        /* Don't display consistency value */
    }

    @Override
    public void validate(boolean parseArgs) {
        super.validate(parseArgs);
        if (consistency != null) {
            if (consistency.getType() == ConsistencyType.Type.TIME) {
                if (consistency.permissibleLagMs < 0) {
                    throw new InvalidConfigException(
                        "permissibleLagMs must not be a negative value: " +
                        consistency.permissibleLagMs,
                        this);
                }
                if (consistency.timeoutMs < 0) {
                    throw new InvalidConfigException(
                        "timeoutMs must not be a negative value: " +
                        consistency.timeoutMs,
                        this);
                }
            }
        }
    }

    @Override
    public CommandParserHandler getCommandHandler(MigratorCommandParser parser) {
        /*
         * Supported arguments:
         *  -helper-hosts <host:port[,host:port]*>
         *  -store <storeName>
         *  [-username <user>] [-security <security-file-path>]
         *  [-namespaces <namespace>,<namespace>]
         *  [-tables <tablename>,<tablename>]
         */
        return new OndbConfigArgumentHandler(parser);
    }

    public ConsistencyType getConsistency() {
        return consistency;
    }

    public Consistency getReadConsistency() {
        if (consistency != null) {
            return consistency.toConsistency();
        }
        return null;
    }

    public static class ConsistencyType {
        public static enum Type {
            NONE,
            ABSOLUTE,
            TIME
        }
        private Type type;
        private long permissibleLagMs;
        private long timeoutMs;

        ConsistencyType() {
        }

        ConsistencyType(Type type) {
            this.type = type;
        }

        ConsistencyType(long permissibleLagMs, long timeoutMs) {
            this.type = Type.TIME;
            this.permissibleLagMs = permissibleLagMs;
            this.timeoutMs = timeoutMs;
        }

        public Type getType() {
            return type;
        }

        public Long getPermissibleLagMs() {
            return (type == Type.TIME) ? permissibleLagMs : null;
        }

        public Long getTimeoutMs() {
            return (type == Type.TIME) ? timeoutMs : null;
        }

        public static ConsistencyType fromConsistency(Consistency cons) {
            if (cons == null) {
                return null;
            }
            if (cons == Consistency.NONE_REQUIRED) {
                return new ConsistencyType(Type.NONE);
            }
            if (cons == Consistency.ABSOLUTE) {
                return new ConsistencyType(Type.ABSOLUTE);
            }
            if (cons instanceof Consistency.Time) {
                Consistency.Time timeCons = (Consistency.Time) cons;
                return new ConsistencyType(
                    timeCons.getPermissibleLag(TimeUnit.MILLISECONDS),
                    timeCons.getTimeout(TimeUnit.MILLISECONDS));
            }
            throw new IllegalArgumentException(
                "Unsupported consistency type: " + cons.getName());
        }

        private Consistency toConsistency() {
            switch(type) {
            case NONE:
                return Consistency.NONE_REQUIRED;
            case ABSOLUTE:
                return Consistency.ABSOLUTE;
            case TIME:
                return new Consistency.Time(permissibleLagMs,
                                            TimeUnit.MILLISECONDS,
                                            timeoutMs,
                                            TimeUnit.MILLISECONDS);
            default:
                throw new IllegalArgumentException(
                    "Unsupported consistency type: " + type);
            }
        }

        @Override
        public int hashCode() {
           return type.hashCode() + Long.hashCode(permissibleLagMs) +
                  Long.hashCode(timeoutMs);
        }
    }
}
