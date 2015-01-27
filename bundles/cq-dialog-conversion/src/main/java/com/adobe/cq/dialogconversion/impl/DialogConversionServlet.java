/*************************************************************************
 *
 * ADOBE CONFIDENTIAL
 * __________________
 *
 *  Copyright 2014 Adobe Systems Incorporated
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Adobe Systems Incorporated and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Adobe Systems Incorporated and its
 * suppliers and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Adobe Systems Incorporated.
 **************************************************************************/

package com.adobe.cq.dialogconversion.impl;

import com.adobe.cq.dialogconversion.DialogRewriteException;
import com.adobe.cq.dialogconversion.DialogRewriteRule;
import com.adobe.cq.dialogconversion.impl.rules.NodeBasedRewriteRule;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

@SlingServlet(
        methods = "POST",
        paths = "/libs/cq/dialogconversion/content/convert",
        extensions = "json"
)
public class DialogConversionServlet extends SlingAllMethodsServlet {

    /**
     * Relative path to the node containing node-based dialog rewrite rules
     */
    public static final String RULES_SEARCH_PATH = "cq/dialogconversion/rules";

    public static final String PARAM_PATHS = "paths";
    private static final String KEY_RESULT_PATH = "resultPath";
    private static final String KEY_ERROR_MESSAGE = "errorMessage";

    private Logger logger = LoggerFactory.getLogger(DialogConversionServlet.class);

    /**
     * Keeps track of OSGi services implementing dialog rewrite rules
     */
    @Reference(
            referenceInterface = DialogRewriteRule.class,
            cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            bind = "bindRule",
            unbind = "unbindRule"
    )
    private List<DialogRewriteRule> rules = Collections.synchronizedList(new LinkedList<DialogRewriteRule>());

    @SuppressWarnings("unused")
    public void bindRule(DialogRewriteRule rule) {
        rules.add(rule);
    }

    @SuppressWarnings("unused")
    public void unbindRule(DialogRewriteRule rule) {
        rules.remove(rule);
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        // validate 'paths' parameter
        RequestParameter[] paths = request.getRequestParameters(PARAM_PATHS);
        if (paths == null) {
            logger.warn("Missing parameter '" + PARAM_PATHS + "'");
            response.setContentType("text/html");
            response.getWriter().println("Missing parameter '" + PARAM_PATHS + "'");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // get dialog rewrite rules
        List<DialogRewriteRule> rules = getRules(request.getResourceResolver());

        long tick = System.currentTimeMillis();
        Session session = request.getResourceResolver().adaptTo(Session.class);
        DialogTreeRewriter rewriter = new DialogTreeRewriter(rules);
        JSONObject results = new JSONObject();
        logger.debug("Converting {} dialogs", paths.length);

        try {
            // iterate over all paths
            for (RequestParameter parameter : paths) {
                String path = parameter.getString();
                JSONObject json = new JSONObject();
                results.put(path, json);

                // verify that the path exists
                if (!session.nodeExists(path)) {
                    json.put(KEY_ERROR_MESSAGE, "Invalid path");
                    logger.debug("Path {} doesn't exist", path);
                    continue;
                }

                try {
                    // rewrite the dialog
                    Node result = rewriter.rewrite(session.getNode(path));
                    json.put(KEY_RESULT_PATH, result.getPath());
                    logger.debug("Successfully converted dialog {} to {}", path, result.getPath());
                } catch (DialogRewriteException e) {
                    json.put(KEY_ERROR_MESSAGE, e.getMessage());
                    logger.warn("Converting dialog {} failed", path, e);
                }
            }
            response.setContentType("application/json");
            response.getWriter().write(results.toString());

            long tack = System.currentTimeMillis();
            logger.debug("Rewrote {} dialogs in {} ms", paths.length, tack - tick);
        } catch (Exception e) {
            throw new ServletException("Caught exception while rewriting dialogs", e);
        }
    }

    private List<DialogRewriteRule> getRules(ResourceResolver resolver)
            throws ServletException {
        final List<DialogRewriteRule> rules = new LinkedList<DialogRewriteRule>();

        // 1) rules provided as OSGi services
        // (we need to synchronize, since the 'addAll' will iterate over 'rules')
        synchronized (this.rules) {
            rules.addAll(this.rules);
        }
        int nb = rules.size();

        // 2) node-based rules
        Resource resource = resolver.getResource(RULES_SEARCH_PATH);
        if (resource != null) {
            try {
                Node rulesContainer = resource.adaptTo(Node.class);
                NodeIterator iterator = rulesContainer.getNodes();
                while (iterator.hasNext()) {
                    rules.add(new NodeBasedRewriteRule(iterator.nextNode()));
                }
            } catch (RepositoryException e) {
                throw new ServletException("Caught exception while collecting rewrite rules", e);
            }
        }

        // sort rules according to their ranking
        Collections.sort(rules, new RuleComparator());

        logger.debug("Found {} rules ({} Java-based, {} node-based)", nb, rules.size() - nb);
        return rules;
    }

    private class RuleComparator implements Comparator<DialogRewriteRule> {

        public int compare(DialogRewriteRule rule1, DialogRewriteRule rule2) {
            int ranking1 = rule1.getRanking();
            ranking1 = ranking1 < 0 ? Integer.MAX_VALUE : ranking1;
            int ranking2 = rule2.getRanking();
            ranking2 = ranking2 < 0 ? Integer.MAX_VALUE : ranking2;
            return Double.compare(ranking1, ranking2);
        }

    }

}