package com.ilscipio.scipio.accounting.external.datev.category;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityJoinOperator;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtil;

import com.ilscipio.scipio.accounting.external.BaseOperationResults;
import com.ilscipio.scipio.accounting.external.BaseOperationStats;
import com.ilscipio.scipio.accounting.external.BaseOperationStats.NotificationLevel;
import com.ilscipio.scipio.accounting.external.BaseOperationStats.NotificationScope;
import com.ilscipio.scipio.accounting.external.datev.DatevException;
import com.ilscipio.scipio.accounting.external.datev.DatevHelper;

public abstract class AbstractDatevDataCategory {

    protected final Delegator delegator;
    protected final DatevHelper datevHelper;

    private final List<GenericValue> datevMetadataFieldsDefinitions;
    private Map<String, Object> datevMetadataValues = new HashMap<>();

    private final List<GenericValue> datevFieldDefinitions;
    private final Map<String, GenericValue> datevFieldMappingsByField;
    private final List<String> datevFieldNames;

    public AbstractDatevDataCategory(Delegator delegator, DatevHelper datevHelper) throws DatevException {
        this.delegator = delegator;
        this.datevHelper = datevHelper;

        try {
            EntityCondition datevFieldCommonCond = EntityCondition.makeCondition("dataCategoryId", EntityJoinOperator.EQUALS,
                    datevHelper.getDataCategory().getString("dataCategoryId"));

            this.datevFieldDefinitions = EntityQuery.use(delegator).from("DatevFieldDefinition").where(datevFieldCommonCond).queryList();
            this.datevFieldNames = EntityUtil.getFieldListFromEntityList(datevFieldDefinitions, "fieldName", true);

            List<String> datevFieldIds = EntityUtil.getFieldListFromEntityList(datevFieldDefinitions, "fieldId", true);
            Map<String, GenericValue> datevFieldMappingsByField = new HashMap<>();
            List<GenericValue> datevFieldMappings = EntityQuery.use(delegator).from("DatevFieldMapping").where(datevFieldCommonCond).queryList();
            for (String fieldId : datevFieldIds) {
                datevFieldMappingsByField.put(fieldId, EntityUtil.getFirst(EntityUtil.filterByAnd(datevFieldMappings, UtilMisc.toMap("fieldId", fieldId))));
            }
            this.datevFieldMappingsByField = datevFieldMappingsByField;

            this.datevMetadataFieldsDefinitions = EntityQuery.use(delegator).from("DatevMetadata").queryList();
        } catch (GenericEntityException e) {
            throw new DatevException("Internal error. Cannot initialize DATEV importer tool.", e);
        }
    }

    static enum DatevFieldType {
        TEXT(String.class), NUMBER(Number.class), AMOUNT(BigDecimal.class), DATE(DateTime.class), BOOLEAN(Boolean.class), ACCOUNT(String.class);

        private final Class<?> fieldTypeClass;

        DatevFieldType(Class<?> clazz) {
            this.fieldTypeClass = clazz;
        }

        public Class<?> getFieldTypeClass() {
            return fieldTypeClass;
        }

        @Override
        public String toString() {
            return "FieldTypeName [" + this.name() + "]: " + fieldTypeClass.getName();
        }

    }

    public abstract void processRecord(int index, Map<String, String> recordMap) throws DatevException;

    public abstract boolean validateField(String fieldName, String value) throws DatevException;

    public abstract boolean validateField(int position, String value) throws DatevException;

    public abstract Class<? extends BaseOperationStats> getOperationStatsClass() throws DatevException;

    public abstract Class<? extends BaseOperationResults> getOperationResultsClass() throws DatevException;

    public List<GenericValue> getDatevFieldDefinitions() {
        return datevFieldDefinitions;
    }

    public String[] getDatevFieldNames() {
        String[] fieldNames = new String[datevFieldNames.size()];
        return datevFieldNames.toArray(fieldNames);
    }

    public Map<String, Object> getDatevMetadataValues() {
        return datevMetadataValues;
    }

    public Map<String, GenericValue> getDatevMappingDefinitions() {
        return datevFieldMappingsByField;
    }

    public boolean isMetaHeader(Iterator<String> metaHeaderIter) throws DatevException {
        boolean hasMetaHeader = true;
        for (int i = 0; metaHeaderIter.hasNext(); i++) {
            GenericValue fieldDefinition = null;
            try {
                fieldDefinition = datevMetadataFieldsDefinitions.get(i);
            } catch (IndexOutOfBoundsException e) {
                datevHelper.addStat("Metadata header size doesn't match the expected size [" + datevMetadataFieldsDefinitions.size() + "]", NotificationScope.META_HEADER,
                        NotificationLevel.WARNING);
                hasMetaHeader = false;
                break;
            }
            if (UtilValidate.isNotEmpty(fieldDefinition)) {
                String metaHeaderValue = metaHeaderIter.next();
                boolean isMetadataFieldValid = validateField(fieldDefinition, metaHeaderValue);
                if (!isMetadataFieldValid) {
                    datevHelper.addStat("Metadata header field [" + fieldDefinition.getString("metadataId") + "] is not valid for value <" + metaHeaderValue + ">",
                            NotificationScope.META_HEADER, NotificationLevel.WARNING);
                }
                datevMetadataValues.put(fieldDefinition.getString("metadataId"), UtilMisc.toMap(metaHeaderValue, isMetadataFieldValid));
            }
            if (i > datevMetadataFieldsDefinitions.size() - 1) {
                datevHelper.addStat("Metadata header size doesn't match the expected size [" + datevMetadataFieldsDefinitions.size() + "]", NotificationScope.META_HEADER,
                        NotificationLevel.WARNING);
                hasMetaHeader = false;
            }
        }

        return hasMetaHeader;
    }

    protected boolean validateField(GenericValue fieldDefinition, String value) {
        String fieldName = "";
        if (fieldDefinition.getModelEntity().isField("fieldName")) {
            fieldName = fieldDefinition.getString("fieldName");
        }
        String type = fieldDefinition.getString("typeEnumId");
        try {
            long length = -1;
            if (UtilValidate.isNotEmpty(fieldDefinition.get("length"))) {
                length = fieldDefinition.getLong("length");
            }
            long scale = 0;
            if (UtilValidate.isNotEmpty(fieldDefinition.get("scale"))) {
                scale = fieldDefinition.getLong("scale");
            }
            long maxLength = -1;
            if (UtilValidate.isNotEmpty(fieldDefinition.get("maxLength"))) {
                maxLength = fieldDefinition.getLong("maxLength");
            }
            String format = null;
            if (UtilValidate.isNotEmpty(fieldDefinition.get("format"))) {
                format = fieldDefinition.getString("format");
            }
            boolean required = false;
            if (UtilValidate.isNotEmpty(fieldDefinition.get("required"))) {
                required = fieldDefinition.getBoolean("required");
            }

            GenericValue fieldTypeEnum = fieldDefinition.getRelatedOne("DatevFieldTypeEnumeration", true);
            DatevFieldType datevFieldType = DatevFieldType.valueOf(fieldTypeEnum.getString("enumCode"));

            if (Debug.isOn(Debug.VERBOSE)) {
                Debug.log("Validating datev field [" + fieldName + "]:" + "\r\n\t type: " + fieldTypeEnum.getString("enumCode") + "\r\n\t length: " + length + "\r\n\t scale: "
                        + scale + "\r\n\t maxLength: " + maxLength + "\r\n\t format: " + format + "\r\n\t required: " + required);
            }

            if (UtilValidate.isEmpty(value)) {
                if (required) {
                    datevHelper.addStat("Required field [" + fieldName + "] has no value", NotificationScope.RECORD, NotificationLevel.ERROR);
                    return false;
                } else {
                    return true;
                }
            }

            if (maxLength > 0 && value.length() > maxLength) {
                datevHelper.addStat("Field [" + fieldName + "] length <" + value.length() + "> is greater than the max value allowed for it <" + maxLength + ">",
                        NotificationScope.RECORD, NotificationLevel.ERROR);
                return false;
            }

            GenericValue settings = datevHelper.getDataCategorySettings();
            Object validatedValue = null;
            switch (datevFieldType) {
            case TEXT:
                validatedValue = value;
                break;
            case BOOLEAN:
                validatedValue = Boolean.valueOf(value);
                break;
            case DATE:
                DateTimeFormatter dtf = null;
                if (UtilValidate.isNotEmpty(format)) {
                    dtf = DateTimeFormat.forPattern(format);
                }
                validatedValue = DateTime.parse(value, dtf);
                break;
            case NUMBER:
                validatedValue = Integer.valueOf(value);
                break;
            case AMOUNT:
                DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance();
                if (UtilValidate.isNotEmpty(settings.get("decimalSeparator"))) {
                    dfs.setDecimalSeparator(settings.getString("decimalSeparator").charAt(0));
                }
                if (UtilValidate.isNotEmpty(settings.get("thousandsSeparator"))) {
                    dfs.setGroupingSeparator(settings.getString("thousandsSeparator").charAt(0));
                }
                DecimalFormat df = new DecimalFormat();
                df.setDecimalFormatSymbols(dfs);

                validatedValue = BigDecimal.valueOf(df.parse(value).doubleValue());
                if (UtilValidate.isNotEmpty(validatedValue) && UtilValidate.isNotEmpty(scale)) {
                    validatedValue = ((BigDecimal) validatedValue).setScale(Math.toIntExact(scale));
                }
                break;
            case ACCOUNT:
                // TODO: Review this
                validatedValue = value;
                break;
            default:
                datevHelper.addStat("Type [" + type + "] is not supported for value: " + value, NotificationScope.RECORD, NotificationLevel.WARNING);
                return false;
            }
        } catch (Exception e) {
            datevHelper.addStat("Can't convert [" + value + "] to type " + type + " for field <" + fieldDefinition.getString("fieldName") + ">", NotificationScope.RECORD,
                    NotificationLevel.WARNING);
            return false;
        }

        return true;
    }

}
