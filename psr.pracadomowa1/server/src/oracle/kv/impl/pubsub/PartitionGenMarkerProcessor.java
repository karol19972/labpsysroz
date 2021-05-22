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


package oracle.kv.impl.pubsub;

import static com.sleepycat.je.utilint.VLSN.NULL_VLSN;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.RequestTimeoutException;
import oracle.kv.impl.rep.migration.generation.PartitionGenDBManager;
import oracle.kv.impl.rep.migration.generation.PartitionGenNum;
import oracle.kv.impl.rep.migration.generation.PartitionGeneration;
import oracle.kv.impl.topo.PartitionId;
import oracle.kv.impl.topo.RepGroupId;
import oracle.kv.impl.util.PollCondition;
import oracle.kv.pubsub.CheckpointFailureException;
import oracle.kv.pubsub.NoSQLSubscriberId;
import oracle.kv.pubsub.StreamPosition;
import oracle.kv.pubsub.SubscriptionFailureException;

import com.sleepycat.je.utilint.VLSN;

/**
 * Object represents the module to process partition generation marker. It is
 * used by stream client open transaction buffer to process the streamed
 * entries from feeder that represents the beginning and end of a partition
 * generation.
 */
class PartitionGenMarkerProcessor {

    /* default wait time in ms for green light from another shard */
    private final static long POLL_TIME_OUT_MS = 10 * 60 * 1000;

    /* polling interval in ms */
    private final static int POLL_INTERVAL_MS = 5000;

    /* parent open txn buffer */
    private final OpenTransactionBuffer otb;

    /* private logger */
    private final Logger logger;

    /* poll time out in ms */
    private volatile long pollTimeOutMs;

    PartitionGenMarkerProcessor(OpenTransactionBuffer otb,
                                Logger logger) {
        this.otb = otb;
        this.logger = logger;

        /* unless in unit test, we should always use default timeout */
        pollTimeOutMs = POLL_TIME_OUT_MS;
    }

    /**
     * Processes an entry from partition generation db
     *
     * @param entry an entry from partition generation db
     *
     * @return true if the entry from partition generation db and processed,
     * false if the entry is not from partition generation db
     */
    boolean process(DataEntry entry) {
        if (!isFromPartMDDB(entry)) {
            return false;
        }

        final PartitionGeneration gen =
            PartitionGenDBManager.readPartGenFromVal(entry.getValue());
        if (gen.isOpen()) {
            processOpenGen(gen, entry.getVLSN());
            return true;
        }

        processClosedGen(gen, entry.getVLSN());
        return true;
    }

    /**
     * returns true if the entry is from partition generation db, false
     * otherwise.
     *
     * @param entry input entry
     *
     * @return true if the entry from partition generation db
     */
    private boolean isFromPartMDDB(DataEntry entry) {
        return entry.getDbId().equals(otb.getParentRSC()
                                         .getRSCStat()
                                         .getPartGenDBId());
    }

    /**
     * Processes the open generation. It may block the stream till its
     * previous generation has been completely streamed from other shard. It
     * is required to maintain the correct order of streamed events with
     * migration. For more background and technical details, please refer to
     * [#26662]
     *
     * @param gen the open generation
     * @param vlsn vlsn of the entry that represents the open generation
     *
     * @throws SubscriptionFailureException if interrupted or timeout during
     * wait for stream of previous generation to catch up
     */
    private void processOpenGen(PartitionGeneration gen, long vlsn)
        throws SubscriptionFailureException {

        assert (gen.isOpen());

        /*
         * Generation zero represent the very first generation when store is
         * created. Stream client can safely ignore such entries to open
         * generation zero, because there is no previous generation of
         * generation zero to wait for.
         */
        final PartitionId pid = gen.getPartId();
        if (gen.getGenNum().equals(PartitionGenNum.generationZero())) {
            logger.log(Level.INFO,
                       () -> lm("Received generation zero for " + pid));
            /* no need to wait */
            return;
        }

        logger.log(Level.INFO, () -> lm("Receive open generation " + gen +
                                        ", wait for checkpoint of " +
                                        gen.getPrevGenRepGroup() +
                                        " to reach or be over " +
                                        gen.getPrevGenEndVLSN()));

        /*
         * Blocks streaming for given generation until condition is met, or
         * timeout, or interrupted. The condition is met when the checkpoint of
         * previous owning shard has advanced to or beyond the last VLSN of the
         * previous generation. At that time, all writes from previous generation
         * has been streamed from previous owning shard, and it is safe to resume
         * streaming from current shard, after making a checkpoint.
         *
         * For details and more background, please see [#26662].
         */
        final long ts = System.currentTimeMillis();
        if (PollCondition.await(POLL_INTERVAL_MS, pollTimeOutMs,
                                () -> ckptCatchup(gen.getPrevGenRepGroup(),
                                                  gen.getPrevGenEndVLSN()))) {
            logger.log(Level.INFO,
                       () -> lm(gen.getPartId() + ":" +
                                gen.getPrevGenRepGroup() +
                                " streamed all changes, resume stream from " +
                                otb.getRepGroupId() +
                                ", waiting time in ms: " +
                                (System.currentTimeMillis() - ts)));
        } else {
            /*
             * Time out in waiting for the green light, have to fail the
             * subscription to signal user that we cannot guarantee that all
             * events from the previous shard are streamed.
             *
             * Before terminating the stream, we make a checkpoint at the
             * open generation vlsn so that user can resume the stream after
             * the gap. The resumed stream will start from the next VLSN
             * after generation is opened.
             */
            final String err = "Timeout (in ms: " + pollTimeOutMs +
                               ") or interrupted in waiting for open " +
                               "generation (" + gen + ") to catch up in " +
                               "previous shard. Terminate stream after " +
                               "checkpoint.";
            logger.log(Level.WARNING, () -> lm(err));

            final PublishingUnit pu = otb.getParentRSC().getPu();
            final NoSQLSubscriberId sid = pu.getSubscriberId();
            try {
                pu.getCkptTableManager().updateElasticShardCkpt(
                    pu.getStoreName(), pu.getStoreId(), otb.getRepGroupId(),
                    vlsn);
            } catch (CheckpointFailureException cfe) {
                logger.log(Level.WARNING,
                           () ->  "Fail to checkpoint " + otb.getRepGroupId() +
                                  " at " + vlsn + ", " + cfe.getMessage() +
                                  (cfe.getCause() == null ? "" :
                                      ", " + cfe.getCause().getMessage()));
            }

            /*
             * TODO: in the case of store contraction, we may want to ping
             * the shard to sef if it is still live, instead of waiting and
             * timing out if the shard is gone. However it is unclear how to
             * verify if the whole shard, rather than a single node, is still
             * live.
             */

            /* The SFE will surface, close PU and terminate the stream */
            throw new SubscriptionFailureException(sid, err);
        }

        /*
         * Got the green light
         *
         * Make a checkpoint to ensure we wont go back beyond it if failure
         * happens.
         *
         * Now all writes from previous generation has already streamed, and
         * we shall never go back beyond this point if failure happens,
         * otherwise the we may end up re-streaming some old generation from
         * this shard only but without re-streaming old generation from
         * other shards, resulting out-of-order events streamed to subscriber.
         *
         * Here is a simple example:
         * Time 1: partition P1 at RG1 with generation 100
         * Time 2: partition P1 moved to RG2 with generation 101
         * Time 3: partition P1 moved back to RG1 with generation 102
         * Time 4 (Now): we know RG2 has streamed all writes of P1 with
         * generation 101, and if failure happens at RG1 later, RG1 should not
         * restart stream from earlier position that may stream writes from
         * generation 100 on this shard.
         */
        try {
            final PublishingUnit pu = otb.getParentRSC().getPu();
            pu.getCkptTableManager().updateElasticShardCkpt(
                pu.getStoreName(), pu.getStoreId(), otb.getRepGroupId(), vlsn);
        } catch (CheckpointFailureException cfe) {
            final NoSQLSubscriberId sid = otb.getParentRSC().getPu()
                                             .getSubscriberId();
            final String err =  "Fail to checkpoint " + otb.getRepGroupId() +
                                " at " + vlsn + " for open generation " + gen;
            logger.log(Level.WARNING, () -> err);
            /* we must make the checkpoint, otherwise fail the subscription */
            throw new SubscriptionFailureException(sid, err, cfe);
        }

        /* add a new partition to RSC stat */
        logger.log(Level.INFO,
                   () -> lm("Continue stream " + gen.getPartId() +
                            " (gen: " + gen.getGenNum() + ")" +
                            ", checkpoint at " + vlsn));
    }

    /**
     * Processes the closed generation. It updates the checkpoint table with
     * the end vlsn for given shard.
     *
     * @param gen  the closed generation
     * @param vlsn vlsn of the entry that represents the closed generation
     */
    private void processClosedGen(PartitionGeneration gen, long vlsn) {

        try {
            final long ts = System.currentTimeMillis();
            final PublishingUnit pu = otb.getParentRSC().getPu();
            final StreamPosition ckpt =
                pu.getCkptTableManager().updateElasticShardCkpt(
                    pu.getStoreName(), pu.getStoreId(), otb.getRepGroupId(),
                    vlsn);
            logger.log(Level.INFO,
                       () -> lm("Received closed generation " + gen +
                                ", successfully update checkpoint to " +
                                ckpt + ", elapsed time in ms: " +
                                (System.currentTimeMillis() - ts)));
            if (gen.isAllPartClosed()) {
                otb.getParentRSC().setAllPartClosed();
                logger.log(Level.INFO,
                           () -> lm("All generations have closed and " +
                                    "streamed"));
            }
        } catch (CheckpointFailureException cfe) {
            /*
             * Fail to checkpoint, we continue streaming from this shard. The
             * consequence is the waiting open txn buffer (could be in same
             * or a separate subscriber) may have to wait longer to the next
             * successful checkpoint.
             */
            logger.log(Level.WARNING,
                       () -> lm(
                           "Fail to make checkpoint for closed generation " +
                           gen +
                           ", continue streaming"));
        }
    }

    private String lm(String msg) {
        return "[PartGenMarkerProc-" + otb.getParentRSC().getConsumerId() +
               "] " + msg;
    }

    /*
     * Returns true if checkpoint vlsn for given shard must equal or
     * beyond than the given vlsn
     */
    private boolean ckptCatchup(RepGroupId gid, long vlsn) {
        final long lastCkptVLSN = getCkptVLSN(gid);
        if (VLSN.isNull(lastCkptVLSN)) {
            logger.log(Level.INFO,
                       () -> lm("Checkpoint for " + gid + " does not exist."));
            return false;
        }

        if (lastCkptVLSN < vlsn) {
            logger.log(Level.INFO,
                       () -> lm("Last checkpoint VLSN at rep " +
                                "group " + gid + " is " + lastCkptVLSN +
                                ", earlier than " + vlsn));
            return false;
        }

        logger.log(Level.INFO,
                   () -> lm("Last checkpoint VLSN at rep group " +
                            gid + " is " + lastCkptVLSN +
                            ", equal or later than " + vlsn));
        return true;
    }

    /* Retrieves the last checkpoint vlsn for given shard from server */
    private long getCkptVLSN(RepGroupId gid) {

        try {
            /* only get the ckpt for given shard */
            final Set<RepGroupId> shards =
                new HashSet<>(Collections.singletonList(gid));
            final StreamPosition ckpt = otb.getParentRSC().getPu()
                                           .getCkptTableManager()
                                           .fetchElasticCkpt(shards);

            if (ckpt == null) {
                logger.log(Level.INFO, () -> lm("No checkpoint is found" +
                                                " for " + gid));
                return NULL_VLSN;
            }

            final StreamPosition.ShardPosition shardPos =
                ckpt.getShardPosition(gid.getGroupId());
            if (shardPos == null) {
                /*
                 * ckpt for the given shard does not exist in the checkpoint
                 * record. Possibly because this shard is a new shard
                 */
                logger.log(Level.INFO,
                           () -> lm("No position for shard " + gid +
                                    "is found in checkpoint" + ckpt));
                return NULL_VLSN;
            }

            return shardPos.getVLSN();
        } catch (RequestTimeoutException rte) {
            /* time out in querying server */
            logger.warning(() -> lm("Timeout in querying checkpoint: " +
                                    rte.getMessage()));
            /*
             * unable to get the checkpoint, simply return null vlsn and
             * the caller will retry after sleep since null vlsn is
             * earlier than any vlsn.
             */
            return NULL_VLSN;
        } catch (Exception exp) {
            logger.warning(() -> lm("Error in querying checkpoint: " +
                                    exp.getMessage()));
            /*
             * unable to get the checkpoint, simply return null vlsn and
             * the caller will retry after sleep since null vlsn is
             * earlier than any vlsn.
             */
            return NULL_VLSN;
        }
    }

    /**
     * Used in unit test only to shorten test running time
     */
    void setWaitTimeout(int waitTimeoutMs) {
        pollTimeOutMs = waitTimeoutMs;
    }
}
