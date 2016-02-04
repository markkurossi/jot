/*

  RestApi.java

  Copyright (c) 2013-2015, Markku Rossi
  All rights reserved.

  BSD 2-Clause License:

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are
  met:

  1. Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.

  2. Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
  COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
  ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
  POSSIBILITY OF SUCH DAMAGE.

*/

package fi.iki.mtr.restapi;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RestApi {
    private static Logger log = LoggerFactory.getLogger(RestApi.class);

    protected Response options(HttpServletRequest req) {
        return options(req, "POST,GET,PUT,DELETE");
    }

    protected Response options(HttpServletRequest req, String allowedMethods) {
        ResponseBuilder response = Response.ok();

        response.header("Access-Control-Max-Age", "3600");
        response.header("Access-Control-Allowed-Methods", allowedMethods);

        String reqHeaders = req.getHeader("Access-Control-Request-Headers");
        if (reqHeaders != null) {
            response.header("Access-Control-Allow-Headers", reqHeaders);
        }
        String origin = req.getHeader("Origin");
        if (origin != null) {
            response.header("Access-Control-Allow-Origin", origin);
        }

        return response.build();
    }

    public static String getAbsoluteURI(HttpServletRequest req) {
        return req.getRequestURL().toString();
    }

    protected ResponseBuilder makeError(Response.Status status) {
        return makeError(status, null);
    }

    protected ResponseBuilder makeError(Response.Status status,
                                        String message) {
        try {
            JSONObject json = new JSONObject();

            json.put("response_code", status.getStatusCode());
            if (message != null) {
                json.put("message", message);
            }

            return Response.status(status)
                .entity(json.toString())
                .type(MediaType.APPLICATION_JSON);
        } catch (JSONException e) {
            log.error("JSON error", e);
            throw new AssertionError(e);
        }
    }
}
