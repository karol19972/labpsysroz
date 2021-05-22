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

package oracle.kv.impl.tif.esclient.esResponse;

import java.io.IOException;

import oracle.kv.impl.tif.esclient.restClient.JsonResponseObjectMapper;
import oracle.kv.impl.tif.esclient.restClient.RestResponse;
import oracle.kv.impl.tif.esclient.restClient.RestStatus;

import com.fasterxml.jackson.core.JsonParser;

public class RefreshResponse extends ESResponse implements
        JsonResponseObjectMapper<RefreshResponse> {

    private boolean success = false;

    @Override
    public RefreshResponse buildFromJson(JsonParser parser) throws IOException {
        return null;
    }

    @Override
    public RefreshResponse buildErrorReponse(ESException e) {
    	 if (e != null) {
             if (e.errorStatus() == RestStatus.NOT_FOUND) {
                 success = true;
                 this.statusCode(RestStatus.NOT_FOUND.getStatus());
             }
         } else {
             success = false;
         }

         return this;
    }

    @Override
    public RefreshResponse buildFromRestResponse(RestResponse restResp)
        throws IOException {
        if (restResp.statusLine().getStatusCode() == 200) {
            this.success = true;
        }
        parsed(true);
        return this;
    }

    public boolean isSuccess() {
        return success;
    }

}
