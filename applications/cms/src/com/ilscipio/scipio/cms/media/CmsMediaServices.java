package com.ilscipio.scipio.cms.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.imageio.ImageIO;

import org.ofbiz.base.conversion.ConversionException;
import org.ofbiz.base.conversion.NumberConverters.StringToInteger;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.PropertyMessage;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.common.FindServices;
import org.ofbiz.common.image.ImageVariantConfig;
import org.ofbiz.content.image.ContentImageWorker;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.model.ModelEntity;
import org.ofbiz.entity.util.EntityListIterator;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.GeneralServiceException;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ModelService;
import org.ofbiz.service.ServiceUtil;

import com.ilscipio.scipio.cms.CmsServiceUtil;
import com.ilscipio.scipio.cms.ServiceErrorFormatter;
import com.ilscipio.scipio.cms.ServiceErrorFormatter.FormattedError;
import com.ilscipio.scipio.common.util.fileType.FileTypeException;
import com.ilscipio.scipio.common.util.fileType.FileTypeResolver;
import com.ilscipio.scipio.common.util.fileType.FileTypeUtil;

public abstract class CmsMediaServices {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    static final String logPrefix = "Cms: Media: ";
    private static final ServiceErrorFormatter errorFmt =
            CmsServiceUtil.getErrorFormatter().specialize().setDefaultLogMsgGeneral("Media Error").build();

    protected CmsMediaServices() {
    }

    /**
     * Generates a list of all available media files. Can be filtered by
     * DataResourceType (TODO).
     */
    public static Map<String, Object> getMediaFiles(DispatchContext dctx, Map<String, ?> context) {
        Delegator delegator = dctx.getDelegator();
        //LocalDispatcher dispatcher = dctx.getDispatcher();
        Map<String, Object> result = ServiceUtil.returnSuccess();
        // String dataResourceType = (String) (context.get("dataResourceType")
        // != null ? context.get("dataResourceType") : null);

        Integer viewIndex = (Integer) context.get("viewIndex") != null ? (Integer) context.get("viewIndex") : 0;
        Integer viewSize = (Integer) context.get("viewSize") != null ? (Integer) context.get("viewSize") : 50;
        String dataResourceTypeId = (String) context.get("dataResourceTypeId");
        Map<String, Object> inputFields = UtilGenerics.checkMap(context.get("inputFields"));

        try {
            Map<String, Object> queryStringMap = new LinkedHashMap<String, Object>();
            ModelEntity modelEntity = delegator.getModelEntity("DataResourceMediaFileView");
            List<EntityCondition> tmpList;
            if (inputFields != null && !inputFields.isEmpty()) {
                tmpList = FindServices.createConditionList(inputFields, modelEntity.getFieldsUnmodifiable(), queryStringMap, delegator, context);
            } else {
                tmpList = null;
            }

            EntityCondition cond;
            if (tmpList != null && tmpList.size() > 0) {
                if (UtilValidate.isNotEmpty(dataResourceTypeId)) {
                    cond = EntityCondition.makeCondition(EntityCondition.makeCondition(tmpList),
                                EntityOperator.AND,
                                EntityCondition.makeCondition("dataResourceTypeId",dataResourceTypeId));
                } else {
                    cond = EntityCondition.makeCondition(tmpList);
                }
            } else {
                if (UtilValidate.isNotEmpty(dataResourceTypeId)) {
                    cond = EntityCondition.makeCondition("dataResourceTypeId",dataResourceTypeId);
                } else {
                    cond = null;
                }
            }

            EntityCondition scpMediaCond = EntityCondition.makeCondition("contentTypeId", "SCP_MEDIA");
            if (cond != null) {
                cond = EntityCondition.makeCondition(cond, EntityOperator.AND, scpMediaCond);
            } else {
                cond = scpMediaCond;
            }

            Map<String, Object> findContext = new HashMap<>();
            findContext.put("userLogin", context.get("userLogin"));

            int start = viewIndex.intValue() * viewSize.intValue();
            List<GenericValue> list = null;
            Integer listSize = 0;
            EntityQuery query = EntityQuery.use(delegator).from("DataResourceMediaFileView");
            if (cond != null) {
                query.where(cond);
            }
            EntityListIterator listIt = query.orderBy("contentName ASC").cursorScrollInsensitive().queryIterator();
            try {
                listSize = listIt.getResultsSizeAfterPartialList();
                list = listIt.getPartialList(start + 1, viewSize);

                listSize = listIt.getResultsSizeAfterPartialList();
            } finally {
                if (listIt != null) listIt.close();
            }

            result.put("mediaFiles", list);
            result.put("viewSize", viewSize);
            result.put("listSize", listSize);
            result.put("viewIndex", viewIndex);
        } catch (Exception e) {
            FormattedError err = errorFmt.format(e, "Error getting media files", null, context);
            Debug.logError(err.getEx(), err.getLogMsg(), module);
            return err.returnFailure();
        }

        return result;
    }

    /**
     * Uploads a media file
     *
     * @param dctx
     * @param context
     * @return
     */
    public static Map<String, Object> uploadMediaFile(DispatchContext dctx, Map<String, ?> context) {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Map<String, Object> result = ServiceUtil.returnSuccess();
        //GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = (Locale) context.get("locale");
        TimeZone timeZone = (TimeZone) context.get("timeZone");

        ByteBuffer byteBuffer = (ByteBuffer) context.get("uploadedFile");
        String dataResourceTypeId = (String) context.get("dataResourceTypeId");
        String contentName = (String) context.get("contentName");

        String fileSize = (String) context.get("_uploadedFile_size");
        String fileName = (String) context.get("_uploadedFile_fileName");
        String contentType = (String) context.get("_uploadedFile_contentType");
        boolean autoResize = Boolean.TRUE.equals(context.get("autoVariants"));

        // USE SAME CREATED DATE FOR EVERYTHING RELATED
        Timestamp createdDate = UtilDateTime.nowTimestamp();

        StringToInteger std = new StringToInteger();
        Integer fileSizeConverted = 0;
        try {
            fileSizeConverted = std.convert(fileSize, locale, timeZone);
        } catch (ConversionException e) {
            Debug.logWarning(logPrefix+"Can't store file size: " + e.getMessage(), module);
        }
        if (fileSizeConverted != byteBuffer.limit()) {
            Debug.logWarning(logPrefix+"request header file size ===> " + fileSizeConverted + " differs from received byte array size ==> " + byteBuffer.limit()
                    + ". Byte array size prevails", module);
            fileSizeConverted = byteBuffer.limit();
        }

        try {
            GenericValue mimeType = null;
            if (UtilValidate.isNotEmpty(contentType)) {
                mimeType = delegator.findOne("MimeType", true, UtilMisc.toMap("mimeTypeId", contentType));
            }

            GenericValue dataResourceType = delegator.findOne("DataResourceType", true, UtilMisc.toMap("dataResourceTypeId", dataResourceTypeId));
            if (UtilValidate.isNotEmpty(dataResourceType) && dataResourceType.getBoolean("hasTable")) {
                FileTypeResolver fileTypeResolver = FileTypeResolver.getInstance(delegator, dataResourceTypeId);
                if (fileTypeResolver != null) {
                    // NOTE: 2017-02-07: we DO NOT use the mimeType sent by browser. override it here.
                    // ALWAYS do mime-type lookup by file extension + magic numbers.
                    // we DO NOT want browser-dependent behavior; the ONLY browser-dependent behavior
                    // is the assumption it sent the correct original filename.
                    // even this is NOT guaranteed... but usually true for common browsers...
                    mimeType = fileTypeResolver.findMimeType(byteBuffer, fileName);
                    if (mimeType != null) {
                        GenericValue mediaDataResource;
                        if (dataResourceTypeId.equals(FileTypeResolver.IMAGE_TYPE)) {
                            mediaDataResource = delegator.makeValue("ImageDataResource");
                            mediaDataResource.put("imageData", byteBuffer.array());
                        } else if (dataResourceTypeId.equals(FileTypeResolver.AUDIO_TYPE)) {
                            mediaDataResource = delegator.makeValue("AudioDataResource");
                            mediaDataResource.put("audioData", byteBuffer.array());
                        } else if (dataResourceTypeId.equals(FileTypeResolver.VIDEO_TYPE)) {
                            mediaDataResource = delegator.makeValue("VideoDataResource");
                            mediaDataResource.put("videoData", byteBuffer.array());
                        } else if (dataResourceTypeId.equals(FileTypeResolver.DOCUMENT_TYPE)) {
                            mediaDataResource = delegator.makeValue("DocumentDataResource");
                            mediaDataResource.put("documentData", byteBuffer.array());
                        } else {
                            // TODO: REVIEW: I'm not sure we should cover this case (2017-07-31: at least log it)
                            Debug.logInfo(logPrefix+"Could not determine media category for dataResourceTypeId '"
                                    + dataResourceTypeId + "' and mimeTypeId '" + mimeType.getString("mimeTypeId")
                                    + "'; storing as OtherDataResource", module);
                            mediaDataResource = delegator.makeValue("OtherDataResource");
                            mediaDataResource.put("dataResourceContent", byteBuffer.array());
                        }

                        GenericValue dataResource = delegator.makeValue("DataResource");
                        dataResource.put("dataResourceTypeId", dataResourceTypeId);
                        dataResource.put("dataResourceName", contentName); // TODO: REVIEW: here dataResourceName could be set to either contentName OR the filename...
                        dataResource.put("statusId", "CTNT_IN_PROGRESS");
                        dataResource.put("mimeTypeId", mimeType.getString("mimeTypeId"));
                        dataResource.put("isPublic", "N");
                        dataResource.put("objectInfo", fileName);
                        dataResource.put("createdDate", createdDate);
                        if (dataResourceTypeId.equals(FileTypeResolver.IMAGE_TYPE)) { // 2017-08-11: pre-read width & height, for future queries
                            try {
                                BufferedImage bufImg = ImageIO.read(new ByteArrayInputStream(byteBuffer.array()));
                                if (bufImg == null) { // SCIPIO: may be null
                                    Debug.logError(logPrefix+"Error uploading media file: Could not read/parse image file type to determine dimensions", module);
                                    return ServiceUtil.returnError(UtilProperties.getMessage("ProductErrorUiLabels", "ScaleImage.unable_to_parse", locale));
                                }
                                dataResource.put("scpWidth", (long) bufImg.getWidth());
                                dataResource.put("scpHeight", (long) bufImg.getHeight());
                            } catch(Exception e) {
                                Debug.logError(e, logPrefix+"Error uploading media file: Could not read/parse image file type to determine dimensions: " + e.getMessage(), module);
                                return ServiceUtil.returnError(UtilProperties.getMessage("ProductErrorUiLabels", "ScaleImage.unable_to_parse", locale) + ": " + e.getMessage());
                            }
                        }
                        dataResource = delegator.createSetNextSeqId(dataResource);
                        String dataResourceId = dataResource.getString("dataResourceId");
                        result.put("dataResourceId", dataResourceId);
                        result.put("dataResourceTypeId", dataResourceTypeId);

                        GenericValue fileSizeDataResourceAttr = delegator.makeValue("DataResourceAttribute");
                        fileSizeDataResourceAttr.put("dataResourceId", dataResource.get("dataResourceId"));
                        fileSizeDataResourceAttr.put("attrName", FileTypeUtil.FILE_SIZE_ATTRIBUTE_NAME);
                        fileSizeDataResourceAttr.put("attrValue", String.valueOf(fileSizeConverted));
                        fileSizeDataResourceAttr.create();

                        mediaDataResource.put("dataResourceId", dataResourceId);
                        mediaDataResource.create();

                        GenericValue content = delegator.makeValue("Content");
                        content.put("contentTypeId", "SCP_MEDIA");
                        content.put("contentName", contentName);
                        content.put("dataResourceId", dataResourceId);
                        content.put("createdDate", createdDate);
                        content = delegator.createSetNextSeqId(content);
                        String contentId = content.getString("contentId");
                        result.put("contentId", contentId);

                        if (dataResourceTypeId.equals(FileTypeResolver.IMAGE_TYPE) && autoResize) {
                            try {
                                Map<String, Object> resizeCtx = dctx.makeValidContext("cmsRebuildMediaVariants", ModelService.IN_PARAM, context);
                                resizeCtx.put("contentIdList", UtilMisc.<String>toList(contentId));
                                resizeCtx.put("force", Boolean.TRUE);
                                resizeCtx.put("createdDate", createdDate);
                                Map<String, Object> resizeResult = dispatcher.runSync("cmsRebuildMediaVariants", resizeCtx);
                                if (!ServiceUtil.isSuccess(resizeResult)) {
                                    return ServiceUtil.returnError("Error creating resized images: " + ServiceUtil.getErrorMessage(resizeResult));
                                }
                            } catch (GenericServiceException e) {
                                FormattedError err = errorFmt.format(e, "Error creating resized images", null, context);
                                Debug.logError(err.getEx(), err.getLogMsg(), module);
                                return err.returnError();
                            }
                        }

                    } else {
                        throw new FileTypeException(PropertyMessage.make("CommonErrorUiLabels", "CommonUnsupportedFileType"));
                    }
                } else {
                    throw new FileTypeException(PropertyMessage.make("CommonErrorUiLabels", "CommonUnsupportedFileType"));
                }
            } else {
                // TODO: Handle this case or throw an error. In fact as
                // it is currently implemented all media (dataResources) handled
                // in here must have an associated entity
                throw new FileTypeException(PropertyMessage.make("CommonErrorUiLabels", "CommonUnsupportedFileType"));
            }
        } catch (Exception e) {
            FormattedError err = errorFmt.format(e, "Error getting media files", null, context);
            if (!(e instanceof FileTypeException)) { // don't log, common user input error
                Debug.logError(err.getEx(), err.getLogMsg(), module);
            }
            return err.returnError();
        }

        // result.put("organizationPartyId", null);

        return result;
    }
    
    /**
     * Uploads a media file using custom variant sizes
     *
     * @param dctx
     * @param context
     * @return
     * @throws GeneralServiceException 
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> uploadMediaFileImageCustomVariantSizes(DispatchContext dctx, Map<String, Object> context) throws GeneralServiceException {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        
        
        
        Map<String, Object> result = ServiceUtil.returnSuccess();
        try {
            String customVariantSizeMethod = (String) context.get("customVariantSizeMethod");
            ImageVariantConfig imageVariantConfig = null;
            List<GenericValue> customImageSizes = UtilMisc.newList();
            if (customVariantSizeMethod.equals("customVariantSizesImgProp")) {
                if (context.containsKey("customVariantSizesImgProp")) {
                    imageVariantConfig = ImageVariantConfig.fromImagePropertiesXml((String) context.get("customVariantSizesImgProp"));
                } else {
                    Debug.logWarning("Custom image dimension properties file not found.", module);
                }
            } else if (customVariantSizeMethod.equals("customVariantSizesPreset")) {
                if (context.containsKey("customVariantSizesPreset")) {
                    Map<String, Map<String, String>> imgPropsMap = UtilMisc.newMap();
                    String presetId = (String) context.get("customVariantSizesPreset");
                    List<GenericValue> imageSizes = EntityQuery.use(delegator).from("ImageSize").where(UtilMisc.toMap("presetId", presetId)).cache(false).queryList();
                    GenericValue imagePreset = EntityQuery.use(delegator).from("ImageSizePreset").where(UtilMisc.toMap("presetId", presetId)).queryOne(); 
                    for (GenericValue imageSize : imageSizes) {
                        GenericValue imageSizeDimension = imageSize.getRelatedOne("ImageSizeDimension", false);
                        imgPropsMap.put(imageSizeDimension.getString("sizeName"),
                                UtilMisc.toMap("width", imageSizeDimension.getString("dimensionWidth"), "height", imageSizeDimension.getString("dimensionHeight")));
                        customImageSizes.add(imageSizeDimension);
                    }
                    imageVariantConfig = ImageVariantConfig.fromImagePropertiesMap(imagePreset.getString("presetName"), "", "", imgPropsMap);
                } else {
                    Debug.logWarning("Custom image size dimension preset not found.", module);
                }
            } else if (customVariantSizeMethod.equals("customVariantSizesForm")) {
                if (context.containsKey("variantSizeName") && context.containsKey("variantSizeWidth") && context.containsKey("variantSizeHeight") && context.containsKey("variantSizeSequenceNum")) {
                    Map<String, Map<String, String>> imgPropsMap = CmsMediaServices.getImgPropsMap(context);
                    imageVariantConfig = ImageVariantConfig.fromImagePropertiesMap("CustomDimension", "", "", imgPropsMap);
                    if (context.containsKey("saveAsPreset") && ((boolean) context.get("saveAsPreset"))) {
                        String presetName = (context.containsKey("presetName")) ? (String) context.get("presetName") : "Preset " + UtilDateTime.nowDateString();
                        customImageSizes = EntityUtil.filterByEntityName(saveCustomImageSizePreset(delegator, presetName, getImgPropsMap(context),
                                (List<String>) context.get("variantSizeSequenceNum")), "ImageSizeDimension");
                    }
                } else {
                    Debug.logWarning("Custom image size dimensions not found.", module);
                }
            }
            if (UtilValidate.isNotEmpty(imageVariantConfig)) {
                context.put("imageVariantConfig", imageVariantConfig);
            }

            result = dispatcher.runSync("cmsUploadMediaFile",
                    ServiceUtil.setServiceFields(dispatcher, "cmsUploadMediaFile", context,
                            (GenericValue) context.get("userLogin"), (TimeZone) context.get("timeZone"),
                            (Locale) context.get("locale")));

            if (result.containsKey("contentId")) {
                String contentId = (String) result.get("contentId");

                if (!customImageSizes.isEmpty()) {
                    EntityListIterator eli = null;
                    try {
                        eli = CmsMediaWorker.getMediaContentDataResourceViewTo(delegator,
                                "IMAGE_OBJECT",  CmsMediaWorker.getVariantContentAssocContentIdTo(delegator, contentId),
                                null);
                        List<GenericValue> variantContentDataResources = eli.getCompleteList();
                        if (UtilValidate.isNotEmpty(variantContentDataResources)) {
                            for (GenericValue customImageSize : customImageSizes) {
                                GenericValue variantContentDataResource = EntityUtil.getFirst(EntityUtil.filterByAnd(
                                        variantContentDataResources, UtilMisc.toList(EntityCondition.makeCondition("caMapKey",
                                                EntityOperator.EQUALS, customImageSize.getString("sizeName")))));
                                variantContentDataResource.put("drSizeId", customImageSize.get("sizeId"));
                                GenericValue dataResource = variantContentDataResource.extractViewMember("DataResource");
                                dataResource.store();
                            }
                        }
                    } catch (GenericEntityException e) {
                        throw e;
                    } finally {
                        if (UtilValidate.isNotEmpty(eli)) {
                            eli.close();
                        }
                    }
                    

                    if (context.containsKey("srcsetModeEnumId")) {
                        GenericValue imageViewPort = delegator.makeValidValue("ResponsiveImage", UtilMisc
                                .toMap("srcsetModeEnumId", context.get("srcsetModeEnumId"), "contentId", contentId));
                        imageViewPort.create();
                        if (context.get("srcsetModeEnumId").equals("IMG_SRCSET_VW")) {
                            List<GenericValue> imageMediaQueries = UtilMisc.newList();
                            List<String> mediaQueries = (List<String>) context.get("viewPortMediaQuery");
                            List<String> viewPortLength = (List<String>) context.get("viewPortLength");
                            List<String> viewPortSequenceNum = (List<String>) context.get("viewPortSequenceNum");
                            if (mediaQueries.size() == viewPortLength.size()) {
                                int i = 0;
                                for (String mediaQuery : mediaQueries) {
                                    imageMediaQueries.add(delegator.makeValidValue("ResponsiveImageVP",
                                            UtilMisc.toMap("contentId", contentId, "viewPortMediaQuery", mediaQuery,
                                                    "viewPortLength", Long.parseLong(viewPortLength.get(i)),
                                                    "sequenceNum", Long.parseLong(viewPortSequenceNum.get(i)))));
                                    i++;
                                }
                                delegator.storeAll(imageMediaQueries);
                            }
                        }
                    }
                }
            }
        } catch (GenericServiceException e) {
            result = ServiceUtil.returnError(e.getMessageList());
        } catch (IOException e) {
            result = ServiceUtil.returnError(e.getMessage());
        } catch (GenericEntityException e) {
            result = ServiceUtil.returnError(e.getMessage());
        }
        
        return result;
    }

    /**
     * Creates an image size preset
     *
     * @param dctx
     * @param context
     * @return
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> createCustomImageSizePreset(DispatchContext dctx, Map<String, Object> context) {
        Delegator delegator = dctx.getDelegator();
        String presetName = (context.containsKey("presetName")) ? (String) context.get("presetName") : "Preset " + UtilDateTime.nowDateString();
        try {
            saveCustomImageSizePreset(delegator, presetName, getImgPropsMap(context), (List<String>) context.get("variantSizeSequenceNum"));
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError(e.getMessageList());
        }
        return ServiceUtil.returnSuccess();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, String>> getImgPropsMap(Map<String, Object> context) { 
        Map<String, Map<String, String>> imgPropsMap = UtilMisc.newInsertOrderMap();    
        List<String> variantSizeNames = (List<String>) context.get("variantSizeName");
        List<String> variantSizeWidth = (List<String>) context.get("variantSizeWidth");
        List<String> variantSizeHeight = (List<String>) context.get("variantSizeHeight");        
        if (variantSizeNames.size() == variantSizeWidth.size() && variantSizeNames.size() == variantSizeHeight.size()) {
            for (int i = 0; i < variantSizeNames.size(); i++) {
                if (UtilValidate.isNotEmpty(variantSizeNames.get(i)) && UtilValidate.isNotEmpty(variantSizeWidth.get(i)) && UtilValidate.isNotEmpty(variantSizeHeight.get(i)))
                    imgPropsMap.put(variantSizeNames.get(i), UtilMisc.toMap("width", variantSizeWidth.get(i), "height", variantSizeHeight.get(i)));
            }
        }
        return imgPropsMap;
    }
    
    private static List<GenericValue> saveCustomImageSizePreset(Delegator delegator, String presetName, Map<String, Map<String, String>> imgPropsMap, List<String> sequenceNums) throws GenericEntityException {
        List<GenericValue> toStore = UtilMisc.newList();
        GenericValue imageSizePreset = delegator.makeValidValue("ImageSizePreset",
                UtilMisc.toMap("presetId", delegator.getNextSeqId("ImageSizePreset"), "presetName", presetName));
        toStore.add(imageSizePreset);
        int i = 0;
        for (String sizeName : imgPropsMap.keySet()) {
            Map<String, String> sizes = imgPropsMap.get(sizeName);
            GenericValue imageSizeDimension = delegator.makeValidValue("ImageSizeDimension", UtilMisc.toMap("sizeId", delegator.getNextSeqId("ImageSizeDimension"), "sizeName",
                    sizeName, "dimensionWidth", Long.parseLong(sizes.get("width")), "dimensionHeight", Long.parseLong(sizes.get("height")), "sequenceNum", Long.parseLong(sequenceNums.get(i))));
            toStore.add(imageSizeDimension);
            toStore.add(delegator.makeValidValue("ImageSize",
                    UtilMisc.toMap("presetId", imageSizePreset.get("presetId"), "sizeId", imageSizeDimension.get("sizeId"))));
            i++;
        }
        delegator.storeAll(toStore);
        return toStore;
    }

    /**
     * Updates a media file
     *
     * @param dctx
     * @param context
     * @return
     */
    public static Map<String, Object> updateMediaFile(DispatchContext dctx, Map<String, ?> context) {
        Delegator delegator = dctx.getDelegator();
        //LocalDispatcher dispatcher = dctx.getDispatcher();
        Locale locale = (Locale) context.get("locale");
        //GenericValue userLogin = (GenericValue) context.get("userLogin");
        TimeZone timeZone = (TimeZone) context.get("timeZone");
        Map<String, Object> result = ServiceUtil.returnSuccess();

        String contentId = (String) context.get("contentId");
        String dataResourceId = (String) context.get("dataResourceId");
        String dataResourceTypeId;

        if (UtilValidate.isEmpty(contentId) && UtilValidate.isEmpty(dataResourceId)) {
            result = ServiceUtil.returnError("cmsUpdateMediaFile requires either a contentId or dataResourceId, not passed");
            return result;
        }

        String contentName = (String) context.get("contentName");
        Boolean isPublic = (Boolean) context.get("isPublic");
        String statusId = (String) context.get("statusId");

        try {
            GenericValue content = CmsMediaWorker.getContentForMedia(delegator, contentId, dataResourceId);
            dataResourceId = content.getString("dataResourceId");
            contentId = content.getString("contentId");

            if (UtilValidate.isNotEmpty(contentName)) {
                content.put("contentName", contentName);
            }
            content.store();
            result.put("contentId", contentId);

            GenericValue dataResource = delegator.findOne("DataResource", UtilMisc.toMap("dataResourceId", dataResourceId), false);
            if (UtilValidate.isEmpty(dataResource)) {
                return ServiceUtil.returnError("Media file not found for dataResourceId '" + dataResourceId + "'");
            }
            dataResourceTypeId = dataResource.getString("dataResourceTypeId");

            if (UtilValidate.isNotEmpty(isPublic)) {
                dataResource.put("isPublic", isPublic);
            }
            if (UtilValidate.isNotEmpty(contentName)) {
                dataResource.put("dataResourceName", contentName); // TODO: REVIEW: here dataResourceName could be set to either contentName OR the filename...
            }
            if (UtilValidate.isNotEmpty(context.get("statusId"))) {
                dataResource.put("statusId", statusId);
            }
            dataResource.store();
            result.put("dataResourceId", dataResourceId);
            result.put("dataResourceTypeId", dataResourceTypeId);

            // SPECIAL: in order to maintain reliable DB, for now this should propagate all the Content/DataResource
            // field modifications to the entities of its variant/resized images (may help optimizations also)
            if ("IMAGE_OBJECT".equals(dataResourceTypeId)) {
                Set<String> visitedContentIdTo = new HashSet<>();

                for(GenericValue varContentAssocTo : CmsMediaWorker.getVariantContentAssocTo(delegator, contentId)) {
                    String varContentId = varContentAssocTo.getString("contentId");
                    if (visitedContentIdTo.contains(varContentId)) continue;
                    visitedContentIdTo.add(varContentId);

                    String sizeType = varContentAssocTo.getString("caMapKey");

                    GenericValue varContent = delegator.findOne("Content", UtilMisc.toMap("contentId", varContentId), false);
                    if (UtilValidate.isEmpty(dataResource)) {
                        return ServiceUtil.returnError("Bad media file - Content not found for variant contentId '" + varContentId + "'");
                    }

                    // TODO: REVIEW: here dataResourceName could be set to either contentName OR the filename...

                    // FIXME: this emulates the DB image resize service... poorly
                    Map<String, Object> imageCtx = new HashMap<>();
                    //imageCtx.put("origfn", imageOrigFnNoExt);
                    //imageCtx.put("origfnnodir", imageOrigFullFnNoExt);
                    Map<String, Object> fieldsCtx = new HashMap<>();
                    fieldsCtx.putAll(content);
                    fieldsCtx.putAll(dataResource); // TODO: REVIEW: possible name clashes...
                    imageCtx.put("fields", fieldsCtx);
                    imageCtx.put("sizetype", sizeType);
                    imageCtx.put("type", sizeType);

                    if (UtilValidate.isNotEmpty(contentName)) {
                        // SPECIAL
                        varContent.put("contentName", CmsMediaWorker.RESIZEIMG_CONTENT_FIELDEXPR.get("contentName").expandString(imageCtx, timeZone, locale));
                    }
                    varContent.store();
                    String varDataResourceId = varContent.getString("dataResourceId");

                    GenericValue varDataResource = delegator.findOne("DataResource", UtilMisc.toMap("dataResourceId", varDataResourceId), false);
                    if (UtilValidate.isEmpty(varDataResource)) {
                        return ServiceUtil.returnError("Bad media file - DataResource not found for variant dataResourceId '" + varDataResourceId + "'");
                    }

                    if (UtilValidate.isNotEmpty(isPublic)) {
                        varDataResource.put("isPublic", isPublic);
                    }
                    if (UtilValidate.isNotEmpty(contentName)) {
                        // SPECIAL
                        varDataResource.put("dataResourceName", CmsMediaWorker.RESIZEIMG_DATARESOURCE_FIELDEXPR.get("dataResourceName").expandString(imageCtx, timeZone, locale));
                    }
                    if (UtilValidate.isNotEmpty(context.get("statusId"))) {
                        varDataResource.put("statusId", statusId);
                    }
                    varDataResource.store();
                }
            }
        } catch (Exception e) {
            FormattedError err = errorFmt.format(e, "Error updating media file", context);
            Debug.logError(err.getEx(), err.getLogMsg(), module);
            return err.returnError();
        }

        result.put("contentId", contentId);
        result.put("dataResourceId", dataResourceId);
        result.put("dataResourceTypeId", dataResourceTypeId);
        return result;
    }

    /**
     * Deletes a media file
     *
     * @param dctx
     * @param context
     * @return
     */
    public static Map<String, Object> deleteMediaFile(DispatchContext dctx, Map<String, ?> context) {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Locale locale = (Locale) context.get("locale");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        TimeZone timeZone = (TimeZone) context.get("timeZone");
        Map<String, Object> result = ServiceUtil.returnSuccess();

        String contentId = (String) context.get("contentId");
        String dataResourceId = (String) context.get("dataResourceId");

        if (UtilValidate.isEmpty(contentId) && UtilValidate.isEmpty(dataResourceId)) {
            result = ServiceUtil.returnError("cmsDeleteMediaFile requires either a contentId or dataResourceId, not passed");
            return result;
        }

        try {
            GenericValue content = CmsMediaWorker.getContentForMedia(delegator, contentId, dataResourceId);
            contentId = content.getString("contentId");

            // delete any associated first
            for(GenericValue contentAssoc : delegator.findByAnd("ContentAssoc", UtilMisc.toMap("contentId", contentId), null, false)) {
                try {
                    Map<String, Object> servCtx = new HashMap<>();
                    servCtx.put("userLogin", userLogin);
                    servCtx.put("locale", locale);
                    servCtx.put("timeZone", timeZone);
                    servCtx.put("contentId", contentAssoc.get("contentIdTo"));
                    // NOTE: this service automatically deletes contentAssoc
                    Map<String, Object> contentResult = dispatcher.runSync("removeContentAndRelated", servCtx);
                    if (!ServiceUtil.isSuccess(contentResult)) {
                        return ServiceUtil.returnError("Error removing media file content: " + ServiceUtil.getErrorMessage(contentResult));
                    }
                } catch (GenericServiceException e) {
                    FormattedError err = errorFmt.format(e, "Error removing media file", context);
                    Debug.logError(err.getEx(), err.getLogMsg(), module);
                    return err.returnError();
                }
            }

            try {
                Map<String, Object> servCtx = new HashMap<>();
                servCtx.put("userLogin", userLogin);
                servCtx.put("locale", locale);
                servCtx.put("timeZone", timeZone);
                servCtx.put("contentId", contentId);
                Map<String, Object> contentResult = dispatcher.runSync("removeContentAndRelated", servCtx);
                if (!ServiceUtil.isSuccess(contentResult)) {
                    return ServiceUtil.returnError("Error removing media file content: " + ServiceUtil.getErrorMessage(contentResult));
                }
            } catch (GenericServiceException e) {
                FormattedError err = errorFmt.format(e, "Error removing media file", context);
                Debug.logError(err.getEx(), err.getLogMsg(), module);
                return err.returnError();
            }
        } catch (Exception e) {
            FormattedError err = errorFmt.format(e, "Error removing media file", context);
            Debug.logError(err.getEx(), err.getLogMsg(), module);
            return err.returnError();
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> rebuildMediaVariants(DispatchContext dctx, Map<String, ?> context) {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        //Locale locale = (Locale) context.get("locale");
        //GenericValue userLogin = (GenericValue) context.get("userLogin");
        //TimeZone timeZone = (TimeZone) context.get("timeZone");
        Map<String, Object> result = ServiceUtil.returnSuccess();

        Collection<String> contentIdList = UtilGenerics.checkCollection(context.get("contentIdList"));
        boolean force = Boolean.TRUE.equals(context.get("force"));
        // USE SAME CREATED DATE FOR EVERYTHING RELATED
        Timestamp createdDate = (Timestamp) context.get("createdDate");
        if (createdDate == null) createdDate = UtilDateTime.nowTimestamp();

        EntityListIterator contentDataResourceList;

        Boolean sepTrans = (Boolean) context.get("sepTrans");
        if (sepTrans == null) sepTrans = (contentIdList == null);

        Set<String> remainingContentIds = new HashSet<>();
        boolean doLog = false;
        String imagePropXmlPath;
        try {
            if (contentIdList == null) {
                contentDataResourceList = CmsMediaWorker.getAllMediaContentDataResourceRequired(delegator, "IMAGE_OBJECT", null);
                doLog = true;
            } else {
                contentIdList = new LinkedHashSet<>(contentIdList); // remove dups
                remainingContentIds = new HashSet<>(contentIdList);
                contentDataResourceList = CmsMediaWorker.getMediaContentDataResourceRequiredByContentId(delegator, "IMAGE_OBJECT", contentIdList, null);
            }

            imagePropXmlPath = CmsMediaWorker.getCmsImagePropertiesPath();
        } catch (Exception e) {
            FormattedError err = errorFmt.format(e, "Error creating resized images", context);
            Debug.logError(err.getEx(), err.getLogMsg(), module);
            return err.returnError();
        }

        if (contentDataResourceList != null) {
            try {
                if (doLog) {
                    Debug.logInfo(logPrefix+"Beginning rebuildMediaVariants for all images", module);
                }
                // SCIPIO (01/19/2018): Get custom image sizes, if present
                Map<String, GenericValue> customImageSizesByName = UtilMisc.newMap();
                if (context.containsKey("customImageSizes")) {
                    List<GenericValue> customImageSizes = (List<GenericValue>) context.get("customImageSizes");
                    for (GenericValue customImageSize : customImageSizes) {
                        customImageSizesByName.put(customImageSize.getString("sizeName"),  customImageSize);
                    }
                }
                
                long imgCount = 0;
                GenericValue contentDataResource;
                while((contentDataResource = contentDataResourceList.next()) != null) {
                    String contentId = contentDataResource.getString("contentId");
                    remainingContentIds.remove(contentId);
                    
                    if (force || CmsMediaWorker.hasVariantContent(delegator, contentId)) {
                        if (doLog) {
                            Debug.logInfo(logPrefix+"rebuildMediaVariants: Rebuilding variants for image [contentId: " + contentId + "] (" + (imgCount+1) + ")", module);
                        }

                        try {
                            Map<String, Object> resizeCtx = dctx.makeValidContext("contentImageDbScaleInAllSizeCore", ModelService.IN_PARAM, context);
                            resizeCtx.put("imageOrigContentId", contentId);
                            if (!resizeCtx.containsKey("deleteOld")) {
                                resizeCtx.put("deleteOld", Boolean.TRUE);
                            }
                            resizeCtx.put("imagePropXmlPath", imagePropXmlPath);
                            resizeCtx.put("fileSizeDataResAttrName", FileTypeUtil.FILE_SIZE_ATTRIBUTE_NAME);

                            Map<String, Object> contentFields = new HashMap<>();
                            contentFields.putAll(CmsMediaWorker.RESIZEIMG_CONTENT_FIELDEXPR);
                            contentFields.put("contentTypeId", "SCP_MEDIA_VARIANT");
                            resizeCtx.put("contentFields", contentFields);

                            Map<String, Object> dataResourceFields = new HashMap<>();
                            dataResourceFields.putAll(CmsMediaWorker.RESIZEIMG_DATARESOURCE_FIELDEXPR);
                            dataResourceFields.put("dataResourceTypeId", "IMAGE_OBJECT");
                            dataResourceFields.put("statusId", contentDataResource.get("drStatusId"));
                            dataResourceFields.put("isPublic", contentDataResource.get("drIsPublic"));
                            
                            // SCIPIO (01/19/2018): Add the custom dimension to the variant dataSource
                            if (customImageSizesByName.containsKey(null)) {
                                dataResourceFields.put("sizeId", customImageSizesByName.get(null).getString("sizedId"));
                            }
                            
                            resizeCtx.put("dataResourceFields", dataResourceFields);
                            resizeCtx.put("createdDate", createdDate);

                            Map<String, Object> resizeResult;
                            if (sepTrans) {
                                resizeResult = dispatcher.runSync("contentImageDbScaleInAllSizeCore", resizeCtx, -1, true);
                            } else {
                                resizeResult = dispatcher.runSync("contentImageDbScaleInAllSizeCore", resizeCtx);
                            }
                            if (!ServiceUtil.isSuccess(resizeResult)) {
                                return ServiceUtil.returnError("Error creating resized images: " + ServiceUtil.getErrorMessage(resizeResult));
                            }
                        } catch (GenericServiceException e) {
                            FormattedError err = errorFmt.format(e, "Error creating resized images", context);
                            Debug.logError(err.getEx(), err.getLogMsg(), module);
                            return err.returnError();
                        }
                        imgCount++;
                    }
                }
                if (remainingContentIds.size() > 0) {
                    String errMsg = "Could not find valid image media records for contentIds: " + remainingContentIds.toString();
                    Debug.logError(logPrefix + errMsg, module);
                    return ServiceUtil.returnError(errMsg);
                }
                if (doLog) {
                    Debug.logInfo(logPrefix+"Finished rebuildMediaVariants for " + imgCount + " images (having variants or forced)", module);
                }
            } catch (Exception e) {
                FormattedError err = errorFmt.format(e, "Error creating resized images", context);
                Debug.logError(err.getEx(), err.getLogMsg(), module);
                return err.returnError();
            } finally {
                try {
                    contentDataResourceList.close();
                } catch (GenericEntityException e) {
                    Debug.logError(e, module);
                }
            }
        }
        return result;
    }

    // TODO: REVIEW: for now we are intentionally ignoring the thruDate on ContentAssoc to simplify.
    // I don't see the point in keeping old records...
    public static Map<String, Object> removeMediaVariants(DispatchContext dctx, Map<String, ?> context) {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Locale locale = (Locale) context.get("locale");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        TimeZone timeZone = (TimeZone) context.get("timeZone");
        Map<String, Object> result = ServiceUtil.returnSuccess();

        Collection<String> contentIdList = UtilGenerics.checkCollection(context.get("contentIdList"));
        EntityListIterator contentDataResourceList;

        Set<String> remainingContentIds = new HashSet<>();
        boolean doLog = false;
        try {
            if (contentIdList == null) {
                contentDataResourceList = CmsMediaWorker.getAllMediaContentDataResourceRequired(delegator, "IMAGE_OBJECT", null);
                doLog = true;
            } else {
                contentIdList = new LinkedHashSet<>(contentIdList);
                remainingContentIds = new HashSet<>(contentIdList);
                contentDataResourceList = CmsMediaWorker.getMediaContentDataResourceRequiredByContentId(delegator, "IMAGE_OBJECT", contentIdList, null);
            }
        } catch (Exception e) {
            FormattedError err = errorFmt.format(e, "Error removing resized images", context);
            Debug.logError(err.getEx(), err.getLogMsg(), module);
            return err.returnError();
        }

        if (contentDataResourceList != null) {
            try {
                if (doLog) {
                    Debug.logInfo(logPrefix+"Beginning removeMediaVariants for all images", module);
                }
                long imgCount = 0;
                GenericValue contentDataResource;
                while((contentDataResource = contentDataResourceList.next()) != null) {
                    String contentId = contentDataResource.getString("contentId");
                    remainingContentIds.remove(contentId);
                    if (doLog) {
                        Debug.logInfo(logPrefix+"removeMediaVariants: Removing variants for image [contentId: " + contentId + "] (" + (imgCount+1) + ")", module);
                    }

                    for(String contentIdTo : CmsMediaWorker.getVariantContentAssocContentIdTo(delegator, contentId)) {
                        try {
                            Map<String, Object> servCtx = new HashMap<>();
                            servCtx.put("userLogin", userLogin);
                            servCtx.put("locale", locale);
                            servCtx.put("timeZone", timeZone);
                            servCtx.put("contentId", contentIdTo);
                            // NOTE: this service automatically deletes contentAssoc
                            Map<String, Object> contentResult = dispatcher.runSync("removeContentAndRelated", servCtx);
                            if (!ServiceUtil.isSuccess(contentResult)) {
                                return ServiceUtil.returnError("Error removing media file variant: " + ServiceUtil.getErrorMessage(contentResult));
                            }
                        } catch (GenericServiceException e) {
                            FormattedError err = errorFmt.format(e, "Error removing media file variant", context);
                            Debug.logError(err.getEx(), err.getLogMsg(), module);
                            return err.returnError();
                        }
                    }
                    delegator.removeByAnd("ContentAttribute", UtilMisc.toMap("contentId", contentId, "attrName", ContentImageWorker.CONTENTATTR_VARIANTCFG));

                    imgCount++;
                }
                if (remainingContentIds.size() > 0) {
                    String errMsg = "Could not find valid image media records for contentIds: " + remainingContentIds.toString();
                    Debug.logError(logPrefix + errMsg, module);
                    return ServiceUtil.returnError(errMsg);
                }
                if (doLog) {
                    Debug.logInfo(logPrefix+"Finished removeMediaVariants for " + imgCount + " images (note: this count includes images that had no variants)", module);
                }
            } catch (Exception e) {
                FormattedError err = errorFmt.format(e, "Error removing resized images", context);
                Debug.logError(err.getEx(), err.getLogMsg(), module);
                return err.returnError();
            } finally {
                try {
                    contentDataResourceList.close();
                } catch (GenericEntityException e) {
                    Debug.logError(e, module);
                }
            }
        }
        return result;
    }
}
