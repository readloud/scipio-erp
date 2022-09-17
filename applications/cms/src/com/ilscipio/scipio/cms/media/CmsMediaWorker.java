package com.ilscipio.scipio.cms.media;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.string.FlexibleStringExpander;
import org.ofbiz.common.image.ImageVariantConfig;
import org.ofbiz.content.image.ContentImageWorker;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.util.EntityListIterator;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.webapp.control.RequestHandler;
import org.ofbiz.webapp.control.WebAppConfigurationException;

public abstract class CmsMediaWorker {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    /**
     * NOTE: currently (2017-08-01) it is recommended to not use this file and to use
     * /applications/content/config/ImageProperties.xml instead, so images everywhere are the same
     * (there may currently be issues if they differ... TODO: REVIEW).
     */
    public static final String CMS_IMAGEPROP_FILEPATH = "/applications/cms/config/ImageProperties.xml";

    public static final Map<String, FlexibleStringExpander> RESIZEIMG_CONTENT_FIELDEXPR = ContentImageWorker.RESIZEIMG_CONTENT_FIELDEXPR;
    public static final Map<String, FlexibleStringExpander> RESIZEIMG_DATARESOURCE_FIELDEXPR = ContentImageWorker.RESIZEIMG_DATARESOURCE_FIELDEXPR;

    public static final Set<String> VALID_DATA_RESOURCE_TYPE_LIST = Collections.unmodifiableSet(UtilMisc.toHashSet("AUDIO_OBJECT", "VIDEO_OBJECT", "IMAGE_OBJECT", "DOCUMENT_OBJECT"));

    private static final ImageVariantConfig defaultCmsImgVariantCfg;
    static {
        ImageVariantConfig cfg = null;
        try {
            cfg = ImageVariantConfig.fromImagePropertiesXml(getCmsImagePropertiesPath());
        } catch(Exception e) {
            Debug.logError(e, "Cms: Media: Could not read ImageProperties.xml: " + e.getMessage(), module);
        }
        defaultCmsImgVariantCfg = cfg;
    }


    protected CmsMediaWorker() {
    }

    public static ImageVariantConfig getDefaultCmsImageVariantConfig() {
        return defaultCmsImgVariantCfg;
    }

    public static GenericValue getContentForMedia(Delegator delegator, String contentId, String dataResourceId) throws GenericEntityException, IllegalArgumentException, IllegalStateException {
        GenericValue content;
        if (UtilValidate.isNotEmpty(contentId)) {
            content = delegator.findOne("Content", UtilMisc.toMap("contentId", contentId), false);
            if (UtilValidate.isEmpty(content)) {
                throw new IllegalArgumentException("Media file not found for contentId '" + contentId + "'");
            }
            //dataResourceId = content.getString("dataResourceId");
        } else {
            List<GenericValue> contentList = delegator.findByAnd("Content", UtilMisc.toMap("dataResourceId", dataResourceId), null, false);
            if (UtilValidate.isEmpty(contentList)) {
                // DEV NOTE: I was going to make this auto-create one for backward compat but not worth it, cms not released yet
                throw new IllegalArgumentException("Invalid media file - dataResourceId '" + dataResourceId + "' has no Content record"
                        + " - either invalid media file ID or schema error - please contact your administrator");
            } else if (contentList.size() > 1){
                throw new IllegalStateException("Media file DataResource is associated to multiple Content records - cannot safely modify -"
                        + " db corruption could occur if we tried to update one - please contact your administrator");
            }
            content = contentList.get(0);
            //contentId = content.getString("contentId");
        }
        return content;
    }
    
    public static GenericValue getDataResourceForMedia(Delegator delegator, String contentId, String dataResourceId) throws GenericEntityException, IllegalArgumentException {
        if (UtilValidate.isNotEmpty(dataResourceId) || UtilValidate.isNotEmpty(contentId)) {
            GenericValue content = getContentForMedia(delegator, contentId, dataResourceId);
            return content.getRelatedOne("DataResource", false);
        } else {
            throw new IllegalArgumentException("Invalid media file - dataResourceId '" + dataResourceId + "' has no Content record"
                    + " - either invalid media file ID or schema error - please contact your administrator");
        }
    }

    /**
     * Returns as ContentDataResourceRequiredView values (NOTE: the DataResource fields have "dr" prefix).
     * @throws GenericEntityException
     */
    public static EntityListIterator getAllMediaContentDataResourceRequired(Delegator delegator, String dataResourceTypeId, List<String> orderBy) throws GenericEntityException {
        List<EntityCondition> condList = new ArrayList<>();
        condList.add(EntityCondition.makeCondition("contentTypeId", "SCP_MEDIA"));
        if (dataResourceTypeId != null) condList.add(EntityCondition.makeCondition("drDataResourceTypeId", dataResourceTypeId));
        return delegator.find("ContentDataResourceRequiredView", EntityCondition.makeCondition(condList, EntityOperator.AND), null, null, orderBy, null);
    }

    public static EntityListIterator getMediaContentDataResourceRequiredByContentId(Delegator delegator, String dataResourceTypeId, Collection<String> contentIdList,
            List<String> orderBy) throws GenericEntityException {
        List<EntityCondition> condList = new ArrayList<>();
        condList.add(EntityCondition.makeCondition("contentTypeId", "SCP_MEDIA"));
        if (dataResourceTypeId != null) condList.add(EntityCondition.makeCondition("drDataResourceTypeId", dataResourceTypeId));
        List<EntityCondition> contentIdCondList = new ArrayList<>();
        for(String contentId : contentIdList) {
            contentIdCondList.add(EntityCondition.makeCondition("contentId", contentId));
        }
        condList.add(EntityCondition.makeCondition(contentIdCondList, EntityOperator.OR));
        return delegator.find("ContentDataResourceRequiredView",
                EntityCondition.makeCondition(condList, EntityOperator.AND), null, null, null, null);
    }
    
    
    public static EntityListIterator getMediaContentDataResourceViewTo(Delegator delegator, String dataResourceTypeId, Collection<String> contentIdList, List<String> orderBy)
            throws GenericEntityException {
        List<EntityCondition> condList = new ArrayList<>();
        condList.add(EntityCondition.makeCondition("contentTypeId", "SCP_MEDIA_VARIANT"));
        if (dataResourceTypeId != null) condList.add(EntityCondition.makeCondition("drDataResourceTypeId", dataResourceTypeId));
        List<EntityCondition> contentIdCondList = new ArrayList<>();
        for(String contentId : contentIdList) {
            contentIdCondList.add(EntityCondition.makeCondition("contentId", contentId));
        }
        condList.add(EntityCondition.makeCondition(contentIdCondList, EntityOperator.OR));
        return delegator.find("ContentAssocDataResourceViewTo",
                EntityCondition.makeCondition(condList, EntityOperator.AND), null, null, null, null);
    }

    /**
     * SCIPIO: Returns the full path to the ImageProperties.xml file to use for cms image size definitions.
     * Uses the one from cms component if available; otherwise falls back on the generic one under content component.
     * WARN: Currently recommend NOT to use the CMS-specific one until better tested.
     * Added 2017-08-01.
     */
    public static String getCmsImagePropertiesFullPath() throws IOException {
        String path = ImageVariantConfig.getImagePropertiesFullPath(CMS_IMAGEPROP_FILEPATH);
        if (new java.io.File(path).exists()) {
            return path;
        } else {
            return ContentImageWorker.getContentImagePropertiesFullPath();
        }
    }

    public static String getCmsImagePropertiesPath() throws IOException {
        String path = ImageVariantConfig.getImagePropertiesFullPath(CMS_IMAGEPROP_FILEPATH);
        if (new java.io.File(path).exists()) {
            return CMS_IMAGEPROP_FILEPATH;
        } else {
            return ContentImageWorker.getContentImagePropertiesPath();
        }
    }

    // TODO: REVIEW: for now we are intentionally ignoring the thruDate on ContentAssoc to simplify.
    // I don't see the point in keeping old records...
    
    public static List<GenericValue> getVariantContentAssocTo(HttpServletRequest request, String contentId) throws GenericEntityException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        return getVariantContentAssocTo(delegator, contentId);
    }
    
    
    public static List<GenericValue> getVariantContentAssocTo(Delegator delegator, String contentId) throws GenericEntityException {
        EntityCondition cond = EntityCondition.makeCondition(
                EntityCondition.makeCondition("contentIdStart", contentId),
                EntityOperator.AND,
                EntityCondition.makeCondition("contentTypeId", "SCP_MEDIA_VARIANT")); // alternative: EntityCondition.makeCondition("caContentAssocTypeId", EntityOperator.LIKE, "IMGSZ_%")
        return delegator.findList("ContentAssocViewTo", cond, null, null, null, false);
    }

    public static Set<String> getVariantContentAssocContentIdTo(Delegator delegator, String contentId) throws GenericEntityException {
        List<GenericValue> assocList = getVariantContentAssocTo(delegator, contentId);
        Set<String> res = new LinkedHashSet<>();
        if (assocList != null) {
            for(GenericValue assoc : assocList) {
                res.add(assoc.getString("contentId"));
            }
        }
        return res;
    }

    public static List<String> getVariantContentMapKeys(Delegator delegator, String contentId) throws GenericEntityException {
        List<GenericValue> assocList = getVariantContentAssocTo(delegator, contentId);
        List<String> res = new ArrayList<>();
        if (assocList != null) {
            for(GenericValue assoc : assocList) {
                res.add(assoc.getString("caMapKey"));
            }
        }
        return res;
    }

    public static EntityListIterator findVariantContentAssocTypes(Delegator delegator) throws GenericEntityException {
        return delegator.find("ContentAssocType",
                EntityCondition.makeCondition("contentAssocTypeId", EntityOperator.LIKE, "IMGSZ_%"),
                null, null, null, null);
    }

    // TODO: optimize
    public static boolean hasVariantContent(Delegator delegator, String contentId) throws GenericEntityException {
        EntityCondition cond = EntityCondition.makeCondition(
                EntityCondition.makeCondition("contentIdStart", contentId),
                EntityOperator.AND,
                EntityCondition.makeCondition("contentTypeId", "SCP_MEDIA_VARIANT")); // alternative: EntityCondition.makeCondition("caContentAssocTypeId", EntityOperator.LIKE, "IMGSZ_%")
        return delegator.findCountByCondition("ContentAssocViewTo", cond, null, null) > 0;
    }
    
    // Responsive image utilities
    /**
     * 
     * @param responsiveImage
     * @return
     * @throws GenericEntityException
     */
    public static List<GenericValue> getResponsiveImageViewPorts(GenericValue responsiveImage) throws GenericEntityException  {
        if (responsiveImage.get("srcsetModeEnumId").equals("IMG_SRCSET_VW"))
            return responsiveImage.getRelated("ResponsiveImageVP", null, UtilMisc.toList("sequenceNum"), false);
        return null;
    }
    
    
    /**
     * 
     * @param delegator
     * @param contentId
     * @return
     * @throws GenericEntityException
     */
    public static List<GenericValue> getResponsiveImageViewPorts(Delegator delegator, String contentId) throws GenericEntityException  {
       return getResponsiveImageViewPorts(getResponsiveImage(delegator, contentId));
    }
    
    /**
     * 
     * @param delegator
     * @param contentId
     * @return
     * @throws GenericEntityException
     */
    public static GenericValue getResponsiveImage(Delegator delegator, String contentId) throws GenericEntityException {
        return delegator.findOne("ResponsiveImage", UtilMisc.toMap("contentId", contentId), false);
    }
    
    /**
     * 
     * @param request
     * @param contentId
     * @return
     * @throws GenericEntityException
     * @throws WebAppConfigurationException
     * @throws IOException
     */
    public static Map<String, String> buildSrcsetMap(HttpServletRequest request, HttpServletResponse response, String contentId)
            throws GenericEntityException, WebAppConfigurationException, IOException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
//        Locale locale = (Locale) request.getAttribute("locale");
        String webSiteId = (String) request.getAttribute("webSiteId");
        Map<String, String> srcsetEntry = UtilMisc.newInsertOrderMap();

        List<GenericValue> imageSizeDimensionList = UtilMisc.newList();
        List<Long> scpWidthList = UtilMisc.newList();

        EntityListIterator contentDataResourceList = null;
        try {
            contentDataResourceList = getMediaContentDataResourceViewTo(delegator, "IMAGE_OBJECT", getVariantContentAssocContentIdTo(delegator, contentId), null);
            GenericValue contentDataResource;
            Map<String, GenericValue> dataResourceBySizeIdMap = UtilMisc.newMap();
            while ((contentDataResource = contentDataResourceList.next()) != null) {
                String sizeId = contentDataResource.getString("drSizeId");
                GenericValue imageSizeDimension = delegator.findOne("ImageSizeDimension", UtilMisc.toMap("sizeId", sizeId), false);
                if (UtilValidate.isNotEmpty(imageSizeDimension)) {
                    imageSizeDimensionList.add(imageSizeDimension);
                } else {
                    // TODO: Let's see what do in this case
                    scpWidthList.add(contentDataResource.getLong("scpWidth"));
                }
                dataResourceBySizeIdMap.put(sizeId, contentDataResource);
            }
            imageSizeDimensionList = EntityUtil.orderBy(imageSizeDimensionList, UtilMisc.toList("sequenceNum"));

            for (GenericValue imageSizeDimension : imageSizeDimensionList) {
                GenericValue dataResource = dataResourceBySizeIdMap.get(imageSizeDimension.getString("sizeId"));
                String variantUrl = RequestHandler.makeLinkAuto(request, response, "media?contentId=" + contentId + "&variant=" + dataResource.get("caMapKey"), false, false,
                        webSiteId, false, true, true, true);
//                OfbizUrlBuilder.from(request).buildFullUrlWithContextPath(variantUrl, "/media?contentId=" + contentId + "&variant=" + dataResource.get("caMapKey"), true);
                if (UtilValidate.isNotEmpty(variantUrl)) {
                    srcsetEntry.put(String.valueOf(imageSizeDimension.getLong("dimensionWidth")), variantUrl);
                }
            }

        } catch (GenericEntityException e) {
            throw e;
        } finally {
            if (UtilValidate.isNotEmpty(contentDataResourceList)) {
                contentDataResourceList.close();
            }
        }

        return srcsetEntry;
    }

}
