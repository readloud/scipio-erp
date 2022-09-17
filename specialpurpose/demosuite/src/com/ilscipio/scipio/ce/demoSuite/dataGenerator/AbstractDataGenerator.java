package com.ilscipio.scipio.ce.demoSuite.dataGenerator;

import java.util.List;

import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;

import com.ilscipio.scipio.ce.demoSuite.dataGenerator.dataObject.AbstractDataObject;
import com.ilscipio.scipio.ce.demoSuite.dataGenerator.helper.AbstractDemoDataHelper;

public abstract class AbstractDataGenerator {

    private final AbstractDemoDataHelper helper;

    public AbstractDataGenerator(AbstractDemoDataHelper helper) {
        this.helper = helper;
    }

    public abstract List<? extends AbstractDataObject> retrieveData() throws Exception;

    public AbstractDataObject handleData(Object result) {
        return handleData(result, null);
    }

    public abstract AbstractDataObject handleData(Object result, String format);

    public abstract String getDataGeneratorName();

    public AbstractDemoDataHelper getHelper() {
        return helper;
    }

    public abstract class DataGeneratorSettings {
        public DataGeneratorSettings() {
        }

        public List<Object> getFields(String dataType) throws UnsupportedOperationException {
            String fields = null;
            fields = helper.getProperties()
                    .getProperty("demosuite.test.data.provider." + getDataGeneratorName() + ".fields." + dataType);
            if (UtilValidate.isNotEmpty(fields)) {
                return UtilMisc.<Object>toListArray(fields.split(",\\s{0,1}"));
            }
            return null;
        }
    }

}
