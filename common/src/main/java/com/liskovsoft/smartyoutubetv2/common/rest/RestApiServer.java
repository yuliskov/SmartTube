package com.liskovsoft.smartyoutubetv2.common.rest;

import com.liskovsoft.sharedutils.mylogger.Log;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.IStatus;
import fi.iki.elonen.NanoHTTPD.Response.Status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class RestApiServer extends NanoHTTPD {
    private static final String TAG = RestApiServer.class.getSimpleName();
    private final RequestHandler mRequestHandler;

    interface RequestHandler {
        RestResponse handle(RestRequest request);
    }

    static final class RestRequest {
        final String method;
        final String path;
        final Map<String, String> query;
        final Map<String, String> headers;

        RestRequest(String method, String path, Map<String, String> query, Map<String, String> headers) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.headers = headers;
        }
    }

    static final class RestResponse {
        final int status;
        final String body;

        RestResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    RestApiServer(int port, RequestHandler requestHandler) {
        super(port);
        mRequestHandler = requestHandler;
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            RestRequest request = parseRequest(session);
            RestResponse response = mRequestHandler.handle(request);
            return buildNanoResponse(response);
        } catch (Exception e) {
            Log.e(TAG, "Serve failed: %s", e.getMessage());
            return newFixedLengthResponse(Status.INTERNAL_ERROR, "application/json", "{\"error\":\"Internal error\"}");
        }
    }

    private RestRequest parseRequest(IHTTPSession session) {
        String method = session.getMethod().name();
        String path = session.getUri();
        Map<String, List<String>> parameters = session.getParameters();
        Map<String, String> query = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            List<String> values = entry.getValue();
            query.put(entry.getKey(), values != null && !values.isEmpty() ? values.get(0) : "");
        }
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, String> entry : session.getHeaders().entrySet()) {
            headers.put(entry.getKey().toLowerCase(), entry.getValue());
        }
        return new RestRequest(method, path, query, headers);
    }

    private Response buildNanoResponse(RestResponse response) {
        IStatus nanoStatus = Status.lookup(response.status);
        if (nanoStatus == null) {
            nanoStatus = new CustomStatus(response.status, getStatusText(response.status));
        }
        String body = response.body != null ? response.body : "{}";
        Response nanoResponse = newFixedLengthResponse(nanoStatus, "application/json; charset=utf-8", body);
        nanoResponse.addHeader("Access-Control-Allow-Origin", "*");
        nanoResponse.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        nanoResponse.addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
        return nanoResponse;
    }

    private static final class CustomStatus implements IStatus {
        private final int mStatusCode;
        private final String mDescription;

        CustomStatus(int statusCode, String description) {
            mStatusCode = statusCode;
            mDescription = description;
        }

        @Override
        public String getDescription() {
            return mStatusCode + " " + mDescription;
        }

        @Override
        public int getRequestStatus() {
            return mStatusCode;
        }
    }

    private static String getStatusText(int status) {
        switch (status) {
            case 200:
                return "OK";
            case 201:
                return "Created";
            case 400:
                return "Bad Request";
            case 401:
                return "Unauthorized";
            case 404:
                return "Not Found";
            case 422:
                return "Unprocessable Entity";
            case 500:
                return "Internal Server Error";
            default:
                return "OK";
        }
    }
}
