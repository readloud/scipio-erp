package com.ilscipio.scipio.ce.demoSuite.dataGenerator.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ofbiz.base.conversion.JSONConverters;
import org.ofbiz.base.conversion.JSONConverters.JSONToList;
import org.ofbiz.base.lang.JSON;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.HttpClient;
import org.ofbiz.base.util.HttpClientException;

import com.ilscipio.scipio.ce.demoSuite.dataGenerator.AbstractDataGenerator;
import com.ilscipio.scipio.ce.demoSuite.dataGenerator.dataObject.AbstractDataObject;
import com.ilscipio.scipio.ce.demoSuite.dataGenerator.helper.AbstractDemoDataHelper;
import com.ilscipio.scipio.ce.demoSuite.dataGenerator.helper.MockarooDemoDataHelper;
import com.ilscipio.scipio.ce.demoSuite.dataGenerator.helper.MockarooDemoDataHelper.MockarooSettings;

public class MockarooDataGenerator extends AbstractDataGenerator {
    private static String MOCKAROO_DATA_GENERATOR = "mockaroo";

    private final MockarooDemoDataHelper helper;

    public MockarooDataGenerator(AbstractDemoDataHelper helper) {
        super(helper);
        this.helper = (MockarooDemoDataHelper) helper;
    }

    @Override
    public List<? extends AbstractDataObject> retrieveData() throws Exception {
        // if (UtilValidate.isEmpty(args))
        // throw new Exception("Invalid arguments. This engine requires one
        // argument to be passed that represents the api");
        // String api = args[0];
        // FIXME: Find a way to pass api properly
        //String api = "";
        HttpClient httpClient = new HttpClient();
        MockarooSettings settings = helper.getSettings();
        String format = settings.getExportFormat();
        StringBuilder url = new StringBuilder(settings.getUrl());
        url.append(settings.getApi());
        url.append("." + format);
        url.append("?key=" + settings.getKey());
        url.append("&count=" + helper.getCount() + "&array=true");
        httpClient.setContentType("application/json");
        httpClient.setUrl(url.toString());
        try {
            httpClient.setAllowUntrusted(true);
            String r = httpClient.sendHttpRequest(settings.getMethod());

            JSONToList jsonListConverter = new JSONConverters.JSONToList();
            List<Object> converted = jsonListConverter.convert(JSON.from(r));
            List<AbstractDataObject> resultList = new ArrayList<>(converted.size());
            for (Object o : converted) {
                resultList.add(handleData(o, format));
            }

            return resultList;

        } catch (HttpClientException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getDataGeneratorName() {
        return MOCKAROO_DATA_GENERATOR;
    }

    @Override
    public AbstractDataObject handleData(Object result, String format) {
        if (format.equals("json")) {
            try {
                Object o = JSON.from(result);
                return (AbstractDataObject) JSON.from(o).toObject(helper.getReturnObjectClass());
            } catch (IOException e) {
                Debug.logError(e.getMessage(), "");
            }
        } else {
            throw new UnsupportedOperationException("Export format " + format + " currently not supported");
        }
        return null;
    }

}
