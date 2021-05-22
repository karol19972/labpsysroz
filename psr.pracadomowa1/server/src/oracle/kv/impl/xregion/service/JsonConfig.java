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

package oracle.kv.impl.xregion.service;

import static oracle.kv.Durability.COMMIT_NO_SYNC;
import static oracle.kv.Durability.COMMIT_SYNC;
import static oracle.kv.Durability.COMMIT_WRITE_NO_SYNC;
import static oracle.kv.RequestLimitConfig.DEFAULT_MAX_ACTIVE_REQUESTS;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import oracle.kv.Durability;
import oracle.kv.impl.api.table.TableImpl;
import oracle.kv.xregion.XRService;
import oracle.nosql.common.JsonUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Objects represents the bootstrap configuration parameters in JSON. The
 * bootstrap configuration contains a small set of parameters that the
 * {@link XRegionService} needs to starts up. The required parameter
 * specifies:
 * - group size which is the number of agents serving the store
 * - group id which is index of the agent in the group, starting from 0
 * - name and helper host of the store the agent serves
 * - path to a writable directory
 * - security file if applies
 */
public class JsonConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /* default durability to write to target store */
    static final Durability DEFAULT_DURABILITY = COMMIT_NO_SYNC;

    /* supported durability */
    static final Map<String, Durability> ALLOWED_DURABILITY = new HashMap<>();
    static {
        ALLOWED_DURABILITY.put("COMMIT_NO_SYNC", COMMIT_NO_SYNC);
        ALLOWED_DURABILITY.put("COMMIT_SYNC", COMMIT_SYNC);
        ALLOWED_DURABILITY.put("COMMIT_WRITE_NO_SYNC", COMMIT_WRITE_NO_SYNC);
    }

    /* local path for agent */
    private String path;

    /* total number of agent in the group */
    private int agentGroupSize;

    /* 0-based index in group in [0, total-1] inclusively  */
    private int agentId;

    /* name of local region, case insensitive */
    private String region;

    /* name of kvstore */
    private String store;

    /* list of helper hosts */
    private String[] helpers;

    /* path to security file, null if non-secure store */
    private String security;

    /* region set */
    private Set<RegionInfo> regions;

    /* optional parameter to specify the durability */
    private String durability;

    /** Whether to enable encryption of table init checkpoint -- for testing */
    static final boolean DEFAULT_ENCRYPT_TABLE_CKPT = true;
    private boolean encryptTableCheckpoint = DEFAULT_ENCRYPT_TABLE_CKPT;

    /** password to generate cipher */
    //TODO: the password is stored in the json config for now. The
    // password should be stored in the wallet or password file. Another
    // possibility is to use a key, not a password, for encryption, and
    // store the key in the keystore.[KVSTORE-617].
    private String passwdFile = DEFAULT_CIPHER_PASSWORD_FILE;
    public static final String DEFAULT_CIPHER_PASSWORD_FILE = ("tic.pass");

    /*---------------------------*
     * STAT COLLECTION PARAMETER *
     *---------------------------*/
    /** stats report interval in seconds */
    static final int DEFAULT_STAT_REPORT_INTERVAL_SECS = 60;
    private int statIntervalSecs = DEFAULT_STAT_REPORT_INTERVAL_SECS;

    /** stats report TTL in days */
    static final long DEFAULT_STAT_REPORT_TTL_DAYS = 3;
    private long statTTLDays = DEFAULT_STAT_REPORT_TTL_DAYS;

    /*-----------------------------*
     * MR TABLE TRANSFER PARAMETER *
     *-----------------------------*/
    /** # of transferred rows to report progress */
    public static final int DEFAULT_ROWS_REPORT_PROGRESS_INTV = 100 * 1000;
    private int tableReportIntv = DEFAULT_ROWS_REPORT_PROGRESS_INTV;

    /** # of rows per read request in table iterator */
    public static final int DEFAULT_BATCH_SIZE_PER_REQUEST = 16;
    private int tableBatchSz = DEFAULT_BATCH_SIZE_PER_REQUEST;

    /** # of threads in table iterator, 0 if use default in iterator */
    public static final int DEFAULT_THREADS_TABLE_ITERATOR = 0;
    private int tableThreads = DEFAULT_THREADS_TABLE_ITERATOR;

    /*-------------------------*
     * REQUEST TABLE PARAMETER *
     *-------------------------*/
    /** request table polling interval in secs */
    public static final int DEFAULT_REQ_POLLING_INTV_SECS = 5;
    private int requestTablePollIntvSecs = DEFAULT_REQ_POLLING_INTV_SECS;

    /*-------------------*
     * SERVICE PARAMETER *
     *-------------------*/
    /**
     * The maximum number of concurrent table init checkpoint requests.
     * Normally one request per remote region.
     */
    private static final int DEFAULT_MAX_CONCURRENT_CKPT_REQUESTS = 5;
    /**
     * The maximum number of stream operations that should be processed
     * concurrently, it should be the default maximum in RequestLimitConfig
     * minus the default max concurrent checkpoint requests.
     */
    public static final int DEFAULT_MAX_CONCURRENT_STREAM_OPS =
        DEFAULT_MAX_ACTIVE_REQUESTS - DEFAULT_MAX_CONCURRENT_CKPT_REQUESTS;
    private int numConcurrentStreamOps = DEFAULT_MAX_CONCURRENT_STREAM_OPS;

    /** Checkpoint interval in seconds */
    public static final int DEFAULT_CKPT_INTERVAL_SECS = 5 * 60;
    private int checkpointIntvSecs = DEFAULT_CKPT_INTERVAL_SECS;

    /** Checkpoint interval in streamed ops */
    public static final int DEFAULT_CKPT_INTERVAL_OPS = 1024 * 1024;
    private int checkpointIntvOps = DEFAULT_CKPT_INTERVAL_OPS;


    /* derived agent root path, not part of json config file */
    @JsonIgnore
    private String agentRoot;

    /**
     * No args constructor for use in serialization,
     * used when constructing instance from JSON.
     */
    JsonConfig() {

    }

    /**
     * Constructs the configuration from parameters for secure store
     *
     * @param path      local path
     * @param agentGroupSize group size
     * @param agentId   agent id within group
     * @param region      name of local region
     * @param store     name of store
     * @param helpers   list of helper hosts
     * @param security  security file
     */
    public JsonConfig(String path,
                      int agentGroupSize,
                      int agentId,
                      String region,
                      String store,
                      Set<String> helpers,
                      String security) {

        this.path = path;
        this.agentGroupSize = agentGroupSize;
        this.agentId = agentId;
        this.region = region;
        this.store = store;
        this.helpers = helpers.toArray(new String[0]);
        this.security = security;
        regions = new HashSet<>();
        if (security != null && security.isEmpty()) {
            throw new IllegalArgumentException("Invalid security file");
        }
        agentRoot = null;
    }

    /**
     * Constructs the configuration from parameters for non-secure store
     *
     * @param path      local path
     * @param agentGroupSize group size
     * @param agentId   agent id within group
     * @param region      name of local region
     * @param store     name of store
     * @param helpers   list of helper hosts
     */
    public JsonConfig(String path,
                      int agentGroupSize,
                      int agentId,
                      String region,
                      String store,
                      Set<String> helpers) {
        this(path, agentGroupSize, agentId, region, store, helpers, null);
    }

    /**
     * Reads from Json file and construct an instance
     *
     * @param file full path name of Json file
     * @return an instance of target class
     */
    public static JsonConfig readJsonFile(String file) {
        try (FileInputStream fis = new FileInputStream(new File(file))) {
            final JsonConfig ret = JsonUtils.readValue(fis, JsonConfig.class);
            ret.validate();
            return ret;
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * Unit test only
     * <p>
     * Writes the bootstrap config to a JSON config file
     *
     * @param conf configuration class
     * @param path path to the file
     * @param file name of Json file
     */
    public static void writeBootstrapFile(JsonConfig conf,
                                          String path,
                                          String file) {
        try {
            final String jsonString = JsonUtils.print(conf, true);
            final File outFile = new File(path, file);
            final PrintWriter out = new PrintWriter(outFile);
            out.write(jsonString);
            out.close();
        } catch (FileNotFoundException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }
    }

    /**
     * Adds region to the list of regions
     *
     * @param rname name of region to add
     * @return true if region added successfully, false otherwise
     */
    public boolean addRegion(RegionInfo rname) {
        return regions.add(rname);
    }

    /**
     * Sets the path to security file, or null for no security.
     *
     * @param security the path to security file or null
     */
    public void setSecurity(String security) {
        this.security = security;
    }

    /**
     * Returns the array of regions
     *
     * @return the array of regions
     */
    public RegionInfo[] getRegions() {
        return regions.toArray(new RegionInfo[]{});
    }

    /**
     * Returns the size of group
     *
     * @return the size of group
     */
    public int getAgentGroupSize() {
        return agentGroupSize;
    }

    /**
     * Returns the agent id within the group
     *
     * @return the agent id within the group
     */
    public int getAgentId() {
        return agentId;
    }

    /**
     * Returns the name of kvstore
     *
     * @return the name of kvstore
     */
    public String getStore() {
        return store;
    }

    /**
     * Returns the security file or null if not secure.
     *
     * @return the security file or null
     */
    public String getSecurity() {
        return security;
    }

    /**
     * Returns the name of local region
     *
     * @return the name of local region
     */
    public String getRegion() {
        return region;
    }

    /**
     * Returns the list of helper hosts
     *
     * @return the list of helper hosts
     */
    public String[] getHelpers() {
        return helpers;
    }

    /**
     * Returns the local writable path
     *
     * @return the local writable path
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns the agent own root path
     *
     * @return the agent own root path
     */
    public String getAgentRoot() throws IOException {
        if (agentRoot == null) {
            agentRoot = ensureAgentRoot();
        }
        return agentRoot;
    }

    /**
     * Returns the durability in json config
     *
     * @return the durability in json config
     */
    public String getDurability() {
        return durability;
    }

    /**
     * Returns stats report interval in seconds
     *
     * @return stats report interval in seconds
     */
    public int getStatIntervalSecs() {
        return statIntervalSecs;
    }

    /**
     * Returns stats report TTL in days
     *
     * @return stats report TTL in days
     */
    public long getStatTTLDays() {
        return statTTLDays;
    }

    /**
     * Returns true if enable encrypting table checkpoint
     *
     * @return  true if enable encrypting table checkpoint
     */
    public boolean getEncryptTableCheckpoint() {
        return encryptTableCheckpoint;
    }

    /**
     * Returns a copy of password to generate cipher
     * @return password string
     */
    public String getPasswdFile() {
        return passwdFile;
    }

    /**
     * Test only
     * Sets the cipher password
     */
    public void setPasswdFile(String pass) {
        passwdFile = pass;
    }

    /**
     * Test only
     *
     * Sets durability
     *
     * @param durability durability
     */
    public void setDurability(String durability) {
        this.durability = durability;
    }

    /**
     * Test only
     *
     * Sets stat interval
     *
     * @param statIntervalSecs stat interval in secs
     */
    public void setStatIntervalSecs(int statIntervalSecs) {
        this.statIntervalSecs = statIntervalSecs;
    }

    /**
     * Test only
     *
     * Sets stats TTL in days
     *
     * @param statTTLDays stats TTL in days
     */
    public void setStatTTLDays(long statTTLDays) {
        this.statTTLDays = statTTLDays;
    }

    /**
     * Returns the durability setting
     *
     * @return the durability setting
     */
    public Durability durabilitySetting() {
        if (durability == null) {
            return DEFAULT_DURABILITY;
        }
        return ALLOWED_DURABILITY.get(durability);
    }

    /**
     * Gets number of rows to report table transfer progress
     * @return number of rows to report table transfer progress
     */
    public int getTableReportIntv() {
        return tableReportIntv;
    }

    /**
     * Test only
     *
     * Sets the rows report progress interval
     *
     * @param val new value
     */
    public void setTableReportIntv(int val) {
        tableReportIntv = val;
    }

    /**
     * Returns number of rows in batch of table iterator request
     * @return  number of rows in batch of table iterator request
     */
    public int getTableBatchSz() {
        return tableBatchSz;
    }

    /**
     * Sets number of rows in batch of table iterator request
     * @param val  number of rows in batch of table iterator request
     */
    public void setTableBatchSz(int val) {
        tableBatchSz = val;
    }

    /**
     * Gets the number of threads in table iterator
     * @return the number of threads in table iterator
     */
    public int getTableThreads() {
        return tableThreads;
    }

    /**
     * Sets # of threads in table iterator
     * @param val  # of threads in table iterator
     */
    public void setTableThreads(int val) {
        tableThreads = val;
    }

    /**
     * Gets the request table polling interval in seconds
     * @return the request table polling interval in seconds
     */
    public int getRequestTablePollIntvSecs() {
        return requestTablePollIntvSecs;
    }

    /**
     * Sets the request table polling interval in seconds
     * @param val value
     */
    public void setRequestTablePollIntvSecs(int val) {
        requestTablePollIntvSecs = val;
    }

    /**
     * Gets the number of concurrent stream ops
     * @return number of concurrent stream ops
     */
    public int getNumConcurrentStreamOps() {
        return numConcurrentStreamOps;
    }

    /**
     * Sets the number of concurrent stream ops
     * @param val  number of concurrent stream ops
     */
    public void setNumConcurrentStreamOps(int val) {
        numConcurrentStreamOps = val;
    }

    /**
     * Gets checkpoint interval in seconds
     * @return checkpoint interval in seconds
     */
    public int getCheckpointIntvSecs() {
        return checkpointIntvSecs;
    }

    /**
     * Sets checkpoint interval in seconds
     * @param val checkpoint interval in seconds
     */
    public void setCheckpointIntvSecs(int val) {
        checkpointIntvSecs = val;
    }

    /**
     * Gets checkpoint interval in number of ops
     * @return checkpoint interval in number of ops
     */
    public int getCheckpointIntvOps() {
        return checkpointIntvOps;
    }

    /**
     * Sets checkpoint interval in number of ops
     * @param val interval in number of ops
     */
    public void setCheckpointIntvOps(int val) {
        checkpointIntvOps = val;
    }

    /**
     * Returns true if the local region is a secure store
     *
     * @return true if the local region is a secure store
     */
    boolean isSecureStore() {
        return security != null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("JSON Config: agent group size=").append(agentGroupSize)
          .append(", agent id=").append(agentId)
          .append(", region=").append(region)
          .append(", store=").append(store)
          .append(", security file=")
          .append(isSecureStore() ? security : "non-secure store")
          .append(", helper hosts=")
          .append(Arrays.toString(helpers));
        if (!regions.isEmpty()) {
            sb.append("Regions:\n");
            regions.forEach(r -> sb.append(r).append("\n"));
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof JsonConfig)) {
            return false;
        }
        final JsonConfig other = (JsonConfig) obj;
        if (isSecureStore() != other.isSecureStore()) {
            return false;
        }
        if (isSecureStore() && !security.equals(other.security)) {
            return false;
        }
        return store.equals(other.store) &&
               region.equalsIgnoreCase(other.region) &&
               Arrays.equals(helpers, other.helpers) &&
               path.equals(other.path) &&
               agentGroupSize == other.agentGroupSize &&
               agentId == other.agentId;
    }

    @Override
    public int hashCode() {
        return region.hashCode() + store.hashCode() + Arrays.hashCode(helpers) +
               (isSecureStore() ? security.hashCode() : 0) +
               path.hashCode() + agentId + agentGroupSize;
    }

    /**
     * Ensures the service root directory, which is a subdirectory under the
     * path in configuration file named after the agent id.
     *
     * @return service root dir
     */
    private String ensureAgentRoot() throws IOException {
        final String sub = XRService.buildAgentId(this);
        final File dir = new File(path, sub);
        if (!dir.exists()) {
            if (!dir.mkdir()) {
                throw new IOException("Cannot create directory " +
                                      dir.getAbsolutePath());
            }
        }
        return dir.getAbsolutePath();
    }

    /* check required parameters */
    private void validate() {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("missing service path");
        }
        checkPath(path);
        if (region == null || region.isEmpty()) {
            throw new IllegalArgumentException("missing local region name");
        }
        validateRegionName(region);
        if (store == null || store.isEmpty()) {
            throw new IllegalArgumentException("missing local store name");
        }
        if (helpers == null || helpers.length == 0) {
            throw new IllegalArgumentException("missing local helper hosts");
        }
        if (agentGroupSize <= 0) {
            throw new IllegalArgumentException("invalid agent group size=" +
                                               agentGroupSize);
        }
        //TODO: remove the check after we support multiple agents
        if (agentGroupSize > 1) {
            throw new IllegalArgumentException("Only single agent is " +
                                               "supported, but agent group " +
                                               "size=" + agentGroupSize);
        }
        if (agentId < 0) {
            throw new IllegalArgumentException("invalid agent group id=" +
                                               agentId);
        }
        if (agentId > agentGroupSize - 1) {
            throw new IllegalArgumentException("agent id must be in the range" +
                                               " between 0 and " +
                                               "(agentGroupSize" +
                                               " - 1) both inclusively");
        }
        if (regions == null || regions.isEmpty()) {
            throw new IllegalArgumentException("at least one remote region " +
                                               "must be defined");
        }
        for (RegionInfo regionInfo : regions) {
            regionInfo.validate();
        }
        final Set<String> remote = regions.stream().map(RegionInfo::getName)
                                          .collect(Collectors.toSet());
        /* local region name cannot appear in remote regions */
        if (remote.contains(region)) {
            throw new IllegalArgumentException("local region=" + region +
                                               " cannot appear in the remote " +
                                               "region list");
        }
        /* # of regions > # of region names */
        if (regions.size() > remote.size()) {
            throw new IllegalArgumentException("Remote regions cannot have" +
                                               " duplicates");
        }
        if (!isAllowedDurability(durability)) {
            throw new IllegalArgumentException(
                "Unsupported durability=" + durability);
        }
        if (statIntervalSecs < 0) {
            throw new IllegalArgumentException("Invalid statistics report " +
                                               "interval in seconds=" +
                                               statIntervalSecs);
        }
        if (statTTLDays < 0) {
            throw new IllegalArgumentException("Invalid statistics report " +
                                               "TTL in days=" +
                                               statTTLDays);
        }
        if (tableReportIntv < 0) {
            throw new IllegalArgumentException("Invalid rows interval to " +
                                               "report table transfer " +
                                               "progress=" +
                                               tableReportIntv);
        }
        if (tableBatchSz < 0) {
            throw new IllegalArgumentException("Invalid batch size in table " +
                                               "request=" +
                                               tableBatchSz);
        }
        if (tableThreads < 0) {
            throw new IllegalArgumentException("Invalid number of threads in " +
                                               "table iterator=" +
                                               tableThreads);
        }
        if (requestTablePollIntvSecs < 1) {
            throw new IllegalArgumentException("Invalid request table polling" +
                                               " interval in seconds=" +
                                               requestTablePollIntvSecs);
        }
        if (numConcurrentStreamOps < 1) {
            throw new IllegalArgumentException("Invalid number of concurrent " +
                                               "streamed operations= " +
                                               numConcurrentStreamOps);
        }
        if (checkpointIntvSecs < 1) {
            throw new IllegalArgumentException("Invalid checkpoint interval " +
                                               " in seconds= " +
                                               checkpointIntvSecs);
        }
        if (checkpointIntvOps < 10) {
            throw new IllegalArgumentException("Invalid checkpoint interval " +
                                               " in number of operations= " +
                                               checkpointIntvOps);
        }
    }

    private static void checkPath(String path) {
        final File file= new File(path);
        if (!file.exists()) {
            throw new IllegalArgumentException(
                "path=" + path + " does not exist");
        }
        if (!file.canWrite()) {
            throw new IllegalArgumentException(
                "path=" + path + " is not writable, please check permission");
        }
        if (!file.canRead()) {
            throw new IllegalArgumentException(
                "path=" + path + " is not readable, please check permission");
        }
    }

    static void validateRegionName(String region) {
        TableImpl.validateRegionName(region);
    }

    private static boolean isAllowedDurability(String durability) {
        return durability == null /* optional parameter */ ||
               ALLOWED_DURABILITY.containsKey(durability);
    }
}

