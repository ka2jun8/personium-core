/**
 * personium.io
 * Copyright 2017-2018 FUJITSU LIMITED
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.personium.core.rule.action;

import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;

import org.json.simple.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.personium.common.utils.PersoniumCoreUtils;
import io.personium.core.event.PersoniumEvent;
import io.personium.core.model.Cell;
import io.personium.core.rule.ActionInfo;

/**
 * Abstract class of Action about Engine.
 */
public abstract class EngineAction extends Action {
    static Logger logger = LoggerFactory.getLogger(EngineAction.class);

    Cell cell;
    String service;
    String action;
    String eventId;
    String chain;

    /**
     * Constructor.
     * @param cell target cell object
     * @param ai ActionInfo object
     */
    public EngineAction(Cell cell, ActionInfo ai) {
        this.cell = cell;
        this.service = ai.getService();
        this.action = ai.getAction();
        this.eventId = ai.getEventId();
        this.chain = ai.getRuleChain();
    }

    @Override
    @SuppressWarnings({ "deprecation", "unchecked" })
    public PersoniumEvent execute(PersoniumEvent event) {
        String requestUrl = getRequestUrl();
        if (requestUrl == null) {
            return null;
        }

        HttpClient client = new org.apache.http.impl.client.DefaultHttpClient();
        HttpPost req = new HttpPost(requestUrl);

        // create payload as JSON
        JSONObject json = new JSONObject();
        json.put("External", event.getExternal());
        if (event.getSchema() != null) {
            json.put("Schema", event.getSchema());
        }
        if (event.getSubject() != null) {
            json.put("Subject", event.getSubject());
        }
        json.put("Type", event.getType());
        json.put("Object", event.getObject());
        json.put("Info", event.getInfo());

        // add specific events to payload in derrived class
        addEvents(json);

        req.setEntity(new StringEntity(json.toString(), ContentType.create("application/json")));

        // set headers
        //  X-Personium-RequestKey, X-Personium-EventId, X-Personium-RuleChain
        req.addHeader(PersoniumCoreUtils.HttpHeaders.X_PERSONIUM_REQUESTKEY, event.getRequestKey());
        req.addHeader(PersoniumCoreUtils.HttpHeaders.X_PERSONIUM_EVENTID, eventId);
        req.addHeader(PersoniumCoreUtils.HttpHeaders.X_PERSONIUM_RULECHAIN, chain);

        // set specific headers in derrived class
        setHeaders(req);

        HttpResponse objResponse = null;
        try {
            objResponse = client.execute(req);
            logger.info(EntityUtils.toString(objResponse.getEntity()));
        } catch (ClientProtocolException e) {
            logger.error("Invalid Http response: " + e.getMessage(), e);
            return null;
        } catch (Exception e) {
            logger.error("Connection Error: " + e.getMessage(), e);
            return null;
        }

        String result;
        if (objResponse != null) {
            result = Integer.toString(objResponse.getStatusLine().getStatusCode());
        } else {
            result = "404";
        }

        PersoniumEvent evt = new PersoniumEvent(PersoniumEvent.INTERNAL_EVENT,
                null, null, action, service, result, event.getRequestKey(), eventId, chain);
        evt.setCellId(cell.getId());

        return evt;
    }

    @Override
    public PersoniumEvent execute(PersoniumEvent[] events) {
        // not supported
        return null;
    }

    /**
     * Create request url in derrived class.
     * @return created request url, null if error is occurred
     */
    protected abstract String getRequestUrl();

    /**
     * Add specific events in derrived class.
     * @param json JSONObject to add events
     */
    protected abstract void addEvents(JSONObject json);

    /**
     * Set specific HTTP headers in derrived class.
     * @param req Request to set headers
     */
    protected abstract void setHeaders(HttpMessage req);
}

