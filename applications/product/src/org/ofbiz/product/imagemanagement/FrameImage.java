/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.ofbiz.product.imagemanagement;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.swing.ImageIcon;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.string.FlexibleStringExpander;
import org.ofbiz.common.image.ImageTransform;
import org.ofbiz.common.image.ImageType;
import org.ofbiz.common.image.ImageType.ImagePixelType;
import org.ofbiz.common.image.ImageUtil;
import org.ofbiz.common.image.scaler.ImageScaler;
import org.ofbiz.common.image.scaler.ImageScalers;
import org.ofbiz.content.layout.LayoutWorker;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtilProperties;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceUtil;

public class FrameImage {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    public static final String resourceError = "ProductErrorUiLabels";
    public static final String resource = "ProductUiLabels";

    public static Map<String, Object> addImageFrame(DispatchContext dctx, Map<String, ? extends Object> context)
            throws IOException {
        Map<String, Object> result;
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        String imageServerPath = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog", "image.management.path", delegator), context);
        String imageServerUrl = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog", "image.management.url", delegator), context);
        String nameOfThumb = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog", "image.management.nameofthumbnail", delegator), context);

        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String productId = (String) context.get("productId");
        String imageName = (String) context.get("imageName");
        String imageWidth = (String) context.get("imageWidth");
        String imageHeight = (String) context.get("imageHeight");
        Locale locale = (Locale) context.get("locale");

        if (UtilValidate.isEmpty(context.get("frameContentId")) || UtilValidate.isEmpty(context.get("frameDataResourceId"))) {
            result = ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                    "ProductImageFrameContentIdRequired", locale));
            result.putAll(context);
        }
        if (UtilValidate.isEmpty(context.get("imageWidth")) || UtilValidate.isEmpty(context.get("imageHeight"))) {
            result = ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                    "ProductImageWidthAndHeightRequired", locale));
            result.putAll(context);
        }

        String frameContentId = (String) context.get("frameContentId");
        String frameDataResourceId = (String) context.get("frameDataResourceId");

        String frameImageName = null;
        try {
            GenericValue contentDataResourceView = EntityQuery.use(delegator).from("ContentDataResourceView").where("contentId", frameContentId, "drDataResourceId", frameDataResourceId).queryOne();
            frameImageName = contentDataResourceView.getString("contentName");
        } catch (GenericEntityException gee) {
            Debug.logError(gee, module);
            result = ServiceUtil.returnError(gee.getMessage());
            result.putAll(context);
        }

        if (UtilValidate.isNotEmpty(imageName)) {

            // Image Frame
            BufferedImage bufImg1 = ImageIO.read(new File(imageServerPath + "/" + productId + "/" + imageName));
            if (bufImg1 == null) { // SCIPIO: may be null
                return ServiceUtil.returnError(UtilProperties.getMessage("CommonErrorUiLabels", "ImageTransform.unable_to_read_image", locale));
            }
            BufferedImage bufImg2 = ImageIO.read(new File(imageServerPath + "/frame/"+frameImageName));
            if (bufImg2 == null) { // SCIPIO: may be null
                return ServiceUtil.returnError(UtilProperties.getMessage("CommonErrorUiLabels", "ImageTransform.unable_to_read_image", locale));
            }

            // SCIPIO: obsolete
//            int bufImgType;
//            if (BufferedImage.TYPE_CUSTOM == bufImg1.getType()) {
//                bufImgType = BufferedImage.TYPE_INT_ARGB_PRE;
//            } else {
//                bufImgType = bufImg1.getType();
//            }

            int width = Integer.parseInt(imageWidth);
            int height= Integer.parseInt(imageHeight);

            Map<String, Object> contentCtx = new HashMap<>();
            contentCtx.put("contentTypeId", "DOCUMENT");
            contentCtx.put("userLogin", userLogin);
            Map<String, Object> contentResult = new HashMap<>();
            try {
                contentResult = dispatcher.runSync("createContent", contentCtx);
                if (ServiceUtil.isError(contentResult)) {
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(contentResult));
                }
            } catch (GenericServiceException e) {
                Debug.logError(e, module);
                result =  ServiceUtil.returnError(e.getMessage());
                result.putAll(context);
            }

            Map<String, Object> contentThumb = new HashMap<>();
            contentThumb.put("contentTypeId", "DOCUMENT");
            contentThumb.put("userLogin", userLogin);
            Map<String, Object> contentThumbResult = new HashMap<>();
            try {
                contentThumbResult = dispatcher.runSync("createContent", contentThumb);
                if (ServiceUtil.isError(contentThumbResult)) {
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(contentThumbResult));
                }
            } catch (GenericServiceException e) {
                Debug.logError(e, module);
                result =  ServiceUtil.returnError(e.getMessage());
                result.putAll(context);
            }

            String contentIdThumb = (String) contentThumbResult.get("contentId");
            String contentId = (String) contentResult.get("contentId");
            String filenameToUse = (String) contentResult.get("contentId") + ".jpg";
            String filenameTouseThumb = (String) contentResult.get("contentId") + nameOfThumb + ".jpg";

            // SCIPIO: 2017-07-12: new configurable scaling; scalerName may be an algorithm name (abstracted) or some other name (3rd-party lib name or other).
            //Image newImg1 = bufImg1.getScaledInstance(width, height , Image.SCALE_SMOOTH);
            //Image newImg2 = bufImg2.getScaledInstance(width , height , Image.SCALE_SMOOTH);
            BufferedImage newImg1;
            BufferedImage newImg2;
            try {
                Map<String, Object> scalingOptions = ImageUtil.addImageOpOptionIfNotSet(ImageUtil.makeOptions(), "targettype", ImageType.DEFAULT_IMAGEOP);
                ImageScaler scaler = ImageScalers.getScalerOrDefault(scalingOptions);
                newImg1 = scaler.scaleImage(bufImg1, width, height, scalingOptions);
                newImg2 = scaler.scaleImage(bufImg2, width, height, scalingOptions);
            } catch(IOException e) {
                throw new IllegalArgumentException("Error scaling image: " + e.getMessage(), e);
            }

            BufferedImage bufNewImg = combineBufferedImage(newImg1, newImg2, bufImg1);
            String mimeType = imageName.substring(imageName.lastIndexOf(".") + 1);
            ImageIO.write(bufNewImg, mimeType, new File(imageServerPath + "/" + productId + "/" + filenameToUse));

            double imgHeight = bufNewImg.getHeight();
            double imgWidth = bufNewImg.getWidth();

            Map<String, Object> resultResize = ImageManagementServices.resizeImageThumbnail(bufNewImg, imgHeight, imgWidth);
            ImageIO.write((RenderedImage) resultResize.get("bufferedImage"), mimeType, new File(imageServerPath + "/" + productId + "/" + filenameTouseThumb));

            String imageUrlResource = imageServerUrl + "/" + productId + "/" + filenameToUse;
            String imageUrlThumb = imageServerUrl + "/" + productId + "/" + filenameTouseThumb;

            ImageManagementServices.createContentAndDataResource(dctx, userLogin, filenameToUse, imageUrlResource, contentId, "image/jpeg");
            ImageManagementServices.createContentAndDataResource(dctx, userLogin, filenameTouseThumb, imageUrlThumb, contentIdThumb, "image/jpeg");

            Map<String, Object> createContentAssocMap = new HashMap<>();
            createContentAssocMap.put("contentAssocTypeId", "IMAGE_THUMBNAIL");
            createContentAssocMap.put("contentId", contentId);
            createContentAssocMap.put("contentIdTo", contentIdThumb);
            createContentAssocMap.put("userLogin", userLogin);
            createContentAssocMap.put("mapKey", "100");
            try {
                Map<String, Object> serviceResult = dispatcher.runSync("createContentAssoc", createContentAssocMap);
                if (ServiceUtil.isError(serviceResult)) {
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(serviceResult));
                }
            } catch (GenericServiceException e) {
                Debug.logError(e, module);
                result =  ServiceUtil.returnError(e.getMessage());
                result.putAll(context);
            }

            Map<String, Object> productContentCtx = new HashMap<>();
            productContentCtx.put("productId", productId);
            productContentCtx.put("productContentTypeId", "IMAGE");
            productContentCtx.put("fromDate", UtilDateTime.nowTimestamp());
            productContentCtx.put("userLogin", userLogin);
            productContentCtx.put("contentId", contentId);
            productContentCtx.put("statusId", "IM_PENDING");
            try {
                Map<String, Object> serviceResult = dispatcher.runSync("createProductContent", productContentCtx);
                if (ServiceUtil.isError(serviceResult)) {
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(serviceResult));
                }
            } catch (GenericServiceException e) {
                Debug.logError(e, module);
                result =  ServiceUtil.returnError(e.getMessage());
                result.putAll(context);
            }

            Map<String, Object> contentApprovalCtx = new HashMap<>();
            contentApprovalCtx.put("contentId", contentId);
            contentApprovalCtx.put("userLogin", userLogin);
            try {
                Map<String, Object> serviceResult = dispatcher.runSync("createImageContentApproval", contentApprovalCtx);
                if (ServiceUtil.isError(serviceResult)) {
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(serviceResult));
                }
            } catch (GenericServiceException e) {
                Debug.logError(e, module);
                result =  ServiceUtil.returnError(e.getMessage());
                result.putAll(context);
            }
        }
         else{
             String errMsg = UtilProperties.getMessage(resourceError, "ProductPleaseSelectImage", locale);
             Debug.logFatal(errMsg, module);
             result =  ServiceUtil.returnError(errMsg);
             result.putAll(context);
        }
        String successMsg = UtilProperties.getMessage(resource, "ProductFrameImageSuccessfully", locale);
        result = ServiceUtil.returnSuccess(successMsg);
        return result;
    }

//    /**
//     * combineBufferedImage
//     * @deprecated SCIPIO: 2017-07-11: This failed to preserve image type properly for indexed images.
//     * FIXME?: this will simply crash now. get rid of anything that called this.
//     */
//    @Deprecated
//    public static BufferedImage combineBufferedImage(Image image1, Image image2, int bufImgType) {
//        return combineBufferedImage(image1, image2, (BufferedImage) null);
//    }

    /**
     * combineBufferedImage.
     * SCIPIO: 2017-07-10: modified to take a typeReferenceImage instance of bufImgType, so we have the full
     * information to replicate the original image type, needed for indexed images.
     */
    public static BufferedImage combineBufferedImage(Image image1, Image image2, BufferedImage typeReferenceImage) {
        // Full image loading
        image1 = new ImageIcon(image1).getImage();
        image2 = new ImageIcon(image2).getImage();

        // New BufferedImage creation
        // SCIPIO: indexed images fix
        BufferedImage bufferedImage;
        Graphics2D g;
        if (image1 instanceof BufferedImage && ImagePixelType.isTypeImageOpFriendly(((BufferedImage) image1).getType())) {
            // still create a copy to avoid modifying the original
            bufferedImage = ImageTransform.cloneBufferedImage((BufferedImage) image1);
            g = bufferedImage.createGraphics();
        } else {
            bufferedImage = ImageTransform.createBufferedImage(ImageType.DEFAULT_IMAGEOP.getImageTypeInfoFor(typeReferenceImage), image1.getWidth(null),
                    image1.getHeight(null));
            g = bufferedImage.createGraphics();
            g.drawImage(image1, null, null);
        }

        // Draw Image combine
        Point2D center = new Point2D.Float(bufferedImage.getHeight() / 2f, bufferedImage.getWidth() / 2f);
        AffineTransform at = AffineTransform.getTranslateInstance(center.getX( ) - (image2.getWidth(null) / 2f), center.getY( ) - (image2.getHeight(null) / 2f));
        g.transform(at);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(image2, 0, 0, null);
        Composite c = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .35f);
        g.setComposite(c);
        at = AffineTransform.getTranslateInstance(center.getX( ) - (bufferedImage.getWidth(null) / 2f), center.getY( ) - (bufferedImage.getHeight(null) / 2f));
        g.setTransform(at);
        g.drawImage(bufferedImage, 0, 0, null);
        g.dispose();

        // SCIPIO: new: we convert to the target type only at the very end, in separate step, so the previous operations don't suffer from color loss
        if (ImageType.imageMatchesRequestedType(bufferedImage, typeReferenceImage)) {
            return bufferedImage;
        } else {
            BufferedImage resultImage = ImageTransform.createCompatibleBufferedImage(typeReferenceImage, bufferedImage.getWidth(null), bufferedImage.getHeight(null));
            ImageTransform.copyToBufferedImage(bufferedImage, resultImage);
            return( resultImage );
        }
    }

    public static String uploadFrame(HttpServletRequest request, HttpServletResponse response) {
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        Delegator delegator = dispatcher.getDelegator();
        HttpSession session = request.getSession();
        GenericValue userLogin = (GenericValue)session.getAttribute("userLogin");

        Map<String, ? extends Object> context = UtilGenerics.checkMap(request.getParameterMap());
        String imageServerPath = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog", "image.management.path", delegator), context);
        String imageServerUrl = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog", "image.management.url", delegator), context);
        Map<String, Object> tempFile = LayoutWorker.uploadImageAndParameters(request, "uploadedFile");
        String imageName = tempFile.get("imageFileName").toString();
        String mimType = tempFile.get("uploadMimeType").toString();
        ByteBuffer imageData = (ByteBuffer) tempFile.get("imageData");
        if (UtilValidate.isEmpty(imageName) || UtilValidate.isEmpty(imageData)) {
            session.setAttribute("frameContentId", request.getParameter("frameExistContentId"));
            session.setAttribute("frameDataResourceId", request.getParameter("frameExistDataResourceId"));
            request.setAttribute("_ERROR_MESSAGE_", "There is no frame image, please select the image type *.PNG  uploading.");
            return "error";
        }
        if (!"image/png".equals(mimType)) {
            session.setAttribute("frameContentId", request.getParameter("frameExistContentId"));
            session.setAttribute("frameDataResourceId", request.getParameter("frameExistDataResourceId"));
            request.setAttribute("_ERROR_MESSAGE_", "The selected image type is incorrect, please select the image type *.PNG to upload.");
            return "error";
        }

        String contentId = null;
        String dataResourceId = null;
        try {
            String dirPath = "/frame/";
            File dir = new File(imageServerPath + dirPath);
            if (!dir.exists()) {
                boolean createDir = dir.mkdir();
                if (!createDir) {
                    request.setAttribute("_ERROR_MESSAGE_", "Cannot create directory.");
                    return "error";
                }
            }
            String imagePath = "/frame/" + imageName;
            File file = new File(imageServerPath + imagePath);
            if (file.exists()) {
                request.setAttribute("_ERROR_MESSAGE_", "There is an existing frame, please select from the existing frame.");
                return "error";
            }
            RandomAccessFile out = new RandomAccessFile(file, "rw");
            out.write(imageData.array());
            out.close();

            //create dataResource
            Map<String, Object> dataResourceCtx = new HashMap<>();
            dataResourceCtx.put("objectInfo", imageServerUrl + imagePath);
            dataResourceCtx.put("dataResourceName", imageName);
            dataResourceCtx.put("userLogin", userLogin);
            dataResourceCtx.put("dataResourceTypeId", "IMAGE_OBJECT");
            dataResourceCtx.put("mimeTypeId", "image/png");
            dataResourceCtx.put("isPublic", "Y");
            Map<String, Object> dataResourceResult = dispatcher.runSync("createDataResource", dataResourceCtx);
            if (ServiceUtil.isError(dataResourceResult)) {
                String errorMessage = ServiceUtil.getErrorMessage(dataResourceResult);
                request.setAttribute("_ERROR_MESSAGE_", errorMessage);
                Debug.logError(errorMessage, module);
                return "error";
            }
            dataResourceId = dataResourceResult.get("dataResourceId").toString();
            //create content
            Map<String, Object> contentCtx = new HashMap<>();
            contentCtx.put("dataResourceId", dataResourceResult.get("dataResourceId").toString());
            contentCtx.put("contentTypeId", "IMAGE_FRAME");
            contentCtx.put("contentName", imageName);
            contentCtx.put("fromDate", UtilDateTime.nowTimestamp());
            contentCtx.put("userLogin", userLogin);
            Map<String, Object> contentResult = dispatcher.runSync("createContent", contentCtx);
            if (ServiceUtil.isError(contentResult)) {
                String errorMessage = ServiceUtil.getErrorMessage(contentResult);
                request.setAttribute("_ERROR_MESSAGE_", errorMessage);
                Debug.logError(errorMessage, module);
                return "error";
            }
            contentId = contentResult.get("contentId").toString();
        } catch (GenericServiceException | IOException gse) {
            request.setAttribute("_ERROR_MESSAGE_", gse.getMessage());
            return "error";
        }
        session.setAttribute("frameContentId", contentId);
        session.setAttribute("frameDataResourceId", dataResourceId);
        request.setAttribute("_EVENT_MESSAGE_", "Upload frame image successful.");
        return "success";
    }

    public static String previewFrameImage(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        Map<String, ? extends Object> context = UtilGenerics.checkMap(request.getParameterMap());
        HttpSession session = request.getSession();
        String imageServerPath = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog", "image.management.path", delegator), context);

        String productId = request.getParameter("productId");
        String imageName = request.getParameter("imageName");

        String dirPath = "/preview/";
        File dir = new File(imageServerPath + dirPath);
        if (!dir.exists()) {
            boolean createDir = dir.mkdir();
            if (!createDir) {
                request.setAttribute("_ERROR_MESSAGE_", "Cannot create directory.");
                return "error";
            }
        }

        if (UtilValidate.isEmpty(request.getParameter("frameContentId")) || UtilValidate.isEmpty(request.getParameter("frameDataResourceId"))) {
            request.setAttribute("_ERROR_MESSAGE_", "Required frame image content ID or dataResource ID parameters. Please upload new frame image or choose the exist frame.");
            return "error";
        }
        String frameContentId = request.getParameter("frameContentId");
        String frameDataResourceId = request.getParameter("frameDataResourceId");

        if (UtilValidate.isEmpty(request.getParameter("imageWidth")) || UtilValidate.isEmpty(request.getParameter("imageHeight"))) {
            String errMsg = "Image Width and Image Height are required to preview the image. Please enter in Image Width and Image Height fields.";
            session.setAttribute("frameContentId", frameContentId);
            session.setAttribute("frameDataResourceId", frameDataResourceId);
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }

        String frameImageName = null;
        try {
            GenericValue contentDataResourceView = EntityQuery.use(delegator).from("ContentDataResourceView").where("contentId", frameContentId, "drDataResourceId", frameDataResourceId).queryOne();
            frameImageName = contentDataResourceView.getString("contentName");
        } catch (GenericEntityException e) {
            request.setAttribute("_ERROR_MESSAGE_", e.getMessage());
            return "error";
        }
        if (UtilValidate.isNotEmpty(imageName)) {
            File file = new File(imageServerPath + "/preview/" +"/previewImage.jpg");
            if(!file.delete()) {
                Debug.logError("File :" + file.getName() + ", couldn't be deleted", module);
            }
            // Image Frame
            BufferedImage bufImg1 = ImageIO.read(new File(imageServerPath + "/" + ImageUtil.cleanPathname(productId) + "/" + ImageUtil.cleanFilename(imageName)).getCanonicalFile()); // SCIPIO: clean parameters
            if (bufImg1 == null) { // SCIPIO: may be null
                request.setAttribute("_EVENT_MESSAGE_", UtilProperties.getMessage("CommonErrorUiLabels", "ImageTransform.unable_to_read_image", UtilHttp.getLocale(request)));
                return "error";
            }
            BufferedImage bufImg2 = ImageIO.read(new File(imageServerPath + "/frame/" + frameImageName));
            if (bufImg2 == null) { // SCIPIO: may be null
                request.setAttribute("_EVENT_MESSAGE_", UtilProperties.getMessage("CommonErrorUiLabels", "ImageTransform.unable_to_read_image", UtilHttp.getLocale(request)));
                return "error";
            }

            // SCIPIO: obsolete
//            int bufImgType;
//            if (BufferedImage.TYPE_CUSTOM == bufImg1.getType()) {
//                bufImgType = BufferedImage.TYPE_INT_ARGB_PRE;
//            } else {
//                bufImgType = bufImg1.getType();
//            }

            int width = Integer.parseInt(request.getParameter("imageWidth"));
            int height= Integer.parseInt(request.getParameter("imageHeight"));

            // SCIPIO: 2017-07-12: new configurable scaling; scalerName may be an algorithm name (abstracted) or some other name (3rd-party lib name or other).
            //Image newImg1 = bufImg1.getScaledInstance(width, height , Image.SCALE_SMOOTH);
            //Image newImg2 = bufImg2.getScaledInstance(width , height , Image.SCALE_SMOOTH);
            BufferedImage newImg1;
            BufferedImage newImg2;
            try {
                Map<String, Object> scalingOptions = ImageUtil.addImageOpOptionIfNotSet(ImageUtil.makeOptions(), "targettype", ImageType.DEFAULT_IMAGEOP);
                ImageScaler scaler = ImageScalers.getScalerOrDefault(scalingOptions);
                newImg1 = scaler.scaleImage(bufImg1, width, height, scalingOptions);
                newImg2 = scaler.scaleImage(bufImg2, width, height, scalingOptions);
            } catch(IOException e) {
                throw new IllegalArgumentException("Error scaling image: " + e.getMessage(), e);
            }

            BufferedImage bufNewImg = combineBufferedImage(newImg1, newImg2, bufImg1); // SCIPIO
            String mimeType = imageName.substring(imageName.lastIndexOf(".") + 1);
            ImageIO.write(bufNewImg, mimeType, new File(imageServerPath + "/preview/" + "/previewImage.jpg"));

        } else {
            String errMsg = "Please select Image.";
            request.setAttribute("_EVENT_MESSAGE_", errMsg);
            return "error";
        }
        return "success";
    }

    public static String chooseFrameImage(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession();
        if(UtilValidate.isEmpty(request.getParameter("frameContentId"))) {
            if (UtilValidate.isNotEmpty(request.getParameter("frameExistContentId")) && UtilValidate.isNotEmpty(request.getParameter("frameExistDataResourceId"))) {
                session.setAttribute("frameExistContentId", request.getParameter("frameExistContentId"));
                session.setAttribute("frameDataResourceId", request.getParameter("frameExistDataResourceId"));
            }
            request.setAttribute("_ERROR_MESSAGE_", "Required frame image content ID");
            return "error";
        }

        String frameContentId = request.getParameter("frameContentId");
        Delegator delegator = (Delegator) request.getAttribute("delegator");

        String frameDataResourceId = null;
        try {
            GenericValue contentDataResource = EntityQuery.use(delegator).from("ContentDataResourceView").where("contentId", frameContentId).queryFirst();
            frameDataResourceId = contentDataResource.getString("dataResourceId");
        } catch (GenericEntityException e) {
            request.setAttribute("_ERROR_MESSAGE_", e.getMessage());
            return "error";
        }
        session.setAttribute("frameContentId", frameContentId);
        session.setAttribute("frameDataResourceId", frameDataResourceId);
        return "success";
    }

    public static String deleteFrameImage(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, ? extends Object> context = UtilGenerics.checkMap(request.getParameterMap());
        String imageServerPath = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog", "image.management.path", (Delegator) context.get("delegator")), context);
        File file = new File(imageServerPath + "/preview/" + "/previewImage.jpg").getCanonicalFile();
        if (file.exists()) {
            if (!file.delete()) {
                Debug.logError("File :" + file.getName() + ", couldn't be deleted", module);
            }
        }
        return "success";
    }
}
