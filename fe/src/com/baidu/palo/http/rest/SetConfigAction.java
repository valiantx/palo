// Copyright (c) 2017, Baidu.com, Inc. All Rights Reserved

// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.baidu.palo.http.rest;

import com.baidu.palo.common.ConfigBase;
import com.baidu.palo.common.DdlException;
import com.baidu.palo.common.ConfigBase.ConfField;
import com.baidu.palo.http.ActionController;
import com.baidu.palo.http.BaseRequest;
import com.baidu.palo.http.BaseResponse;
import com.baidu.palo.http.IllegalArgException;

import com.google.common.collect.Maps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

import io.netty.handler.codec.http.HttpMethod;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/*
 * used to set fe config
 * eg:
 *  fe_host:http_port/api/_set_config?config_key1=config_value1&config_key2=config_value2&...
 */
public class SetConfigAction extends RestBaseAction {
    private static final Logger LOG = LogManager.getLogger(SetConfigAction.class);

    public SetConfigAction(ActionController controller) {
        super(controller);
    }

    public static void registerAction(ActionController controller) throws IllegalArgException {
        SetConfigAction action = new SetConfigAction(controller);
        controller.registerHandler(HttpMethod.GET, "/api/_set_config", action);
    }

    @Override
    public void execute(BaseRequest request, BaseResponse response) throws DdlException {
        checkAdmin(request);

        Map<String, List<String>> configs = request.getAllParameters();
        Map<String, String> setConfigs = Maps.newHashMap();
        Map<String, String> errConfigs = Maps.newHashMap();

        LOG.debug("get config from url: {}", configs);

        Field[] fields = ConfigBase.confClass.getFields();
        for (Field f : fields) {
            // ensure that field has "@ConfField" annotation
            ConfField anno = f.getAnnotation(ConfField.class);
            if (anno == null) {
                continue;
            }

            // ensure that field has property string
            String confKey = anno.value().equals("") ? f.getName() : anno.value();
            List<String> confVals = configs.get(confKey);
            if (confVals == null || confVals.isEmpty()) {
                continue;
            }

            if (confVals.size() > 1) {
                continue;
            }

            try {
                ConfigBase.setConfigField(f, confVals.get(0));
            } catch (Exception e) {
                continue;
            }

            setConfigs.put(confKey, confVals.get(0));
        }

        for (String key : configs.keySet()) {
            if (!setConfigs.containsKey(key)) {
                errConfigs.put(key, configs.get(key).toString());
            }
        }

        Map<String, Map<String, String>> resultMap = Maps.newHashMap();
        resultMap.put("set", setConfigs);
        resultMap.put("err", errConfigs);

        // to json response
        String result = "";
        ObjectMapper mapper = new ObjectMapper();
        try {
            result = mapper.writeValueAsString(resultMap);
        } catch (Exception e) {
            //  do nothing
        }

        // send result
        response.setContentType("application/json");
        response.getContent().append(result);
        sendResult(request, response);
    }

    public static void print(String msg) {
        System.out.println(System.currentTimeMillis() + " " + msg);
    }
}