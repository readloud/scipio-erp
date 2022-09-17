
package com.ilscipio.scipio.webtools;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.webapp.WebAppUtil;


/**
 * Servlet used to serve entity export files, which basically consists in getting the
 * corresponding raw data from database and stream it in the response.
 */
@SuppressWarnings("serial")
public class ExportServlet extends HttpServlet {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    /**
     * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest,
     *      javax.servlet.http.HttpServletResponse)
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // SPECIAL: getDelegator/getDispatcher methods required so tenant db doesn't break (or breaks less)
        Delegator delegator = WebAppUtil.getDelegatorFilterSafe(request);
        //LocalDispatcher dispatcher = getDispatcher(request);
        //Locale locale = UtilHttp.getLocale(request);
        //GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");

        String exportId = request.getParameter("exportId");

        GenericValue dataResource;
        try {
            if (UtilValidate.isNotEmpty(exportId)) {
                // this implies we're getting IMAGE_OBJECT type
                dataResource = EntityUtil.getFirst(EntityQuery.use(delegator).from("EntityExport").where("exportId", exportId).queryList());

                // see org.ofbiz.content.data.DataEvents#serveImage for reference code
                //ServletContext application = request.getServletContext(); // SCIPIO: NOTE: no longer need getSession() for getServletContext(), since servlet API 3.0

                byte[] mediaData = dataResource.getBytes("fileData");
                if (mediaData == null) {
                    if (dataResource.getModelEntity().isField("file")) {
                        mediaData = dataResource.getBytes("file");
                        if (mediaData != null) {
                            Debug.logWarning("DEPRECATED: EntityExport exportId '" + exportId
                                    + "' is using the old EntityExport.file field; this field is deprecated as of 2018-09-04"
                                    + " and will be removed. Please save your old data if needed, delete this record, and re-export as needed."
                                    + " Sorry for the inconvenience.", module);
                        }
                    }

                    if (mediaData == null) {
                        mediaData = new byte[0];
                        Debug.logWarning("EntityExport exportId '" + exportId
                                + "' contains no file data; this could be either due"
                                + " to an unexpected error or database modification OR because the EntityExport.file field has been removed"
                                + " ; in the latter case, if you still need this data, simply temporarily uncomment the EntityExport \"file\" field"
                                + " in framework/webtools/entitydef/entitymodel_scipio.xml to retrieve your data", module);
                    }
                }

                // dead code
                //ByteArrayInputStream mediaStream = new ByteArrayInputStream(mediaData);

                // extra warning, will help users figure out what happened because we were forced to rename a field...
                long fileSize = (long) dataResource.get("fileSize");
                if (fileSize != mediaData.length) {
                    Debug.logWarning("EntityExport exportId '" + exportId
                            + "' has a fileSize field different from the actual file data size; this could be either due"
                            + " to an unexpected error or database modification", module);
                }

                // TODO: REVIEW: why use anything other than mediaData.length here? can only cause problems
                //long mediaLength = fileSize;
                long mediaLength = mediaData.length;

                response.setContentType("application/zip");
                response.setHeader("Content-Disposition", "inline; filename= " + exportId+".zip");
                response.setContentLengthLong(mediaLength);
                if (mediaData != null) {
                    response.getOutputStream().write(mediaData, 0, (int) mediaLength);
                }
                // dead code, mediaData cannot be null
                //else if (mediaStream != null) {
                //    UtilHttp.streamContent(response.getOutputStream(), mediaStream, (int) mediaLength);
                //} else {
                //    Debug.logError("Webtools: Bad stream/bytes source [effective exportId: " + exportId + "]", module);
                //    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal error"); // WARN: DO NOT send details, for security reasons
                //    return;
                //}
            }
        } catch (Exception e) {
            Debug.logError(e, module);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal error"); // WARN: DO NOT send details, for security reasons
            return;
        }
    }
}
