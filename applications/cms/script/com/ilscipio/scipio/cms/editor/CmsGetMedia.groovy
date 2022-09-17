/**
 * Scipio CMS Get Media - script
 * Fetches Media files and puts them into context
 */

import javax.servlet.*;
import javax.servlet.http.*;

import org.ofbiz.base.util.*;
import org.ofbiz.entity.*;
import org.ofbiz.entity.util.*;

import com.ilscipio.scipio.cms.CmsUtil;
import com.ilscipio.scipio.common.util.fileType.FileTypeUtil

final String module = "CmsGetMedia.groovy";

/*Parameters*/
media = null;
fileSizeAttr = null;
variantList = null;
responsiveImage = null;
responsiveImageViewPortList = null;
if (parameters.contentId) { // needless?: && parameters.dataResourceTypeId && context.validMediaDataResourceTypeIdSet.contains(parameters.dataResourceTypeId)
    try {
        //media = delegator.findOne("DataResource", ["dataResourceId": parameters.dataResourceId], false);
        mediaList = delegator.findByAnd("DataResourceMediaFileView", ["contentTypeId": "SCP_MEDIA", "contentId": parameters.contentId], null, false);
        if (mediaList) {
            if (mediaList.size() == 1) {
                media = mediaList[0];
                variantList = com.ilscipio.scipio.cms.media.CmsMediaWorker.getVariantContentMapKeys(delegator, media.contentId);
//                for (variant in variantList) {
//                    Debug.log("variant ====> " + variant);
//                }
                responsiveImage = com.ilscipio.scipio.cms.media.CmsMediaWorker.getResponsiveImage(delegator, media.contentId);                
                if (responsiveImage && responsiveImage.get("srcsetModeEnumId").equals("IMG_SRCSET_VW")) {
                    responsiveImageViewPortList = com.ilscipio.scipio.cms.media.CmsMediaWorker.getResponsiveImageViewPorts(delegator, media.contentId);
                }
                fileSizeAttr = EntityUtil.getFirst(media.getRelated("DataResourceAttribute", ["attrName": FileTypeUtil.FILE_SIZE_ATTRIBUTE_NAME], null, false));
            } else {
                errMsg = "Schema error while reading media: several results found for contentId '" + parameters.contentId + "'";// TODO: localize
                context.cmsErrorHandler.addContextReadError(context, errMsg); 
            }
        }
    } catch (Exception e) {
        Debug.logError(e, module);
        context.cmsErrorHandler.addContextReadError(context, "Exception while getting media: " + e.getMessage()); // TODO: localize
    }
}

imageSizePresets = EntityQuery.use(delegator).from("ImageSizePreset").cache(true).queryList();

context.imageSizePresets = imageSizePresets;
context.media = media;
context.fileSizeAttr = fileSizeAttr;
context.variantList = variantList;
context.responsiveImage = responsiveImage;
context.responsiveImageViewPortList = responsiveImageViewPortList;
context.hasVariantContent = variantList ? true : false;
if (CmsUtil.verboseOn()) {
    if (parameters.contentId) {
        Debug.logInfo("Cms: editMedia: [contentId: " + parameters.contentId + ", media: " + media + ", variants: " + variantList + "]", module);
    } else {
        Debug.logInfo("Cms: editMedia: no contentId, showing new media page", module);
    }
}
